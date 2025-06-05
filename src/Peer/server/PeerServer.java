package Peer.server;
/**
 * Classe PeerServer
 * 
 * Questa classe rappresenta il server TCP del peer, responsabile di aprire una porta
 * e rimanere in ascolto di connessioni in ingresso da altri peer.
 * 
 * Per ogni connessione accettata, crea un nuovo thread che esegue PeerRequestHandler,
 * delegando così la gestione della comunicazione a un gestore dedicato per la concorrenza.
 * 
 * Supporta l’avvio e lo stop pulito del server, permettendo di chiudere la porta in modo sicuro.
 */

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class PeerServer implements Runnable {

    private final int port;
    private boolean running = false;
    private ServerSocket serverSocket;

    public PeerServer(int port) {
        this.port = port;
    }

    // TODO: Valutare se tenerlo
     /**
     * Metodo getter per la porta su cui il server sta ascoltando.
     * @return porta del server.
     */
    public int getPort() {
        return port;
    }

    /*
    * Metodo principale del server peer che rimane in ascolto sulla porta specificata.
    * Per ogni nuova connessione accettata, crea un nuovo thread eseguendo PeerRequestHandler,
    * consentendo così di gestire più connessioni contemporaneamente.
    * In questo modo abilita la concorrenza creando thread separati per ogni client,
    * mentre il server principale continua ad accettare nuove connessioni senza bloccare.
    */
    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(1000); // timeout di 1 secondo per accept()

            System.out.println("[PEER SERVER] Avviato sulla porta " + port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[PEER SERVER] Connessione ricevuta da " + clientSocket.getInetAddress());

                    // Avvia un nuovo thread per gestire la connessione
                    new Thread(new PeerRequestHandler(clientSocket)).start();

                } catch (SocketTimeoutException e) {
                    // Timeout: nessuna connessione arrivata in questo intervallo,
                    // controllo variabile running e continuo il ciclo
                }
            }
        } catch (IOException e) {
            System.err.println("[PEER SERVER] Errore nella creazione del ServerSocket: " + e.getMessage());
            running = false;
        } finally {
            closeServerSocket();
        }

        System.out.println("[PEER SERVER] Server terminato.");
    }

    /*
    * Ferma il server chiudendo il ServerSocket e interrompendo il ciclo di ascolto
    */
    public synchronized void stop() {
        running = false;
        closeServerSocket();
        System.out.println("[PEER SERVER] Stop richiesto");
    }

    /*
    * Metodo che restituisce lo stato di esecuzione del server.
    * @return true se il server è in esecuzione, false altrimenti.
    */
    public boolean isRunning() {
        return running;
    }

    /*
    * Metodo privato per chiudere il ServerSocket in modo sicuro
    */
    private void closeServerSocket() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                System.out.println("[PEER SERVER] ServerSocket chiuso.");
            } catch (IOException e) {
                System.err.println("[PEER SERVER] Errore chiusura ServerSocket: " + e.getMessage());
            }
        }
    }

}