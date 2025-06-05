package Master;

import Common.DownloadLogEntry;
import Common.PeerInfo;
import java.net.InetAddress;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * contiene tutte le strutture e i metodi necessari per tenere traccia dei peer registrati e delle risorse che ciascuno di essi possiede.
 * garantisce che tutte le operazioni di inserimento, rimozione e lookup avvengano in modo thread-safe.
 */
class MasterState {
    // Mappa peerId -> PeerInfo
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    // Mappa nomeRisorsa -> insieme di peerId che possiedono quella risorsa
    private final Map<String, Set<String>> resourceToPeers = new ConcurrentHashMap<>();
    // Lock lettura/scrittura per proteggere resourceToPeers in mutua esclusione
    private final ReadWriteLock tableLock = new ReentrantReadWriteLock();
    // Coda append-only per log dei download
    private final LinkedBlockingQueue<DownloadLogEntry> downloadLog = new LinkedBlockingQueue<>();

    // Mutex per serializzare l'assegnazione di token in caso di DOWNLOAD_FAIL
    private final Object downloadMutex = new Object();

    /**
     * Registra un nuovo peer o aggiorna un peer esistente.
     * Aggiunge anche le risorse nella tabella risorse.
     *
     * peerId   ID del peer che si registra
     * address  Indirizzo IP del peer
     * port     Porta di ascolto del peer
     * resources Insieme di risorse possedute dal peer
     */
    public void registerPeer(String peerId, InetAddress address, int port, Set<String> resources) {
        Objects.requireNonNull(peerId);
        Objects.requireNonNull(address);
        Objects.requireNonNull(resources);

        // Crea un PeerInfo con timestamp corrente
        PeerInfo info = new PeerInfo(peerId, address, port, resources, Instant.now());
        peers.put(peerId, info);

        // Aggiunge le risorse nella mappa resourceToPeers
        tableLock.writeLock().lock();
        try {
            for (String r : resources) {
                resourceToPeers.computeIfAbsent(r, k -> ConcurrentHashMap.newKeySet()).add(peerId);
            }
        } finally {
            tableLock.writeLock().unlock();
        }
    }

    /**
     * Aggiorna la lista delle risorse possedute da un peer (modifica completa).
     * Rimuove le vecchie risorse e inserisce le nuove.
     *
     *  peerId       ID del peer da aggiornare
     *  newResources Nuovo insieme di risorse possedute
     */
    public void updatePeerResources(String peerId, Set<String> newResources) {
        Objects.requireNonNull(peerId);
        Objects.requireNonNull(newResources);

        PeerInfo oldInfo = peers.get(peerId);
        if (oldInfo == null) {
            return; // peer non registrato
        }

        // Nuovo PeerInfo con aggiornamento timestamp e risorse
        PeerInfo updated = new PeerInfo(peerId, oldInfo.getAddress(), oldInfo.getPort(), newResources, Instant.now());
        peers.put(peerId, updated);

        // Prima rimuovo le vecchie risorse nella mappa
        tableLock.writeLock().lock();
        try {
            // Rimuove peerId da tutte le risorse che non ci sono più
            for (String rOld : oldInfo.getResources()) {
                Set<String> set = resourceToPeers.get(rOld);
                if (set != null) {
                    set.remove(peerId);
                    if (set.isEmpty()) {
                        resourceToPeers.remove(rOld);
                    }
                }
            }
            // Aggiunge peerId alle nuove risorse
            for (String rNew : newResources) {
                resourceToPeers.computeIfAbsent(rNew, k -> ConcurrentHashMap.newKeySet()).add(peerId);
            }
        } finally {
            tableLock.writeLock().unlock();
        }
    }

