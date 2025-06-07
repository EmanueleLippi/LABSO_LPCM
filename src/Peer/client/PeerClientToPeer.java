package Peer.client;

import Common.Protocol;
import Peer.utils.Logger;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

// Questa classe è un client che si connette ad altri peer per scaricare risorse
// Implementa i metodi per connettersi a un peer, inviare richieste di download e ricevere file
// La logica di connessione e comunicazione con i peer sarà implementata qui
public class PeerClientToPeer {
    // metodo per scaricare un file da un peer
    public byte[] requestFile(String peerAddress, int peerPort, String fileName) {
        try (Socket socket = new Socket(peerAddress, peerPort);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            DataInputStream datain = new DataInputStream(socket.getInputStream())) {

            // 1. Invia la richiesta di download
            out.println(Protocol.DOWNLOAD_REQUEST + " " + fileName);


            // 2. Attende risposta
            String response = in.readLine();
            if (response != null && response.startsWith(Protocol.DOWNLOAD_DATA)) {
                String[] headerParts = response.split(" ",2);
                String headerFile = headerParts.length > 1 ? headerParts[1] : fileName;
                Logger.info("Download del file '" + headerFile + "' avviato da " + peerAddress + ":" + peerPort);

                // 3. Legge la dimensione del file (riga successiva)
                String sizeStr = in.readLine();
                int fileSize = Integer.parseInt(sizeStr);

                byte[] fileData = new byte[fileSize];
                datain.readFully(fileData);  // legge esattamente fileSize byte

                Logger.info("Download completato. Ricevuti " + fileSize + " byte.");
                return fileData;

            } else if (response != null && response.startsWith(Protocol.DOWNLOAD_DENIED)) {
                String[] headerParts = response.split(" ",2);
                String deniedFile = headerParts.length > 1 ? headerParts[1] : fileName;
                Logger.warn("Download del file '" + deniedFile + "' rifiutato da " + peerAddress + ":" + peerPort);
                return null;

            } else {
                Logger.error("Risposta non riconosciuta dal peer: " + response);
                return null;
            }

        } catch (Exception e) {
            Logger.error("Errore nel download da " + peerAddress + ":" + peerPort + ": " + e.getMessage());
            return null;
        }
    }
}
