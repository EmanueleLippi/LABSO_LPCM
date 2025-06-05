package Common;
/*
 * Classe Protocol per standardizzare i messaggi tra Peer e Master.
 * Contiene le costanti per i comandi utilizzati nella comunicazione.
 * Queste costanti sono utilizzate per identificare le azioni da eseguire
 * e le risposte da inviare tra i Peer e il Master.
 */

public class Protocol {

    // Da Peer a Master
    public static final String REGISTER = "REGISTER"; // Peer si registra al Master
    public static final String UPDATE = "UPDATE"; // Aggiornamento risorse disponibili
    public static final String LIST_DATA_REMOTE = "LIST_DATA_REMOTE"; // Richiesta lista risorse remote
    public static final String GET_PEERS_FOR_RESOURCE = "GET_PEERS_FOR_RESOURCE"; // Richiesta lista peer per una risorsa specifica
    public static final String DOWNLOAD_FAIL = "DOWNLOAD_FAIL"; // Download fallito notificato
    public static final String DISCONNECTED = "DISCONNECTED"; // Peer si disconnette dal Master

    // Da Master a Peer
    public static final String LIST_DATA_RESPONSE = "LIST_DATA_RESPONSE"; // Risposta alla richiesta di lista risorse remote
    public static final String PEER_FOR_RESOURCE = "PEER_FOR_RESOURCE"; // Lista dei peer che hanno una risorsa specifica
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND"; // Risorsa non trovata
    public static final String DOWNLOAD_PERMITTED = "DOWNLOAD_PERMITTED"; // Download permesso, peer può procedere

    // Da Peer a Peer
    public static final String DOWNLOAD_REQUEST = "DOWNLOAD_REQUEST"; // Richiesta di download di una risorsa
    public static final String DOWNLOAD_DATA = "DOWNLOAD_DATA"; // Invio dei dati della risorsa richiesta
    public static final String DOWNLOAD_DENIED =  "DOWNLOAD_DENIED"; // Download negato, peer non può procedere
}
