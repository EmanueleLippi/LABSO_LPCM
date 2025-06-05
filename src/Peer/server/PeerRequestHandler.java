package Peer.server;

import java.io.*;
import java.net.Socket;
import Peer.utils.FileManager;
import Common.Protocol;

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
            DataOutputStream dataOut = new DataOutputStream(clientSocket.getOutputStream());
        ) {
            String requestType = in.readLine();     // DOWNLOAD_REQUEST
            String fileName = in.readLine();        // nome del file

            if (Protocol.DOWNLOAD_REQUEST.equals(requestType) && fileName != null) {
                byte[] fileData = FileManager.readFile(fileName);

                if (fileData != null) {
                    out.println(Protocol.DOWNLOAD_DATA);
                    out.println(fileData.length); // invia dimensione
                    dataOut.write(fileData);      // invia contenuto binario
                    dataOut.flush();
                    System.out.println("[REQUEST HANDLER] File '" + fileName + "' inviato con successo");
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
