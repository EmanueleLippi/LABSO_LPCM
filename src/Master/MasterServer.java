package Master;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gestisce il ServerSocket e il thread-pool per le connessioni dei peer.
 * Metodi start() e shutdown() dichiarati synchronized per evitare race conditions nella fase di avvio/arresto del server.
 */
class MasterServer {

    private final int port;
    private final MasterState state = new MasterState();
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile boolean running = false;
    private ServerSocket serverSocket;

    MasterServer(int port) {
        this.port = port;
    }

    /**
     * Avvia il server in modo sincronizzato: apre il ServerSocket e accetta connessioni.
     */
    public synchronized void start() {
        // Evita di avviare il server più di una volta
        if (running)
            return;
        try {
            // Apre il socket sulla porta specificata
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Master in ascolto sulla porta " + port);

            // Avvio del thread di console (daemon, non blocca l'accept)
            Thread cliThread = new Thread(new CliConsole(this));
            // Non impedisce la chiusura dell'app se il main thread finisce
            cliThread.setDaemon(true);
            cliThread.start();

            // Ciclo di accept: accetta connessioni dai peer e delega a un thread nel pool
            while (running) {
                Socket clientSocket = serverSocket.accept();
                // Gestisce il peer in un nuovo thread
                pool.execute(new PeerHandler(clientSocket, state));
            }
        } catch (IOException e) {
            // Mostra l'errore solo se il server era in esecuzione
            if (running) {
                System.err.println("Errore server: " + e.getMessage());
            }
        } finally {
            shutdown();
        }
    }

    /**
     * Arresta il server (sincronizzato per sicurezza thread).
     * Chiude il socket e ferma il pool di thread.
     */
    public synchronized void shutdown() {
        // Se è già fermo, esce subito
        if (!running)
            return;
        // Aggiorna lo stato per bloccare il ciclo di accept()
        running = false;

        // Chiude il socket principale
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        // Arresta il thread pool in modo ordinato
        pool.shutdown();
        try {
            // Aspetta fino a 5 secondi per la terminazione ordinata dei task
            if (!pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                // Se non termina in tempo, forza la chiusura dei thread
                pool.shutdownNow();
            }
        } catch (InterruptedException ex) {
            // In caso di interruzione, forza la chiusura del pool e segnala il thread come interrotto
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("Master arrestato.");
    }

    /** @return Stato interno per la CLI */
    public MasterState getState() {
        return state;
    }
}
