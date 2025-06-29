package Common;

import java.time.Instant;
import java.util.Objects;

/**
 * Rappresenta un tentativo di download tra due peer.
 * tiene traccia di ogni tentativo di download (chi richiede cosa, chi serve, esito, orario)
 */
public class DownloadLogEntry {
    private final Instant timestamp;
    private final String resource;
    private final String fromPeer;
    private final String toPeer;
    private final boolean success;

    /**
     * Costruisce un nuovo DownloadLogEntry.
     *
     * timestamp Istante in cui è stato tentato il download
     * resource Nome della risorsa richiesta
     * fromPeer ID del peer che possiede (o presumibilmente possiede) la risorsa
     * toPeer ID del peer che ha richiesto il download
     * success true se il download è riuscito, altrimenti false
     */
    public DownloadLogEntry(Instant timestamp,String resource,String fromPeer,String toPeer,boolean success) {
        this.timestamp = timestamp;
        this.resource = resource;
        this.fromPeer = fromPeer;
        this.toPeer = toPeer;
        this.success = success;
    }

    /** ritorna Istante del tentativo di download */
    public Instant getTimestamp() {
        return timestamp;
    }

    /** ritorna Nome della risorsa */
    public String getResource() {
        return resource;
    }

    /** ritorna ID del peer sorgente */
    public String getFromPeer() {
        return fromPeer;
    }

    /** ritorna ID del peer destinatario */
    public String getToPeer() {
        return toPeer;
    }

    /** ritorna true se il download è riuscito, altrimenti false */
    public boolean isSuccess() {
        return success;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        // Se o non è un oggetto della classe DownloadLogEntry, allora non può essere uguale
        if (!(o instanceof DownloadLogEntry)) return false;
        DownloadLogEntry other = (DownloadLogEntry) o;
        // Se tutti questi campi coincidono, allora i due oggetti rappresentano lo stesso evento di download
        return success == other.success
                && timestamp.equals(other.timestamp)
                && resource.equals(other.resource)
                && fromPeer.equals(other.fromPeer)
                && toPeer.equals(other.toPeer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, resource, fromPeer, toPeer, success);
    }

    @Override
    public String toString() {
        return "DownloadLogEntry{" +
                "timestamp=" + timestamp +
                ", resource='" + resource + '\'' +
                ", fromPeer='" + fromPeer + '\'' +
                ", toPeer='" + toPeer + '\'' +
                ", success=" + success +
                '}';
    }
}
