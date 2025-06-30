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

        // 1. Scegli repo non in uso oppure crea una nuova cartella
        File baseFolder = new File(System.getProperty("user.dir"), "shared/files");
        baseFolder.mkdirs();

        File myRepo = null;
        // Ripete la selezione finché non riesce a riservare una repo in modo univoco
        while (myRepo == null) {
            File[] repoFolders = baseFolder.listFiles(File::isDirectory);

            if (repoFolders != null) {
                for (File folder : repoFolders) {
                    File marker = new File(folder, ".in-use");
                    try {
                       // createNewFile restituisce false se il marker esiste già
                       // metodo atomico per evitare conflitti tra più peer, si basa sulle primitive del FileSystem, atomiche anche tra processi diversi
                        if (marker.createNewFile()) {
                            myRepo = folder;
                            break;
                        }
                    } catch (IOException e) {
                        Logger.error("Impossibile creare file .in-use per " + folder.getName());
                        return;
                    }
                }
            }
       if (myRepo == null) {
                // nessuna repo libera o nessuna repo esistente: crea nuova cartella
                int maxIndex = -1;
                if (repoFolders != null) {
                    for (File folder : repoFolders) {
                        String name = folder.getName();
                        if (name.startsWith("repo")) {
                            try {
                                int n = Integer.parseInt(name.substring(4));
                                if (n > maxIndex) maxIndex = n;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            int newIndex = maxIndex + 1;
                File newRepo = new File(baseFolder, "repo" + newIndex);
                if (!newRepo.exists() && !newRepo.mkdirs()) {
                    Logger.error("Impossibile creare la cartella " + newRepo.getName());
                    return;
                }
                File marker = new File(newRepo, ".in-use");
                try {
                    if (marker.createNewFile()) {
                        myRepo = newRepo;
                    }
                } catch (IOException e) {
                    Logger.error("Impossibile creare file .in-use per " + newRepo.getName());
                    return;
                }
            }
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
        FileManager.setDownloadsFolderPath(myRepo.getPath());

        PeerServer peerServer = new PeerServer(myPort);
        Thread serverThread = new Thread(peerServer);
        serverThread.start();

        List<String> localFiles = FileManager.getLocalFiles();
        Logger.info("File locali disponibili: " + localFiles);

        PeerClientToMaster masterClient = new PeerClientToMaster(masterIP, masterPort);
        masterClient.register(peerName, myPort, localFiles);

        // 5. Interazione con comandi
        Scanner scanner = new Scanner(System.in);
        PeerClientToPeer downloader = new PeerClientToPeer();

        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            String[] parts = line.split("\\s+", 3);
            if (parts.length == 0 || parts[0].isEmpty()) continue;

            switch (parts[0]) {
                case "listdata" -> {
                    if (parts.length == 2 && parts[1].equals("local")) {
                        localFiles = FileManager.getLocalFiles();
                        System.out.println("Local: " + localFiles);
                    } else if (parts.length == 2 && parts[1].equals("remote")) {
                        java.util.Map<String, java.util.List<String>> remote = masterClient.listRemoteResources();
                        remote.forEach((r,p) -> System.out.println(r + " -> " + p));
                    } else {
                        System.out.println("Uso: listdata local|remote");
                    }
                }
                case "add" -> {
                    if (parts.length >= 3) {
                        String name = parts[1];
                        String content = parts[2];
                        try {
                            FileManager.createLocalFile(name, content);
                            localFiles = FileManager.getLocalFiles();
                            masterClient.update(peerName, myPort, localFiles);
                            System.out.println("File aggiunto: " + name);
                        } catch (IOException e) {
                            Logger.error("Impossibile creare il file: " + e.getMessage());
                        }
                    } else {
                        System.out.println("Uso: add <nome> <contenuto>");
                    }
                }
                case "download" -> {
                    if (parts.length == 2) {
                        String resource = parts[1];
                        List<String> peers = masterClient.getPeersForFile(resource);
                        if (peers.isEmpty()) {
                            Logger.warn("Risorsa non trovata: " + resource);
                            break;
                        }
                        boolean success = false;
                        for (String info : peers) {
                            String[] t = info.split(" ");
                            String pid = t[0];
                            String ip = t[1];
                            int port = Integer.parseInt(t[2]);
                            byte[] data = downloader.requestFile(ip, port, resource);
                            boolean attemptOk = false;
                            if (data != null) {
                                try {
                                    FileManager.saveFile(resource, data);
                                    localFiles = FileManager.getLocalFiles();
                                    masterClient.update(peerName, myPort, localFiles);
                                    Logger.info("File '" + resource + "' salvato in " + myRepo.getPath() + "/.");
                                    attemptOk = true;
                                    success = true;
                                } catch (Exception e) {
                                    Logger.error("Errore salvataggio: " + e.getMessage());
                                }
                            }
                            masterClient.logDownload(resource, pid, peerName, attemptOk);
                            if (attemptOk) {
                                break;
                            } else {
                                masterClient.notifyDownloadFail(resource, pid);
                            }
                        }
                        if (!success) {
                            Logger.error("Download fallito per la risorsa " + resource);
                        }
                    } else {
                        System.out.println("Uso: download <risorsa>");
                    }
                }
             case "quit" -> {
                    masterClient.disconnect(peerName);
                    peerServer.stop();
                    try {
                        serverThread.join();
                    } catch (InterruptedException e) {
                        Logger.error("Attesa del PeerServer interrotta: " + e.getMessage());
                        Thread.currentThread().interrupt();
                    }
                    new File(myRepo, ".in-use").delete();
                    Logger.warn("Peer disconnesso e server terminato.");
                    scanner.close();
                    return;
                }
                default -> System.out.println("Comando sconosciuto.");
                }
            }
        }
}
