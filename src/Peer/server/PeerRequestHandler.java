package Peer.server;

import java.io.*;
import java.net.Socket;
import Peer.utils.FileManager;

public class PeerRequestHandler implements Runnable {

    private final Socket clientSocket;

    public PeerRequestHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        ) {
            String request = in.readLine();
            System.out.println("[REQUEST HANDLER] Ricevuta richiesta: " + request);

            if (request != null && request.startsWith("DOWNLOAD_REQUEST")) {
                String[] parts = request.split(" ");
                if (parts.length == 2) {
                    String fileName = parts[1];
                    if (fileManager.hasFile(fileName)) {
                        String content = fileManager.readFile(fileName);
                        out.println("DOWNLOAD_DATA " + fileName);
                        out.println(content);
                    } else {
                        out.println("DOWNLOAD_DENIED " + fileName);
                    }
                } else {
                    out.println(Protocol.DOWNLOAD_DENIED);
                    System.out.println("[REQUEST HANDLER] File '" + fileName + "' non trovato");
                }
            } else {
                out.println("ERROR Unsupported or malformed request");
            }

        } catch (IOException e) {
            System.err.println("[REQUEST HANDLER] Errore I/O: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("[REQUEST HANDLER] Errore chiusura socket: " + e.getMessage());
            }
        }
    }
}
