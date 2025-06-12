package Master;

import Common.DownloadLogEntry;
import Common.PeerInfo;
import Common.Protocol;
import java.net.InetAddress;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * gestisce lo stato interno del server Master:
 * mappa dei peer registrati
 * mappa con associazione risorsa -> peer
 * log dei download
 * meccanismi di sincronizzazione
 */


class MasterState {
    // Semaforo binario per proteggere operazioni critiche con accesso mutualmente esclusivo
    private final Semaphore semaphore = new Semaphore(1, true);
    // Mappa dei peer registrati: peerId -> PeerInfo
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    // Mappa risorsa -> set di peerId che la offrono
    private final Map<String, Set<String>> resourceToPeers = new ConcurrentHashMap<>();
    // lock per accesso concorrente: consente letture simultanee e scritture esclusive
    private final ReadWriteLock tableLock = new ReentrantReadWriteLock();
    // Coda thread-safe con tutti i log dei download.
    private final LinkedBlockingQueue<DownloadLogEntry> downloadLog = new LinkedBlockingQueue<>();


    /**
     * Registra un nuovo peer nel sistema.
     */
    public void registerPeer(String peerId, InetAddress address, int port, Set<String> resources) {
        try {
            // Entra in sezione critica
            semaphore.acquire();
            // Crea e salva l'oggetto PeerInfo
            PeerInfo info = new PeerInfo(peerId, address, port, resources, Instant.now());
            peers.put(peerId, info);

            // Aggiorna la mappa risorsa → peer in modo sicuro:
            tableLock.writeLock().lock();
            try {
                for (String r : resources) {
                    resourceToPeers.computeIfAbsent(r, k -> ConcurrentHashMap.newKeySet()).add(peerId);
                }
            } finally {
                tableLock.writeLock().unlock();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            //  Esce dalla sezione critica.
            semaphore.release();
        }
    }

    // Aggiorna le risorse di un peer già registrato, sovrascrivendole.

    public void updatePeerResources(String peerId, Set<String> newResources) {
        try {
            // Entra in sezione critica
            semaphore.acquire();
            // aggiorna le risorse del peerId specificato            
            PeerInfo oldInfo = peers.get(peerId);
            if (oldInfo == null) return;
            PeerInfo updated = new PeerInfo(peerId, oldInfo.getAddress(), oldInfo.getPort(), newResources, Instant.now());
            peers.put(peerId, updated);

            // lock per aggiornare la mappa risorsa → peer in modo sicuro
            tableLock.writeLock().lock();
            try {
                for (String rOld : oldInfo.getResources()) {
                    Set<String> set = resourceToPeers.get(rOld);
                    if (set != null) {
                        set.remove(peerId);
                        if (set.isEmpty()) {
                            resourceToPeers.remove(rOld);
                        }
                    }
                }
                // Cicla su tutte le risorse dichiarate da un peer
                for (String rNew : newResources) {
                    // se la risorsa non è presente, la aggiunge
                    // altrimenti aggiunge il peerId al set di peer che la offrono
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

    // rimuove un peer sia dalla mappa peers che da tutte le risorse che possedeva
    public void removePeer(String peerId) {
        try {
            // Entra in sezione critica
            semaphore.acquire();
            PeerInfo info = peers.remove(peerId);
            if (info == null) return;
            // Rimuove il peerId da tutte le risorse che possedeva
            // lock per aggiornare la mappa risorsa → peer in modo sicuro
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
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            semaphore.release();
        }
    }

    // Restituisce una copia immutabile e ordinata (TreeMap) della mappa risorse -> peer.
    public Map<String, Set<String>> listAllResources() {
        // Legge sotto lock di sola lettura
        tableLock.readLock().lock();
        try {
            // Crea una TreeMap per ordinare alfabeticamente le risorse
            Map<String, Set<String>> snapshot = new TreeMap<>();
            // Copia ogni entry (risorsa -> set di peer)
            for (Map.Entry<String, Set<String>> entry : resourceToPeers.entrySet()) {
                snapshot.put(entry.getKey(), Set.copyOf(entry.getValue()));
            }
            return snapshot;
        } finally {
            tableLock.readLock().unlock();
        }
    }

    // Metodo di utilità interno che restituisce i peer che possiedono una specifica risorsa
    private Set<String> fetchPeerIds(String resource) {
        // garantisce lettura concorrente sicura
        tableLock.readLock().lock();
        try {
            // Accede alla mappa resourceToPeers e cerca la risorsa passata come chiave
            Set<String> set = resourceToPeers.get(resource);
            // Se non esiste, restituisce un set vuoto
            // Altrimenti restituisce una copia immutabile del set di peer
            return (set == null) ? Collections.emptySet() : Set.copyOf(set);
        } finally {
            tableLock.readLock().unlock();
        }
    }

    /**
     * Genera la risposta al comando GET_PEERS_FOR_RESOURCE.
     * Se ci sono peer:
     *   PEER_FOR_RESOURCE <count> <pid1> <ip1> <port1> ... <pidN> <ipN> <portN>
     * Altrimenti:
     *   RESOURCE_NOT_FOUND <resource>
     */

    public String getPeersFor(String resource) {
        // Recupera gli ID dei peer che offrono la risorsa
        Set<String> ids = fetchPeerIds(resource);
        if (ids.isEmpty()) {
            return Protocol.RESOURCE_NOT_FOUND + " " + resource;
        }
        // Inizia a costruire il messaggio
        StringBuilder sb = new StringBuilder();
        sb.append(Protocol.PEER_FOR_RESOURCE).append(" ").append(ids.size());
        for (String pid : ids) {
            PeerInfo info = peers.get(pid);
            sb.append(" ").append(pid).append(" ").append(info.getAddress().getHostAddress()).append(" ").append(info.getPort());
        }
        return sb.toString();
    }

    /* gestisce il fallimento di un download.
    *  1- Rimuove il peer fallito dalla lista dei possessori
    *  2- Restituisce un altro peer, se esiste, per tentare di nuovo
    */ 
    public String handleDownloadFail(String resource, String failedPeer) {
        String nextPeer = null;
        try {
            // Entra in sezione critica per aggiornare la tabella
            semaphore.acquire();
            tableLock.writeLock().lock();
            try {
                // Rimuove failedPeer dalla lista.
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
            // Cerca un altro peer che possiede la risorsa
            Set<String> candidato = fetchPeerIds(resource);
            if (!candidato.isEmpty()) {
                nextPeer = candidato.iterator().next();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            semaphore.release();
        }
        return nextPeer;
    }

    // Aggiunge un log nella coda.
    public void addDownloadLog(DownloadLogEntry entry) {
    downloadLog.add(entry);
    }

    // Restituisce una copia immutabile dei log
    public List<DownloadLogEntry> getLogEntries() {
        return List.copyOf(downloadLog);
    }

    // Restituisce i dati di un singolo peer
    public PeerInfo inspectPeer(String peerId) {
        return peers.get(peerId);
    }

    // Restituisce l’insieme di peer che possiedono la risorsa
    public Set<String> inspectPeersByResource(String resource) {
        return fetchPeerIds(resource);
    }
}
