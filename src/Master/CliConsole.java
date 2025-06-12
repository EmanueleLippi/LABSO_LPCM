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
                    case "quit" -> handleQuit();
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
        MasterState state = server.getState();
        Map<String, Set<String>> all = state.listAllResources();
        System.out.println("Risorse disponibili in rete:");
        for (Map.Entry<String, Set<String>> entry : all.entrySet()) {
            System.out.printf("- %s : %s%n", entry.getKey(), entry.getValue());
        }
    }

    /**
     * Gestisce il comando 'inspectNodes' per ispezionare i peer e le risorse.
     */
    private void handleInspectNodes(BufferedReader console) throws IOException {
        MasterState state = server.getState();
        System.out.println(
                "Modalità inspectNodes: digita 'peer <peerId>' o 'resource <nomeRisorsa>' o 'exit' per uscire.");
        while (true) {
            System.out.print("> ");
            String cmd = console.readLine();
            if (cmd == null || cmd.trim().equals("exit")) {
                System.out.println("Uscita da inspectNodes.");
                return;
            }
            String[] tokens = cmd.trim().split("\\s+");
            if (tokens.length != 2) {
                System.out.println("Syntax: peer <peerId> | resource <nomeRisorsa> | exit");
                continue;
            }
            switch (tokens[0]) {
                case "peer" -> {
                    String pid = tokens[1];
                    var info = state.inspectPeer(pid);
                    if (info == null) {
                        System.out.println("Peer non trovato: " + pid);
                    } else {
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
                    Set<String> peers = state.inspectPeersByResource(ris);
                    if (peers.isEmpty()) {
                        System.out.println("Nessun peer possiede la risorsa: " + ris);
                    } else {
                        System.out.printf("Risorsa %s posseduta da: %s%n", ris, peers);
                    }
                }
                default -> System.out.println("Syntax: peer <peerId> | resource <nomeRisorsa> | exit");
            }
        }
    }

    /**
     * Gestisce il comando 'log' per visualizzare i tentativi di download registrati.
     */
    private void handleLog() {
        MasterState state = server.getState();
        var entries = state.getLogEntries();
        System.out.println("Elenco tentativi di download:");
        for (DownloadLogEntry e : entries) {
            System.out.printf(
                    "- %s risorsa: %s da: %s a: %s success: %s%n",
                    e.getTimestamp(),
                    e.getResource(),
                    e.getFromPeer(),
                    e.getToPeer(),
                    e.isSuccess());
        }
    }

    /**
     * Gestisce il comando 'quit' per terminare il server Master e uscire.
     */
    private void handleQuit() {
        // Ferma il server Master e rilascia risorse
        server.shutdown();
        System.out.println("Master disconnesso e server terminato.");
        // Esci dal programma
        System.exit(0);
    }
}
