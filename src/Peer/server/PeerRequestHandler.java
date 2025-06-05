package Peer.server;

import Common.Protocol;
import Peer.utils.FileManager;
import Peer.utils.Logger;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.Semaphore;

public class PeerRequestHandler implements Runnable {

    private final Socket clientSocket;
    private static final Semaphore semaphore = new Semaphore(1, true);

    public PeerRequestHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }


    //TODO: Valuatre se mettere semafori per file e non per richiesta
    /**
     * Metodo che gestisce le richieste dei peer.
     * Si occupa di leggere la richiesta dal client, verificare se il file esiste
     * e inviarlo se disponibile, oppure rispondere con un messaggio di errore.
     * Utilizza un semaforo per garantire l'accesso esclusivo alla sezione critica.
     */
    @Override
    public void run() {
    try {
        semaphore.acquire();  // Entra in sezione critica
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream out = clientSocket.getOutputStream();
        ) {
            String request = in.readLine();
            Logger.info("[REQUEST HANDLER] Ricevuta richiesta: " + request);

            /* Gestione della richiesta di download
            Controlla se la richiesta è valida e inizia con il prefisso corretto
            Se la richiesta è di tipo DOWNLOAD_REQUEST, verifica il file richiesto
            Se il file esiste, lo legge e lo invia al client
            Se il file non esiste, risponde con DOWNLOAD_DENIED
            Se la richiesta non è valida, risponde con un errore generico
            */
            if (request != null && request.startsWith(Protocol.DOWNLOAD_REQUEST)) {
                String[] parts = request.split(" ");
                if (parts.length == 2) {
                    String fileName = parts[1];
                    if (FileManager.hasFile(fileName)) {
                        byte[] content = FileManager.readFile(fileName);
                        String header = Protocol.DOWNLOAD_DATA + " " + fileName + "\n";
                        out.write(header.getBytes());
                        out.write(content);
                        out.flush();
                        Logger.info("[REQUEST HANDLER] File '" + fileName + "' inviato con successo.");
                    } else {
                        String response = Protocol.DOWNLOAD_DENIED + " " + fileName + "\n";
                        out.write(response.getBytes());
                        out.flush();
                        Logger.info("[REQUEST HANDLER] File '" + fileName + "' non trovato.");
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

        } catch (IOException e) {
            Logger.error("[REQUEST HANDLER] Errore I/O: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                Logger.error("[REQUEST HANDLER] Errore chiusura socket: " + e.getMessage());
            }
        }
    } catch (InterruptedException e) {
        Logger.error("[REQUEST HANDLER] Interrotto durante attesa semaforo: " + e.getMessage());
        Thread.currentThread().interrupt();
    } finally {
        semaphore.release();  // Esce dalla sezione critica
    }
}

}
