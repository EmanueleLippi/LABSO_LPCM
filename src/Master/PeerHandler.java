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

    /**
     * Costruttore: inizializza socket e stato condiviso
    */
    PeerHandler(Socket socket, MasterState state) {
        this.socket = socket;
        this.state = state;
    }

    /**
     * Metodo principale eseguito nel thread: legge e interpreta i comandi del peer.
    */
    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            String line;
            while ((line = in.readLine()) != null) {
                // Divisione della riga in token, usando lo spazio come delimitatore
                String[] tokens = line.trim().split("\\s+");
                if (tokens.length == 0) continue;

                String cmd = tokens[0];
                // Gestione comandi secondo Protocol
                switch (cmd) {
                    case Protocol.REGISTER                   -> handleRegister(tokens);
                    case Protocol.UPDATE                     -> handleUpdate(tokens);
                    case Protocol.LIST_DATA_REMOTE           -> handleListData();
                    case Protocol.GET_PEERS_FOR_RESOURCE     -> handleGetPeers(tokens);
                    case Protocol.DOWNLOAD_FAIL              -> handleDownloadFail(tokens);
                    case Protocol.DISCONNECTED               -> {
                        handleDisconnect(tokens);
                        return; // chiude il thread
                    }
                    default                                 -> sendResponse(Protocol.ERROR + " UNKNOWN_COMMAND");
                }
            }
        } catch (IOException e) {
            // Peer disconnesso inaspettatamente
        } finally {
            // Chiude la connessione e libera risorse
            cleanup();
        }
    }

    /**
     * Gestisce il comando REGISTER.
     * Sintassi: REGISTER <peerId> <peerPort> <numRisorse> <ris1> <ris2> ... <risN>
    */
    private void handleRegister(String[] tokens) throws IOException {
        if (tokens.length < 4) {
            sendResponse(Protocol.ERROR + " Missing arguments for REGISTER");
            return;
        }
        String peerId = tokens[1];
        int peerPort;
        // Parsing risorse
        try {
            peerPort = Integer.parseInt(tokens[2]);
        } catch (NumberFormatException ex) {
            sendResponse(Protocol.ERROR + " Bad peerPort for REGISTER");
            return;
        }
        int n;
        try {
            n = Integer.parseInt(tokens[3]);
        } catch (NumberFormatException ex) {
            sendResponse(Protocol.ERROR + " Bad numRisorse for REGISTER");
            return;
        }
        // Verifica che il numero di risorse corrisponda a quanto dichiarato, in caso positivo crea il set
        if (tokens.length != 4 + n) {
            sendResponse(Protocol.ERROR + " REGISTER count mismatch");
            return;
        }
        Set<String> resources = new HashSet<>();
        for (int i = 0; i < n; i++) {
            resources.add(tokens[4 + i]);
        }
        // Registra il peer nello stato condiviso, associando l'indirizzo e le risorse 
        InetAddress address = socket.getInetAddress();
        state.registerPeer(peerId, address, peerPort, resources);
        // Conferma la registrazione al peer
        sendResponse(Protocol.REGISTERED + " " + peerId);
    }

    /**
     * Gestisce il comando UPDATE --> Aggiorna le risorse disponibili di un peer già registrato.
     * Sintassi: UPDATE <peerId> <numRisorse> <ris1> ... <risN>
    */
    private void handleUpdate(String[] tokens) throws IOException {
        if (tokens.length < 3) {
            sendResponse(Protocol.ERROR + " Missing arguments for UPDATE");
            return;
        }
        String peerId = tokens[1];
        int n;
        try {
            n = Integer.parseInt(tokens[2]);
        } catch (NumberFormatException ex) {
            sendResponse(Protocol.ERROR + " Bad numRisorse for UPDATE");
            return;
        }
        if (tokens.length != 3 + n) {
            sendResponse(Protocol.ERROR + " UPDATE count mismatch");
            return;
        }
         // Costruisce il nuovo set di risorse
        Set<String> newResources = new HashSet<>();
        for (int i = 0; i < n; i++) {
            newResources.add(tokens[3 + i]);
        }
        // Aggiorna lo stato del peer e conferma l'aggiornamento
        state.updatePeerResources(peerId, newResources);
        sendResponse(Protocol.UPDATED + " " + peerId);
    }

    /**
     * Gestisce il comando LIST_DATA_REMOTE.
     * Restituisce l'elenco completo delle risorse condivise e i peer che le possiedono.
    */
    private void handleListData() throws IOException {
        Map<String, Set<String>> all = state.listAllResources();
        StringBuilder sb = new StringBuilder();
        sb.append(Protocol.LIST_DATA_RESPONSE).append(" ").append(all.size());
        // Per ogni risorsa, aggiunge la lista dei peer che la posseggono
        for (var entry : all.entrySet()) {
            String risorsa = entry.getKey();
            Set<String> peers = entry.getValue();
            sb.append(" ").append(risorsa).append(" ").append(peers.size());
            for (String pid : peers) {
                sb.append(" ").append(pid);
            }
        }
        // Invia risposta al peer
        sendResponse(sb.toString());
    }

    /**
     * Gestisce il comando GET_PEERS_FOR_RESOURCE --> Restituisce i peer che posseggono una risorsa specifica.
     * Sintassi: GET_PEERS_FOR_RESOURCE <risorsa>
    */
    private void handleGetPeers(String[] tokens) throws IOException {
        if (tokens.length != 2) {
            sendResponse(Protocol.ERROR + " Missing argument for GET_PEEERS_FOR_RESOURCE");
            return;
        }
        String risorsa = tokens[1];
        // Ottiene i peer che hanno la risorsa
        Set<String> peers = state.getPeersFor(risorsa);
        // Se non ci sono peer, risponde con RESOURCE_NOT_FOUN
        if (peers.isEmpty()) {
            sendResponse(Protocol.RESOURCE_NOT_FOUND + " " + risorsa);
        // Altrimenti, costruisce la risposta con il numero di peer e i loro ID
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
     * Gestisce il comando DOWNLOAD_FAIL --> Cerca un nuovo peer per scaricare la risorsa.
     * Sintassi: DOWNLOAD_FAIL <risorsa> <peerFallito>
    */
    private void handleDownloadFail(String[] tokens) throws IOException {
        if (tokens.length != 3) {
            sendResponse(Protocol.ERROR + " Missing args for DOWNLOAD_FAIL");
            return;
        }
        String risorsa = tokens[1];
        String failedPeer = tokens[2];
        // Richiede un nuovo peer per la risorsa
        String nextPeer = state.handleDownloadFail(risorsa, failedPeer);
        // Nessun peer alternativo disponibile
        if (nextPeer == null) {
            sendResponse(Protocol.RESOURCE_NOT_FOUND + " " + risorsa);
        // Altrimenti, risponde con il peer suggerito
        } else {
            sendResponse(Protocol.DOWNLOAD_PERMITTED + " " + risorsa + " " + nextPeer);
        }
    }

    /**
     * Gestisce il comando DISCONNECTED --> Rimuove il peer dal sistema.
     * Sintassi: DISCONNECTED <peerId> 
    */
    private void handleDisconnect(String[] tokens) throws IOException {
        if (tokens.length != 2) {
            sendResponse(Protocol.ERROR + " Missing argument for DISCONNECTED");
            return;
        }
        String peerId = tokens[1];
        // Rimuove il peer dallo stato
        state.removePeer(peerId);
        // Conferma disconnessione
        sendResponse(Protocol.DISCONNECTED_OK + " " + peerId);
    }

    /**
     * Invia una risposta al peer nel formato del protocollo (terminata con \r\n)
    */
    private void sendResponse(String msg) throws IOException {
        out.write(msg);
        out.write("\r\n");
        out.flush();
    }

    /**
     * Chiude il socket
    */
    private void cleanup() {
        try {
            socket.close();
        } catch (IOException ignored) { }
    }
}
