package Master;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gestisce il ServerSocket e il thread-pool per le connessioni dei peer.
 * Metodi start() e shutdown() dichiarati synchronized 
 * per evitare race conditions nella fase di avvio/arresto del server.
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
        if (running) return; // evita doppio avvio
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Master in ascolto sulla porta " + port);

            // Avvio del thread di console (daemon, non blocca l'accept)
            Thread cliThread = new Thread(new CliConsole(this));
            cliThread.setDaemon(true);
            cliThread.start();

            // Ciclo di accept
            while (running) {
                Socket clientSocket = serverSocket.accept();
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
     * Arresta il server in modo ordinato: chiude socket e thread pool.
     */
    public synchronized void shutdown() {
        if (!running) return;
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) { }

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

    /** @return Stato interno per la CLI */
    public MasterState getState() {
        return state;
    }
}
