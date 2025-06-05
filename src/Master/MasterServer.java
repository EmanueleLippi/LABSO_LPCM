package Master;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gestisce il ServerSocket e il thread-pool per le connessioni dei peer.
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
     * Avvia il server: apre il ServerSocket e inizia ad accettare connessioni.
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Master in ascolto sulla porta " + port);

            // Avvio thread CLI (non blocca)
            Thread cliThread = new Thread(new CliConsole(this));
            cliThread.setDaemon(true);
            cliThread.start();

            // Ciclo di accept per peer
            while (running) {
                Socket clientSocket = serverSocket.accept();
                // Ogni connessione è gestita da un PeerHandler nel pool
                pool.execute(new PeerHandler(clientSocket, state));
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Errore server: " + e.getMessage());
            }
        } finally {
            shutdown();
        }
    }

    /**
     * Arresta il server in modo ordinato: chiude socket, pool e risorse.
     */
    public void shutdown() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) { }

        // Chiude il pool e aspetta terminazione
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException ex) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("Master arrestato.");
    }

    /** ritorna lo Stato interno (per accesso da CLI) */
    public MasterState getState() {
        return state;
    }
}
