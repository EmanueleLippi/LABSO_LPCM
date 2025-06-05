package Common;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Oggetto immutabile che descrive un peer conosciuto dal Master.
 * descrive lo stato di un Peer (ID, indirizzo, porta, risorse, timestamp). Il Master lo usa per tenere traccia di chi si è registrato e quali file possiede.
 */
public class PeerInfo {
    private final String id;
    private final InetAddress address;
    private final int port;
    private final Set<String> resources;
    private final Instant lastSeen;

    /**
     * Costruisce un nuovo PeerInfo.
     *
     * id Identificativo univoco del peer (es: "peer1")
     * address Indirizzo IP del peer
     * port Porta di ascolto del peer per connessioni P2P
     * resources Insieme di risorse (nomi) possedute dal peer
     * lastSeen Istante dell’ultimo aggiornamento del peer (Instant.now())
     */
    public PeerInfo(String id,InetAddress address,int port,Set<String> resources,Instant lastSeen) {
        this.id = id;
        this.address = address;
        this.port = port;

        // Copia difensiva delle risorse, rendendole immutabili
        Set<String> tmp = new HashSet<>(resources);
        this.resources = Collections.unmodifiableSet(tmp);
        this.lastSeen = lastSeen;
    }

    /** ritorna Identificativo del peer */
    public String getId() {
        return id;
    }

    /** ritorna Indirizzo IP del peer */
    public InetAddress getAddress() {
        return address;
    }

    /** ritorna Porta di ascolto del peer */
    public int getPort() {
        return port;
    }

    /** ritorna Insieme immutabile di risorse possedute */
    public Set<String> getResources() {
        return resources;
    }

    /** ritorna Istante dell’ultimo aggiornamento */
    public Instant getLastSeen() {
        return lastSeen;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PeerInfo)) return false;
        PeerInfo other = (PeerInfo) o;
        return port == other.port
                && id.equals(other.id)
                && address.equals(other.address)
                && resources.equals(other.resources)
                && lastSeen.equals(other.lastSeen);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, address, port, resources, lastSeen);
    }

    @Override
    public String toString() {
        return "PeerInfo{" +
                "id='" + id + '\'' +
                ", address=" + address +
                ", port=" + port +
                ", resources=" + resources +
                ", lastSeen=" + lastSeen +
                '}';
    }
}
