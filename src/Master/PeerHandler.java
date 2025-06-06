package Master;

import Common.Protocol;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Gestisce la connessione con un singolo peer.
 * Interpreta i comandi in arrivo e risponde secondo il protocollo definito in Common.Protocol.
 */
class PeerHandler implements Runnable {

    private final Socket socket;
    private final MasterState state;

    private BufferedReader in;
    private BufferedWriter out;

    PeerHandler(Socket socket, MasterState state) {
        this.socket = socket;
        this.state = state;
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            String line;
            while ((line = in.readLine()) != null) {
                String[] tokens = line.trim().split("\\s+");
                if (tokens.length == 0) continue;

                String cmd = tokens[0];
                switch (cmd) {
                    case Protocol.REGISTER -> handleRegister(tokens);
                    case Protocol.UPDATE   -> handleUpdate(tokens);
                    case Protocol.LIST_DATA_REMOTE      -> handleListData();
                    case Protocol.GET_PEERS_FOR_RESOURCE -> handleGetPeers(tokens);
                    case Protocol.DOWNLOAD_FAIL         -> handleDownloadFail(tokens);
                    case Protocol.DISCONNECTED          -> {
                        handleDisconnect(tokens);
                        return; // chiude il thread
                    }
                    default -> sendResponse("UNKNOWN_COMMAND");
                }
            }
        } catch (IOException e) {
            // Se il peer si disconnette inaspettatamente
        } finally {
            cleanup();
        }
    }

    /**
     * REGISTER <peerId> <peerPort> <numRisorse> <ris1> <ris2> ... <risN>
     */
    private void handleRegister(String[] tokens) throws IOException {
        if (tokens.length < 4) {
            sendResponse("ERROR Missing arguments for REGISTER");
            return;
        }
        String peerId = tokens[1];
        int peerPort;
        try {
            peerPort = Integer.parseInt(tokens[2]);
        } catch (NumberFormatException ex) {
            sendResponse("ERROR Bad peerPort for REGISTER");
            return;
        }
        int n;
        try {
            n = Integer.parseInt(tokens[3]);
        } catch (NumberFormatException ex) {
            sendResponse("ERROR Bad numRisorse for REGISTER");
            return;
        }
        if (tokens.length != 4 + n) {
            sendResponse("ERROR REGISTER count mismatch");
            return;
        }
        Set<String> resources = new HashSet<>();
        for (int i = 0; i < n; i++) {
            resources.add(tokens[4 + i]);
        }
        InetAddress address = socket.getInetAddress();
        state.registerPeer(peerId, address, peerPort, resources);
        sendResponse("200 OK");
    }

    /**
     * FORMATO: UPDATE <peerId> <numRisorse> <ris1> ... <risN>
     */
    private void handleUpdate(String[] tokens) throws IOException {
        if (tokens.length < 3) {
            sendResponse("ERROR Missing arguments for UPDATE");
            return;
        }
        String peerId = tokens[1];
        int n;
        try {
            n = Integer.parseInt(tokens[2]);
        } catch (NumberFormatException ex) {
            sendResponse("ERROR Bad numRisorse for UPDATE");
            return;
        }
        if (tokens.length != 3 + n) {
            sendResponse("ERROR UPDATE count mismatch");
            return;
        }
        Set<String> newResources = new HashSet<>();
        for (int i = 0; i < n; i++) {
            newResources.add(tokens[3 + i]);
        }
        state.updatePeerResources(peerId, newResources);
        sendResponse("200 OK");
    }

    private void handleListData() throws IOException {
        Map<String, Set<String>> all = state.listAllResources();
        StringBuilder sb = new StringBuilder();
        sb.append(Protocol.LIST_DATA_RESPONSE).append(" ").append(all.size());
        for (var entry : all.entrySet()) {
            String risorsa = entry.getKey();
            Set<String> peers = entry.getValue();
            sb.append(" ").append(risorsa).append(" ").append(peers.size());
            for (String pid : peers) {
                sb.append(" ").append(pid);
            }
        }
        sendResponse(sb.toString());
    }

    private void handleGetPeers(String[] tokens) throws IOException {
        if (tokens.length != 2) {
            sendResponse("ERROR Missing argument for GET_PEEERS_FOR_RESOURCE");
            return;
        }
        String risorsa = tokens[1];
        Set<String> peers = state.getPeersFor(risorsa);
        if (peers.isEmpty()) {
            sendResponse(Protocol.RESOURCE_NOT_FOUND + " " + risorsa);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(Protocol.PEER_FOR_RESOURCE).append(" ").append(peers.size());
            for (String pid : peers) {
                sb.append(" ").append(pid);
            }
            sendResponse(sb.toString());
        }
    }

    /**
     * FORMATO: DOWNLOAD_FAIL <risorsa> <peerFallito>
     */
    private void handleDownloadFail(String[] tokens) throws IOException {
        if (tokens.length != 3) {
            sendResponse("ERROR Missing args for DOWNLOAD_FAIL");
            return;
        }
        String risorsa = tokens[1];
        String failedPeer = tokens[2];

        String nextPeer = state.handleDownloadFail(risorsa, failedPeer);
        if (nextPeer == null) {
            sendResponse(Protocol.RESOURCE_NOT_FOUND + " " + risorsa);
        } else {
            sendResponse(Protocol.DOWNLOAD_PERMITTED + " " + risorsa + " " + nextPeer);
        }
    }

    private void handleDisconnect(String[] tokens) throws IOException {
        if (tokens.length != 2) {
            sendResponse("ERROR Missing argument for DISCONNECTED");
            return;
        }
        String peerId = tokens[1];
        state.removePeer(peerId);
        sendResponse("200 OK");
    }

    private void sendResponse(String msg) throws IOException {
        out.write(msg);
        out.write("\r\n");
        out.flush();
    }

    private void cleanup() {
        try {
            socket.close();
        } catch (IOException ignored) { }
    }
}
