package Master;

import Common.DownloadLogEntry;
import Common.Protocol;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Ogni istanza gestisce la comunicazione tra un singolo peer e il master, 
 * processa i comandi che il peer invia tramite socket.
*/
class PeerHandler implements Runnable {

    // connessione con un peer
    private final Socket socket;
    // stato globale del master
    private final MasterState state;
    // stream per leggere / scrivere dal e verso il peer
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
        // Prepara gli stream per comunicare con il peer
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            // Ciclo che ascolta i comandi inviati dal peer riga per riga
            String line;
            while ((line = in.readLine()) != null) {
                // Divisione della riga in token, usando lo spazio come delimitatore
                String[] tokens = line.trim().split("\\s+");
                if (tokens.length == 0) continue;

                String cmd = tokens[0];
                // Invoca il metodo corrispondente al comando.
                switch (cmd) {
                    case Protocol.REGISTER                   -> handleRegister(tokens);
                    case Protocol.UPDATE                     -> handleUpdate(tokens);
                    case Protocol.LIST_DATA_REMOTE           -> handleListData();
                    case Protocol.GET_PEERS_FOR_RESOURCE     -> handleGetPeers(tokens);
                    case Protocol.DOWNLOAD_LOG               -> handleDownloadLog(tokens);
                    case Protocol.DOWNLOAD_FAIL              -> handleDownloadFail(tokens);
                    case Protocol.DISCONNECTED               -> {handleDisconnect(tokens);
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
     * Gestisce il comando DOWNLOAD_LOG.
     * Sintassi: DOWNLOAD_LOG <resource> <fromPeer> <toPeer> <success>
     */

    private void handleDownloadLog(String[] tokens) throws IOException {
        if (tokens.length != 5) {
            sendResponse(Protocol.ERROR + " Mancano argomenti per il comando DOWNLOAD_LOG");
            return;
        }
        String resource = tokens[1];
        String fromPeer = tokens[2];
        String toPeer = tokens[3];
        boolean success = Boolean.parseBoolean(tokens[4]);
        // oggetto che rappresenta un tentativo di download 
        DownloadLogEntry entry = new DownloadLogEntry(Instant.now(), resource, fromPeer, toPeer, success);
        // Aggiunge il log appena creato alla coda di log nel MasterState
        state.addDownloadLog(entry);
        // Conferma che il log è stato ricevuto e registrato correttamente dal master
        sendResponse(Protocol.LOG_OK);
    }

    /**
     * Gestisce il comando REGISTER.
     * Sintassi: REGISTER <peerId> <peerPort> <numRisorse> <ris1> <ris2> ... <risN>
    */
    private void handleRegister(String[] tokens) throws IOException {
        // Controlla che i parametri siano completi e coerenti
        if (tokens.length < 4) {
            sendResponse(Protocol.ERROR + " Mancano argomenti per il comando REGISTER");
            return;
        }
        // ID del peer che si sta registrando
        String peerId = tokens[1];
        int peerPort;
        // Parse la porta su cui il peer è in ascolto
        try {
            peerPort = Integer.parseInt(tokens[2]);
        } catch (NumberFormatException ex) {
            sendResponse(Protocol.ERROR + " PeerPort errata per REGISTER");
            return;
        }
        // Parse il numero di risorse n che il peer dichiara di voler registrare.
        int n;
        try {
            n = Integer.parseInt(tokens[3]);
        } catch (NumberFormatException ex) {
            sendResponse(Protocol.ERROR + " numRisorse errato per REGISTER");
            return;
        }
        // Verifica che ci siano esattamente n risorse dopo i primi 4 token.
        if (tokens.length != 4 + n) {
            sendResponse(Protocol.ERROR + " Incoerenza di numero di risorse per REGISTER");
            return;
        }
        // Costruisce il set (no duplicati) di risorse che il peer sta registrando
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
            sendResponse(Protocol.ERROR + " Mancano argomenti per il comando UPDATE");
            return;
        }
        // Controlla che i parametri siano completi e coerenti
        String peerId = tokens[1];
        int n;
        try {
            n = Integer.parseInt(tokens[2]);
        } catch (NumberFormatException ex) {
            sendResponse(Protocol.ERROR + " numRisorse errato per UPDATE");
            return;
        }
        if (tokens.length != 3 + n) {
            sendResponse(Protocol.ERROR + " Incoerenza di numero di risorse per UPDATE");
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
            sendResponse(Protocol.ERROR + " Mancano argomenti per il comando GET_PEERS_FOR_RESOURCE");
            return;
        }
        String resource = tokens[1];
        String reply = state.getPeersFor(resource);
        sendResponse(reply);
    }


    /**
     * Gestisce il comando DOWNLOAD_FAIL --> Cerca un nuovo peer per scaricare la risorsa.
     * Sintassi: DOWNLOAD_FAIL <risorsa> <peerFallito>
    */
    private void handleDownloadFail(String[] tokens) throws IOException {
        if (tokens.length != 3) {
            sendResponse(Protocol.ERROR + " Mancano argomenti per il comando DOWNLOAD_FAIL");
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
            sendResponse(Protocol.ERROR + " Mancano argomenti per il comando DISCONNECTED");
            return;
        }
        String peerId = tokens[1];
        // Rimuove il peer dallo stato
        state.removePeer(peerId);
        // Conferma disconnessione
        sendResponse(Protocol.DISCONNECTED_OK + " " + peerId);
    }

    /**
     * Invia una risposta al peer nel formato del protocollo
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
