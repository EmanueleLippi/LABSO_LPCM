package Peer.server;

import Common.Protocol;
import Peer.utils.FileManager;
import Peer.utils.Logger;
import java.io.*;
import java.net.Socket;

public class PeerRequestHandler implements Runnable {

    private final Socket clientSocket;

    public PeerRequestHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

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
                    if (FileManager.hasFile(fileName)) {
                        byte[] content = FileManager.readFile(fileName);
                        
                        // Invia intestazione
                        String header = Protocol.DOWNLOAD_DATA + " " + fileName + "\n";
                        out.write(header.getBytes());
                        
                        // Invia contenuto
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
    }
}
