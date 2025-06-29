package Master;

import Common.DownloadLogEntry;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Set;

/*
 * TERMINALE 1:
 * javac -d out (Get-ChildItem -Recurse -Filter *.java -Path src | ForEach-Object { $_.FullName })
 * cd out
 * java Master.Master 9000
 * TERMINALE 2:
 * javac -d out (Get-ChildItem -Recurse -Filter *.java -Path src | ForEach-Object { $_.FullName })
 * java -cp out Peer.Client 127.0.0.1 9000
 */

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
     * Gestisce il comando listdata della console del Master. 
     * Recupera la lista di tutte le risorse conosciute e da quali peer sono disponibili.
     */
    private void handleListData() {
        //recupera l'oggetto MasterState che contiene lo stato globale di risorse e peer
        MasterState state = server.getState();
        // Richiede la mappa completa delle risorse con i relativi peer che le possiedono.
        // chiave: nome risorsa; valore: ID dei peer che la possiedono
        Map<String, Set<String>> all = state.listAllResources();
        System.out.println("Risorse disponibili in rete:");
        for (Map.Entry<String, Set<String>> entry : all.entrySet()) {
            System.out.printf("- %s : %s%n", entry.getKey(), entry.getValue());
        }
    }

    /**
     * Gestisce il comando inspectNodes, aprendo una sottoconsole interattiva 
     * per esplorare lo stato di singoli peer o delle risorse.
     */
    private void handleInspectNodes(BufferedReader console) throws IOException {
        // Recupera lo stato corrente del server.
        MasterState state = server.getState();
        System.out.println("Modalità inspectNodes: digita 'peer <peerId>' o 'resource <nomeRisorsa>' o 'exit' per uscire.");
        while (true) {
            System.out.print("> ");
            String cmd = console.readLine();
            // Se il comando è exit o input nullo, esce dalla modalità inspect.
            if (cmd == null || cmd.trim().equals("exit")) {
                System.out.println("Uscita da inspectNodes.");
                return;
            }
            String[] tokens = cmd.trim().split("\\s+");
            if (tokens.length != 2) {
                System.out.println("Syntax: peer <peerId> | resource <nomeRisorsa> | exit");
                continue;
            }
            // Valuta se il primo token è peer o resource.
            switch (tokens[0]) {
                case "peer" -> {
                    String pID = tokens[1];
                    // restituisce informazioni su un peer specifico oppure null se non esiste.
                    var info = state.inspectPeer(pID);
                    // Cerca e stampa informazioni su un peer specifico. Se non trovato, stampa errore.
                    if (info == null) {
                        System.out.println("Peer non trovato: " + pID);
                    } else {
                        System.out.printf("Peer %s @%s:%d - risorse: %s - lastSeen: %s%n",info.getId(),info.getAddress(),info.getPort(), info.getResources(),info.getLastSeen());
                    }
                }
                // Verifica quali peer possiedono una risorsa specifica. Se nessuno, lo segnala. Altrimenti mostra l’elenco.
                case "resource" -> {
                    String risorsa = tokens[1];
                    // restituisce un oggetto di tipo Set<String> contenente gli ID di tutti i peer che hanno registrato quella risorsa.
                    Set<String> peers = state.inspectPeersByResource(risorsa);
                    if (peers.isEmpty()) {
                        System.out.println("Nessun peer possiede la risorsa: " + risorsa);
                    } else {
                        System.out.printf("Risorsa %s posseduta da: %s%n", risorsa, peers);
                    }
                }
                default -> System.out.println("Syntax: peer <peerId> | resource <nomeRisorsa> | exit");
            }
        }
    }

    /**
     * Gestisce il comando 'log' Mostra i tentativi di download registrati nel log del Master
     */
    private void handleLog() {
        //Ottiene dal server lo stato attuale e la lista di DownloadLogEntry.
        MasterState state = server.getState();
        // var permette di inserire automaticamente il tipo della variabile in base a cosa gli viene assegnato.
        // equivale a List<DownloadLogEntry> entries = state.getLogEntries();
        var entries = state.getLogEntries();
        System.out.println("Elenco tentativi di download:");
        for (DownloadLogEntry e : entries) {
            System.out.printf("- %s risorsa: %s da: %s a: %s success: %s%n",e.getTimestamp(),e.getResource(),e.getFromPeer(),e.getToPeer(),e.isSuccess());
        }
    }

    /**
     * Gestisce il comando 'quit' per terminare il server Master e uscire.
     */
    private void handleQuit() {
        System.out.println("Master disconnesso e server terminato.");
        // Arresta il server
        server.shutdown();
    }
}
