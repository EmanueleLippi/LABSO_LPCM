package Peer.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileManager {
    private static String sharedFolderPath;
    private static String downloadsFolderPath;

    // Imposta il percorso della cartella condivisa
    public static void setSharedFolderPath(String path) {
        sharedFolderPath = path;
    }

    // Imposta il percorso della cartella di download
    public static void setDownloadsFolderPath(String path) {
        downloadsFolderPath = path;
    }


    // Ritorna la lista dei nomi dei file presenti in shared/files/
    // Filtra solo i file veri, non le sottocartelle
    // Restituisce una List<String>, ognuno è il nome di un file
    public static List<String> getLocalFiles(){
        List<String> fileList = new ArrayList<>();
        File folder = new File(sharedFolderPath);
        if (folder.exists() && folder.isDirectory()) {
            for(File file : folder.listFiles()) {
                if (file.isFile()) {
                    fileList.add(file.getName());
                }
            }
        }
        return fileList;
    }

    // Legge un file da shared/files/ e restituisce il contenuto come byte[]
    // Serve al PeerRequestHandler per inviare un file ad altri peer
    // Ritorna null se il file non esiste o dà errore
    public static byte[] readFile(String filename) throws IOException {
        Path filePath = Path.of(sharedFolderPath, filename);
        return Files.readAllBytes(filePath);

    }

    // Controlla se un file esiste in shared/files/
    // Ritorna true se il file esiste e non è una cartella
    // Ritorna false se il file non esiste o è una cartella
    // Serve al PeerRequestHandler per verificare se un file può essere inviato
    // Utilizza il percorso condiviso impostato da setSharedFolderPath()
    public static boolean hasFile(String fileName){
        File file = new File(sharedFolderPath, fileName);
        return file.exists() && file.isFile();
    }

    // metodo che salva il contenuto ricevuto in downloads/ come un nuovo file
    // creare downloads/ se non esiste
    // Sovrascrive il file se già esiste
    // Gestisce le eccezioni con log o stampa
    public static void saveFile(String filename, byte[] data) throws IOException{
        File folder = new File(downloadsFolderPath);
        if (!folder.exists()) {
            folder.mkdirs(); // Crea la cartella se non esiste
        }
        Path filePath = Path.of(downloadsFolderPath, filename);
        Files.write(filePath, data); // Sovrascrive il file se esiste già
    }
}
