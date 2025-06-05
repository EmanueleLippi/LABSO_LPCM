package Peer;

import java.util.List;

import Peer.client.PeerClientToMaster;

public class PeerMain {
    public static void main(String[] args) {
        // Parametri per test – da aggiornare se leggi da file
        String masterIP = "127.0.0.1";    // IP del Master
        int masterPort = 9000;           // Porta su cui il Master è in ascolto
        String peerName = "PeerA";       // Nome del peer
        int peerPort = 8081;             // Porta su cui questo peer sarà in ascolto
        List<String> sharedFiles = List.of("file1.txt", "file2.txt");

        // Crea client verso il master
        PeerClientToMaster client = new PeerClientToMaster(masterIP, masterPort);

        // 1. REGISTRAZIONE
        client.register(peerName, peerPort, sharedFiles);

        // 2. RICHIESTA DI PEER PER UN FILE
        String fileDaRichiedere = "file1.txt";
        List<String> peersCheLoHanno = client.getPeersForFile(fileDaRichiedere);

        // 3. NOTIFICA DOWNLOAD FALLITO (simulato)
        if (peersCheLoHanno.isEmpty()) {
            client.notifyDownloadFail(fileDaRichiedere, peerName);
        }

        // 4. DISCONNESSIONE
        client.disconnect(peerName);
    }
}
