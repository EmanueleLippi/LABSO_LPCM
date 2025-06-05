package Peer.server;

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
    public void run() {}
}   