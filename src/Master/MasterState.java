package Master;

import Common.DownloadLogEntry;
import Common.PeerInfo;
import java.net.InetAddress;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Contiene lo stato condiviso del Master (mappa peer e indice risorse) in modo thread-safe.
 * Usa un semaforo per serializzare tutte le operazioni di scrittura.
 */
class MasterState {
    // Semaforo binario, fairness=true --> garantisce l'accesso seriale alle operazioni di scrittura
    private final Semaphore semaphore = new Semaphore(1, true);

    // Mappa peerId -> PeerInfo
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    // Mappa nomeRisorsa -> insieme di peerId che possiedono quella risorsa
    private final Map<String, Set<String>> resourceToPeers = new ConcurrentHashMap<>();
    // Lock lettura/scrittura per proteggere resourceToPeers
    private final ReadWriteLock tableLock = new ReentrantReadWriteLock();
    // Coda append-only per log dei download
    private final LinkedBlockingQueue<DownloadLogEntry> downloadLog = new LinkedBlockingQueue<>();

    /**
     * Registra un nuovo peer o aggiorna un peer esistente.
     * Aggiunge le risorse nella mappa resourceToPeers.
     */
    public void registerPeer(String peerId, InetAddress address, int port, Set<String> resources) {
        Objects.requireNonNull(peerId);
        Objects.requireNonNull(address);
        Objects.requireNonNull(resources);

        try {
            // Entrata in sezione critica scrittura --> acquisizione del semaforo
            semaphore.acquire(); 
            // Costruisce PeerInfo con timestamp corrente
            PeerInfo info = new PeerInfo(peerId, address, port, resources, Instant.now());
            peers.put(peerId, info);

            tableLock.writeLock().lock();
            try {
                for (String r : resources) {
                    // Aggiunge peerId alla lista dei possessori della risorsa r
                    resourceToPeers.computeIfAbsent(r, k -> ConcurrentHashMap.newKeySet()).add(peerId);
                }
            } finally {
                tableLock.writeLock().unlock();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            // Uscita dalla sezione critica --> rilascio del semaforo
            semaphore.release(); 
        }
    }

    /**
     * Aggiorna la lista delle risorse possedute da un peer.
     * Rimuove le vecchie risorse ed inserisce le nuove.
     */
    public void updatePeerResources(String peerId, Set<String> newResources) {
        Objects.requireNonNull(peerId);
        Objects.requireNonNull(newResources);

        try {
            semaphore.acquire();
            PeerInfo oldInfo = peers.get(peerId);
            // Peer non registrato
            if (oldInfo == null) return;

            // Costruisce PeerInfo aggiornato con nuove risorse e timestamp
            PeerInfo updated = new PeerInfo(peerId, oldInfo.getAddress(), oldInfo.getPort(), newResources, Instant.now());
            peers.put(peerId, updated);

            tableLock.writeLock().lock();
            try {
                // Rimuove peerId dalle risorse vecchie
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
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            semaphore.release();
        }
    }

    /**
     * Rimuove un peer (DISCONNECTED) e tutte le sue risorse.
     */
    public void removePeer(String peerId) {
        Objects.requireNonNull(peerId);

        try {
            semaphore.acquire();
            // Rimuove il peer
            PeerInfo info = peers.remove(peerId);
            if (info == null) return;

            tableLock.writeLock().lock();
            try {
                for (String r : info.getResources()) {
                    Set<String> set = resourceToPeers.get(r);
                    if (set != null) {
                        set.remove(peerId);
                        if (set.isEmpty()) {
                             // Elimina risorsa se nessuno la possiede più
                            resourceToPeers.remove(r);
                        }
                    }
                }
            } finally {
                tableLock.writeLock().unlock();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            semaphore.release();
        }
    }

    /**
     * Restituisce una copia della mappa risorsa --> insieme di peer (readonly).
     * Lettura non bloccante su semaforo, ma usa read-lock per resourceToPeers.
     */
    public Map<String, Set<String>> listAllResources() {
        tableLock.readLock().lock();
        try {
            Map<String, Set<String>> snapshot = new TreeMap<>();
            for (Map.Entry<String, Set<String>> entry : resourceToPeers.entrySet()) {
                // Copia immutabile
                snapshot.put(entry.getKey(), Set.copyOf(entry.getValue()));
            }
            return snapshot;
        } finally {
            tableLock.readLock().unlock();
        }
    }

    /**
     * Restituisce l'insieme di peer che possiedono una determinata risorsa.
     */
    public Set<String> getPeersFor(String resource) {
        Objects.requireNonNull(resource);

        tableLock.readLock().lock();
        try {
            Set<String> set = resourceToPeers.get(resource);
            return (set == null) ? Collections.emptySet() : Set.copyOf(set);
        } finally {
            tableLock.readLock().unlock();
        }
    }

    /**
     * Gestisce un DOWNLOAD_FAIL: rimuove il peer fallito e restituisce il prossimo candidato.
     */
    public String handleDownloadFail(String resource, String failedPeer) {
        Objects.requireNonNull(resource);
        Objects.requireNonNull(failedPeer);

        String nextPeer = null;
        try {
            semaphore.acquire();
            // Rimozione del peer fallito
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
            // Cerca il prossimo candidato --> se esistono altri peer che possiedono la risorsa, ne restituisce uno
            Set<String> remaining = getPeersFor(resource);
            if (!remaining.isEmpty()) {
                nextPeer = remaining.iterator().next();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            semaphore.release();
        }
        return nextPeer;
    }

    /**
     * Aggiunge una voce al download log.
     */
    public void addDownloadLog(DownloadLogEntry entry) {
        Objects.requireNonNull(entry);
        // Non necessita semaforo, la LinkedBlockingQueue è già thread-safe per l'append
        downloadLog.add(entry);
    }

    /**
     * Restituisce la lista di tutti i download log.
     */
    public List<DownloadLogEntry> getLogEntries() {
        // Lettura non bloccante
        return List.copyOf(downloadLog);
    }

    /**
     * Restituisce le informazioni di un singolo peer (per inspectNodes).
     */
    public PeerInfo inspectPeer(String peerId) {
        return peers.get(peerId);
    }

    /**
     * Restituisce l'insieme di peer che possiedono la risorsa (per inspectNodes).
     */
    public Set<String> inspectPeersByResource(String resource) {
        return getPeersFor(resource);
    }
}
