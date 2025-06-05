package Peer;

import Peer.client.PeerClientToMaster;
import Peer.client.PeerClientToPeer;
import Peer.server.PeerServer;
import Peer.utils.FileManager;
import Peer.utils.Logger;

import java.util.*;
import java.io.*;

public class Client {
    public static void main(String[] args) {
        if (args.length != 2) {
            Logger.error("Utilizzo corretto: java Client <masterAddress> <masterPort>");
            return;
        }

        String masterIP = args[0];
        int masterPort = Integer.parseInt(args[1]);

        // 1. Scegli repo non in uso
        File baseFolder = new File("shared/files");
        File[] repoFolders = baseFolder.listFiles(File::isDirectory);
        if (repoFolders == null || repoFolders.length == 0) {
            Logger.error("Nessuna cartella trovata in shared/files/. Impossibile avviare il peer.");
            return;
        }

        File myRepo = null;
        for (File folder : repoFolders) {
            File marker = new File(folder, ".in-use");
            if (!marker.exists()) {
                myRepo = folder;
                try {
                    marker.createNewFile(); // segna la repo come in uso
                } catch (IOException e) {
                    Logger.error("Impossibile creare file .in-use per " + folder.getName());
                    return;
                }
                break;
            }
        }

        if (myRepo == null) {
            Logger.error("Tutte le repo sono già in uso. Impossibile avviare un nuovo peer.");
            return;
        }

        // 2. Porta casuale
        Random rand = new Random();
        int myPort = 10000 + rand.nextInt(10000);

        // 3. Crea nome peer univoco
        String peerName = myRepo.getName() + "_" + myPort;

        Logger.info("Peer inizializzato come '" + peerName + "' con cartella: " + myRepo.getPath());
        Logger.info("Avvio del PeerServer sulla porta: " + myPort);

        // 4. Configura cartelle
        FileManager.setSharedFolderPath(myRepo.getPath());
        FileManager.setDownloadsFolderPath("download");

        PeerServer peerServer = new PeerServer(myPort);
        new Thread(peerServer).start();

        List<String> localFiles = FileManager.getLocalFiles();
        Logger.info("File locali disponibili: " + localFiles);

        PeerClientToMaster masterClient = new PeerClientToMaster(masterIP, masterPort);
        masterClient.register(peerName, myPort, localFiles);

        // 5. Interazione
        Scanner scanner = new Scanner(System.in);
        PeerClientToPeer downloader = new PeerClientToPeer();

        while (true) {
            System.out.print("> Digita nome file da scaricare (o 'exit'): ");
            String input = scanner.nextLine();

            if ("exit".equalsIgnoreCase(input)) {
                masterClient.disconnect(peerName);
                peerServer.stop();

                // Elimina file .in-use al termine
                new File(myRepo, ".in-use").delete();

                Logger.warn("Peer disconnesso e server terminato.");
                break;
            }

            List<String> peersWithFile = masterClient.getPeersForFile(input);
            if (peersWithFile.isEmpty()) continue;

            String peerInfo = peersWithFile.get(0);
            String[] tokens = peerInfo.split(" ");
            String ip = tokens[1];
            int port = Integer.parseInt(tokens[2]);

            byte[] data = downloader.requestFile(ip, port, input);
            if (data != null) {
                try {
                    FileManager.saveFile(input, data);
                    Logger.info("File '" + input + "' salvato correttamente in 'download/'.");
                } catch (Exception e) {
                    Logger.error("Errore durante il salvataggio del file: " + e.getMessage());
                }
            } else {
                masterClient.notifyDownloadFail(input, tokens[0]);
            }
        }

        scanner.close();
    }
}
