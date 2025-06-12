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
    // Crea un thread pool dinamico, che crea nuovi thread su richiesta.
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
        // Evita avvii multipli se il server è già attivo.
        if (running)
            return;
        try {
            // Apre il socket sulla porta specificata e imposta lo stato a "running".
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Master in ascolto sulla porta " + port);

            // Avvio del thread di console (daemon, non blocca l'accept)
            Thread cliThread = new Thread(new CliConsole(this));
            // Non impedisce la chiusura dell'app se il main thread finisce
            cliThread.setDaemon(true);
            cliThread.start();

            // Ciclo che accetta connessioni finchè è attivo
            // ogni nuova connessione Socket crea un nuovo PeerHandler
            // eseguito in pool: ogni peer è gestito in modo concorrente 
            while (running) {
                Socket clientSocket = serverSocket.accept();
                PeerHandler handler = new PeerHandler(clientSocket, state);
                pool.execute(handler);
            }
        } catch (IOException e) {
            // Mostra l'errore solo se il server era in esecuzione
            if (running) {
                System.err.println("Errore server: " + e.getMessage());
            }
        }
    }

    /** ritorna Stato interno per la CLI */
    public MasterState getState() {
        return state;
    }
}
