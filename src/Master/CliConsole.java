package Master;

import Common.DownloadLogEntry;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Set;

/**
 * Thread che gestisce la console interattiva dell'operatore Master.
 * I comandi disponibili non bloccano il servizio di rete.
 */
class CliConsole implements Runnable {

    private final MasterServer server;

    // Costruttore: riceve il server Master da gestire
    CliConsole(MasterServer server) {
        this.server = server;
    }

    @Override
    public void run() {
        try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            // Ciclo continuo per leggere i comandi da console finché l'input non è nullo
            while ((line = console.readLine()) != null) {
                // Rimuove spazi bianchi e gestisce la scelta dell'azione in base al comando inserito
                switch (line.trim()) {
                    case "listdata" -> handleListData();
                    case "inspectNodes" -> handleInspectNodes(console);
                    case "log" -> handleLog();
                    case "quit" -> {
                        server.shutdown();
                        return;
                    }
                    default -> System.out.println("Comando sconosciuto. Uso: listdata | inspectNodes | log | quit");
                }
            }
        } catch (IOException e) {
            // Gestione errori di input da console
            System.err.println("Errore console: " + e.getMessage());
        }
    }

    /**
     * Gestisce il comando 'listdata' per elencare le risorse disponibili in rete.
     */
    private void handleListData() {
        // Ottiene lo stato corrente del master
        MasterState state = server.getState();
        // Mappa risorsa -> peer che la possiedono
        Map<String, Set<String>> all = state.listAllResources();
        System.out.println("Risorse disponibili in rete:");
        for (Map.Entry<String, Set<String>> entry : all.entrySet()) {
            // Stampa la risorsa e i peer che la possiedono
            System.out.printf("- %s : %s%n", entry.getKey(), entry.getValue());
        }
    }

    /**
     * Gestisce il comando 'inspectNodes' per ispezionare i peer e le risorse.
     * Permette di vedere quali peer possiedono quali risorse e altre informazioni sui peer.
     */
    private void handleInspectNodes(BufferedReader console) throws IOException {
        // Ottiene lo stato corrente del master e avvia la modalità di ispezione dei nodi
        MasterState state = server.getState();
        System.out.println(
                "Modalità inspectNodes: digita 'peer <peerId>' o 'resource <nomeRisorsa>' o 'exit' per uscire.");
        while (true) {
            System.out.print("> ");
            // Legge il comando interattivo
            String cmd = console.readLine();
            if (cmd == null || cmd.trim().equals("exit")) {
                System.out.println("Uscita da inspectNodes.");
                return;
            }
            // Divide il comando in parti
            String[] tokens = cmd.trim().split("\\s+");
            if (tokens.length != 2) {
                System.out.println("Syntax: peer <peerId> | resource <nomeRisorsa> | exit");
                continue;
            }
            // Gestisce i comandi 'peer' e 'resource'
            switch (tokens[0]) {
                case "peer" -> {
                    String pid = tokens[1];
                    // Cerca informazioni sul peer specificato
                    var info = state.inspectPeer(pid);
                    if (info == null) {
                        System.out.println("Peer non trovato: " + pid);
                    } else {
                        // Stampa dettagli del peer trovato
                        System.out.printf(
                                "Peer %s @%s:%d - risorse: %s - lastSeen: %s%n",
                                info.getId(),
                                info.getAddress(),
                                info.getPort(),
                                info.getResources(),
                                info.getLastSeen());
                    }
                }
                case "resource" -> {
                    String ris = tokens[1];
                    // Ottiene i peer che possiedono la risorsa
                    Set<String> peers = state.inspectPeersByResource(ris);
                    if (peers.isEmpty()) {
                        System.out.println("Nessun peer possiede la risorsa: " + ris);
                    } else {
                        // Stampa i peer che possiedono la risorsa
                        System.out.printf("Risorsa %s posseduta da: %s%n", ris, peers);
                    }
                }
                default -> System.out.println("Syntax: peer <peerId> | resource <nomeRisorsa> | exit");
            }
        }
    }

    /**
     * Gestisce il comando 'log' per visualizzare i tentativi di download registrati.
     * Mostra l'elenco dei download effettuati, con timestamp, risorsa, peer di origine e destinazione, e successo.
     */
    private void handleLog() {
        // Ottiene lo stato corrente del master
        MasterState state = server.getState();
        // Recupera le voci di log dei download
        var entries = state.getLogEntries();
        System.out.println("Elenco tentativi di download:");
        for (DownloadLogEntry e : entries) {
            // Stampa ogni entry del log con dettagli
            System.out.printf(
                    "- %s risorsa: %s da: %s a: %s success: %s%n",
                    e.getTimestamp(),
                    e.getResource(),
                    e.getFromPeer(),
                    e.getToPeer(),
                    e.isSuccess());
        }
    }
}
