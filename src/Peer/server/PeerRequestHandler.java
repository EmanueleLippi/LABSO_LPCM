package peer.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class PeerRequestHandler implements Runnable {

    private Socket clientSocket;

    public PeerRequestHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try (DataInputStream input = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream())) {

            // Gestione della richiesta del client
            String command = input.readUTF();
            System.out.println("[PEER REQUEST HANDLER] Ricevuta richiesta: " + command);

            // Interpreta e gestisce il comando
            switch (command) {
    }   