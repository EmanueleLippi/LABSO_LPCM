package Peer.server;

import Common.Protocol;
import Peer.utils.FileManager;
import Peer.utils.Logger;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ConcurrentHashMap;

public class PeerRequestHandler implements Runnable {

    private final Socket clientSocket;
    // Mappa dei semafori per file: consente download concorrenti di file diversi
    private static final ConcurrentHashMap<String, Semaphore> fileSemaphores = new ConcurrentHashMap<>();
    public PeerRequestHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }


    /**
     * Metodo che gestisce le richieste dei peer.
    * Legge la richiesta dal client e, se valida, invia il file richiesto.
     * I download vengono serializzati solo sullo stesso file utilizzando
     * un semaforo dedicato per ciascun nome di file.
     */
    @Override
    public void run() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream out = clientSocket.getOutputStream();
        ) {
            String request = in.readLine();
            Logger.info("[REQUEST HANDLER] Ricevuta richiesta: " + request);

            if (request != null && request.startsWith(Protocol.DOWNLOAD_REQUEST)) {
                String[] parts = request.split(" ");
                if (parts.length == 2) {
                    String fileName = parts[1];
                    /**
                     * fa due cose principali:

Cerca un oggetto Semaphore associato a fileName nella mappa fileSemaphores.
Se non esiste, ne crea uno nuovo con 1 permesso e modalità "fair" (FIFO), e lo inserisce nella mappa.
Dettagli
fileSemaphores è probabilmente una ConcurrentHashMap<String, Semaphore>.
computeIfAbsent è un metodo thread-safe: se la chiave (fileName) non esiste, esegue la funzione (f -> new Semaphore(1, true)) per creare il valore.
Semaphore(1, true) crea un semaforo con un solo permesso e politica "fair": chi prima chiede il permesso, prima lo ottiene.
Il risultato (Semaphore) viene assegnato a fileSemaphore.
A cosa serve?
Questo pattern è usato per sincronizzare l’accesso a una risorsa (qui, probabilmente un file) tra più thread: solo uno alla volta può accedere, gli altri aspettano il loro turno.

Possibili "gotcha"
Se più thread chiedono contemporaneamente il semaforo per lo stesso file, solo uno lo crea, gli altri ottengono lo stesso oggetto.
La modalità "fair" può essere più lenta rispetto a quella "non fair", ma garantisce l’ordine di attesa.
                     */
                    Semaphore fileSemaphore = fileSemaphores.computeIfAbsent(fileName, f -> new Semaphore(1, true));
                    try {
                        fileSemaphore.acquire();

                        if (FileManager.hasFile(fileName)) {
                            byte[] content = FileManager.readFile(fileName);
                            String header = Protocol.DOWNLOAD_DATA + " " + fileName + "\n";
                            out.write(header.getBytes());
                            out.write((content.length + "\n").getBytes());
                            out.write(content);
                            out.flush();
                            Logger.info("[REQUEST HANDLER] File '" + fileName + "' inviato con " + content.length + " byte.");
                        } else {
                            String response = Protocol.DOWNLOAD_DENIED + " " + fileName + "\n";
                            out.write(response.getBytes());
                            out.flush();
                            Logger.info("[REQUEST HANDLER] File '" + fileName + "' non trovato.");
                        }
                    } finally {
                        fileSemaphore.release();
                    }
                } else {
                    String response = Protocol.DOWNLOAD_DENIED + " INVALID_FORMAT\n";
                    out.write(response.getBytes());
                    out.flush();
                    Logger.warn("[REQUEST HANDLER] Formato richiesta non valido.");
                }
            } else {
                String response = "ERROR Unsupported or malformed request\n";
                out.write(response.getBytes());
                out.flush();
                Logger.error("[REQUEST HANDLER] Comando sconosciuto.");
            }
            } catch (InterruptedException e) {
            Logger.error("[REQUEST HANDLER] Interrotto durante attesa semaforo: " + e.getMessage());
            Thread.currentThread().interrupt();

        } catch (IOException e) {
            Logger.error("[REQUEST HANDLER] Errore I/O: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                Logger.error("[REQUEST HANDLER] Errore chiusura socket: " + e.getMessage());
            }
        }
    }
}