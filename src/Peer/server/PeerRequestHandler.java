package Peer.server;

import Common.Protocol;
import Peer.utils.FileManager;
import Peer.utils.Logger;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

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
                            Logger.info("File '" + fileName + "' inviato con " + content.length + " byte.");
                        } else {
                            String response = Protocol.DOWNLOAD_DENIED + " " + fileName + "\n";
                            out.write(response.getBytes());
                            out.flush();
                            Logger.info("File '" + fileName + "' non trovato.");
                        }
                    } finally {
                        fileSemaphore.release();
                    }
                } else {
                    String response = Protocol.DOWNLOAD_DENIED + " INVALID_FORMAT\n";
                    out.write(response.getBytes());
                    out.flush();
                    Logger.warn("Formato richiesta non valido.");
                }
            } else {
                String response = "ERROR Unsupported or malformed request\n";
                out.write(response.getBytes());
                out.flush();
                Logger.error("Comando sconosciuto.");
            }
            } catch (InterruptedException e) {
            Logger.error("Interrotto durante attesa semaforo: " + e.getMessage());
            Thread.currentThread().interrupt();

        } catch (IOException e) {
            Logger.error("Errore I/O: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                Logger.error("Errore chiusura socket: " + e.getMessage());
            }
        }
    }
}