    /**
     * Rimuove un peer (in caso di DISCONNECTED). Rimuove anche tutte le sue risorse dalla tabella.
     *
     *  peerId ID del peer che si disconnette
     */
    public void removePeer(String peerId) {
        Objects.requireNonNull(peerId);

        PeerInfo info = peers.remove(peerId);
        if (info == null) return;

        tableLock.writeLock().lock();
        try {
            for (String r : info.getResources()) {
                Set<String> set = resourceToPeers.get(r);
                if (set != null) {
                    set.remove(peerId);
                    if (set.isEmpty()) {
                        resourceToPeers.remove(r);
                    }
                }
            }
        } finally {
            tableLock.writeLock().unlock();
        }
    }

    /**
     * Restituisce mappa completa risorsa -> insieme di peer (readonly copy).
     *
     *  Copia della mappa risorsa->peers
     */
    public Map<String, Set<String>> listAllResources() {
        tableLock.readLock().lock();
        try {
            Map<String, Set<String>> snapshot = new TreeMap<>();
            for (Map.Entry<String, Set<String>> entry : resourceToPeers.entrySet()) {
                snapshot.put(entry.getKey(), Set.copyOf(entry.getValue()));
            }
            return snapshot;
        } finally {
            tableLock.readLock().unlock();
        }
    }

    /**
     * Restituisce l'insieme di peer che attualmente dicono di possedere la risorsa richiesta.
     *
     *  resource Nome della risorsa
     *  Insieme di peerId (iterabile) oppure empty set se non esiste
     */
    public Set<String> getPeersFor(String resource) {
        Objects.requireNonNull(resource);

        tableLock.readLock().lock();
        try {
            Set<String> set = resourceToPeers.get(resource);
            if (set == null) {
                return Collections.emptySet();
            }
            return Set.copyOf(set);
        } finally {
            tableLock.readLock().unlock();
        }
    }

    /**
     * Gestisce un DOWNLOAD_FAIL segnalato dal peer (tentativo fallito).
     * Rimuove il peer fallito dalla lista di candidati e restituisce il prossimo peer, se presente.
     *
     *  resource   Nome della risorsa
     *  failedPeer ID del peer che ha fallito nel fornire la risorsa
     *  ID del prossimo peer candidato oppure null se la lista è vuota
     */
    public String handleDownloadFail(String resource, String failedPeer) {
        Objects.requireNonNull(resource);
        Objects.requireNonNull(failedPeer);

        synchronized (downloadMutex) {
            // Rimuovo il peer dalla mappa
            tableLock.writeLock().lock();
            try {
                Set<String> set = resourceToPeers.get(resource);
                if (set != null) {
                    set.remove(failedPeer);
                    if (set.isEmpty()) {
                        resourceToPeers.remove(resource);
                    }
                }
            } finally {
                tableLock.writeLock().unlock();
            }

            // Ora restituisco il prossimo candidato (se esiste)
            Set<String> remaining = getPeersFor(resource);
            if (!remaining.isEmpty()) {
                // Prendo arbitrariamente il primo iterato
                return remaining.iterator().next();
            } else {
                return null; // nessun altro peer disponibile
            }
        }
    }

    /**
     * Aggiunge una voce di log per un tentativo di download.
     *
     *  entry Voce di log da aggiungere
     */
    public void addDownloadLog(DownloadLogEntry entry) {
        Objects.requireNonNull(entry);
        downloadLog.add(entry);
    }

    /**
     * Restituisce la lista (copia) di tutte le voci di download loggati.
     *
     *  Lista ordinata di DownloadLogEntry
     */
    public List<DownloadLogEntry> getLogEntries() {
        return List.copyOf(downloadLog);
    }

    /**
     * Restituisce informazioni dettagliate su un singolo peer (per inspectNodes).
     *
     *  peerId ID del peer da ispezionare
     *  PeerInfo se trovato, altrimenti null
     */
    public PeerInfo inspectPeer(String peerId) {
        return peers.get(peerId);
    }

    /**
     * Restituisce insieme di peer che possiedono la risorsa indicata (per inspectNodes).
     *
     *  resource Nome risorsa
     *  Insieme di peerId o empty set
     */
    public Set<String> inspectPeersByResource(String resource) {
        return getPeersFor(resource);
    }
}
