/*
 * Classe che rappresenta la comunicazione tra il Peer Client e il Master.
 * Si occupa di:
 * - Creare un socket per connettersi al Master
 * - Inviare comandi come REGISTER, GET_PEEERS_FOR_RESOURCE, DOWNLOAD_FAIL, DISCONNECTED
 * - Ricevere e interpretare le risposte
 * - Chiudere automaticamente la connessione al termine
 */
package Peer.client;
import Common.Protocol;
import Peer.utils.Logger;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;


public class PeerClientToMaster {
    private final String masterAddress; // Indirizzo del Master
    private final int masterPort; // Porta del Master

    // Costruttore che inizializza l'indirizzo e la porta del Master
    public PeerClientToMaster(String masterAddress, int masterPort) {
        this.masterAddress = masterAddress;
        this.masterPort = masterPort;
    }

    public void register(String peerName, int peerPort, List<String> resources){
        // Invia il comando di registrazione al Master
        // Invia il nome del peer, la porta e la lista delle risorse disponibili
        // Attende una risposta dal Master
        // Chiude tutto
        try(Socket socket = new Socket(masterAddress, masterPort)){
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true); // per inviare messaggi al Master
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream())); // per leggere le risposte dal Master
            // Invia il comando di registrazione al Master
            String joinedResources = String.join(" ", resources);
            String cmdRegister = Protocol.REGISTER
                    + " " + peerName
                    + " " + resources.size()
                    + " " + joinedResources;
            out.println(cmdRegister);

            //Legge la risposta dal Master
            String response = in.readLine();
            // Espone la risposta del Master
            Logger.info("Risposta dal Master alla registrazione: " + response);
            // TODO: Gestire la risposta del Master
            if(Protocol.REGISTER.equals(response)){
                Logger.info("Registrazione al Master completata con successo.");
            } else {
                Logger.warn("Registrazione al Master fallita: " + response);
            }
        } catch(IOException e){
            Logger.error("Errore durante la registrazione al Master: " + e.getMessage());
        }
    }

    // TODO: per il master in caso di tutto ok deve rispondere con un messaggio di conferma --> Protocol.PEER_FOR_RESOURCE
    // Metodo per Ricevere la lista dei peer che hanno una risorsa specifica
    public List<String> getPeersForFile(String resourceName){
        try(Socket socket = new Socket(masterAddress, masterPort)){
            PrintWriter out  = new PrintWriter(socket.getOutputStream(), true); // per inviare messaggi al Master
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream())); // per leggere le risposte dal Master
            // Invia la richiesta per ottenere i peer che hanno la risorsa specificata
            out.println(Protocol.GET_PEERS_FOR_RESOURCE + " " + resourceName);
            // Legge la risposta dal Master
            String response = in.readLine();
            // Controlla se la risposta è valida
            if(Protocol.PEER_FOR_RESOURCE.equals(response)){
                // Legge la lista dei peer cha hanno la risorsa
                String line;
                List<String> peers = new ArrayList<>(); // Lista per salvare i peer che hanno la risorsa
                while((line = in.readLine()) != null && !line.isEmpty()){
                    peers.add(line); // Aggiunge il peer alla lista
                }
                Logger.info("Lista dei peer che hanno la risorsa '" + resourceName + "': " + peers);
                return peers; // Ritorna la lista dei peer che hanno la risorsa

            }else if (Protocol.RESOURCE_NOT_FOUND.equals(response)){
                Logger.warn("Risorsa '" + resourceName + "' non trovata in nessun peer.");
                return List.of(); // Ritorna null se la risorsa non è stata trovata
            } else {
                Logger.error("Errore nella risposta del Master: " + response);
                return List.of(); // Ritorna null in caso di errore
            }
            
        } catch (IOException e) {
            Logger.error("Errore durante la richiesta dei peer per la risorsa '" + resourceName + "': " + e.getMessage());
            return List.of(); // Ritorna null in caso di errore
        }
    }


    // Metodo che notifica un fallimento del download di un file al Master
    public void notifyDownloadFail(String resourceName, String peerName){
        try (Socket socket = new Socket(masterAddress, masterPort)){
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true); // per inviare messaggi al Master

            // Invia il comando di download fallito al Master
            out.println(Protocol.DOWNLOAD_FAIL + " " + resourceName + " " + peerName);
            // Warn del Logger del Peer
            Logger.warn("Download fallito per la risorsa '" + resourceName + "' dal peer '" + peerName + "'. Notifica inviata al Master.");
        } catch (IOException e) {
            Logger.error("Errore durante la notifica del fallimento del download al Master: " + e.getMessage());
        }
    }

    // Metodo per la notifica di disconnessione del Peer al Master
    public void disconnect(String peerName){
        try(Socket socket = new Socket(masterAddress, masterPort)){
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true); // per inviare messaggi al Master
            // Invia il comando di disconnessione al Master
            out.println(Protocol.DISCONNECTED + " " + peerName);
            Logger.info("Disconnessione del Peer '" + peerName + "' dal Master completata.");
        } catch (IOException e) {
            Logger.error("Errore durante la disconnessione del Peer dal Master: " + e.getMessage());
        }
    }


}
