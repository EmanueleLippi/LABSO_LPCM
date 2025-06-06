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
import java.util.Map;
import java.util.HashMap;


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
                    + " " + peerPort
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

     // Aggiorna le risorse del peer già registrato
    public void update(String peerName, int peerPort, List<String> resources){
        try(Socket socket = new Socket(masterAddress, masterPort)){
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String joinedResources = String.join(" ", resources);
            String cmdUpdate = Protocol.UPDATE
                    + " " + peerName
                    + " " + peerPort
                    + " " + resources.size()
                    + " " + joinedResources;
            out.println(cmdUpdate);

            String response = in.readLine();
            Logger.info("Risposta dal Master all'update: " + response);
        } catch(IOException e){
            Logger.error("Errore durante l'update al Master: " + e.getMessage());
        }
    }

    // Metodo per ricevere la lista dei peer che possiedono una risorsa specifica
    public List<String> getPeersForFile(String resourceName){
        try(Socket socket = new Socket(masterAddress, masterPort)){
            PrintWriter out  = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println(Protocol.GET_PEERS_FOR_RESOURCE + " " + resourceName);
            // Legge la risposta dal Master
            String response = in.readLine();
            if(response != null && response.startsWith(Protocol.PEER_FOR_RESOURCE)){
                String[] parts = response.split("\\s+");
                int count = Integer.parseInt(parts[1]);
                List<String> peers = new ArrayList<>();
                for(int i=0;i<count;i++){
                    int idx = 2 + i*3;
                    if(parts.length >= idx+3){
                        String pid = parts[idx];
                        String ip = parts[idx+1];
                        String port = parts[idx+2];
                        peers.add(pid + " " + ip + " " + port);
                    }
                }
                return peers; // Ritorna la lista dei peer che possiedono la risorsa
                
            } else if(response != null && response.startsWith(Protocol.RESOURCE_NOT_FOUND)){
                return List.of();
            
        } else{
                Logger.error("Risposta non valida dal Master: " + response);
                return List.of();
            }
        } catch (IOException e) {
            Logger.error("Errore durante la richiesta dei peer per la risorsa '" + resourceName + "': " + e.getMessage());
            return List.of(); // Ritorna null in caso di errore
        }
    }

    // Richiede al Master la lista completa delle risorse in rete
    public Map<String, List<String>> listRemoteResources(){
        Map<String, List<String>> result = new HashMap<>();
        try(Socket socket = new Socket(masterAddress, masterPort)){
            PrintWriter out  = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println(Protocol.LIST_DATA_REMOTE);

            String response = in.readLine();
            if(response != null && response.startsWith(Protocol.LIST_DATA_RESPONSE)){
                String[] tokens = response.split("\\s+");
                int idx = 1;
                int total = Integer.parseInt(tokens[idx++]);
                for(int i=0;i<total;i++){
                    String res = tokens[idx++];
                    int count = Integer.parseInt(tokens[idx++]);
                    List<String> peers = new ArrayList<>();
                    for(int j=0;j<count;j++){
                        peers.add(tokens[idx++]);
                    }
                    result.put(res, peers);
                }
            } else {
                Logger.error("Risposta non valida dal Master: " + response);
            }
        } catch(IOException e){
            Logger.error("Errore durante la richiesta listdata remote: " + e.getMessage());
        }
        return result;
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
