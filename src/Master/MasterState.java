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

class MasterState {
    private final Semaphore semaphore = new Semaphore(1, true);
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> resourceToPeers = new ConcurrentHashMap<>();
    private final ReadWriteLock tableLock = new ReentrantReadWriteLock();
    private final LinkedBlockingQueue<DownloadLogEntry> downloadLog = new LinkedBlockingQueue<>();

    public void registerPeer(String peerId, InetAddress address, int port, Set<String> resources) {
        try {
            semaphore.acquire();
            PeerInfo info = new PeerInfo(peerId, address, port, resources, Instant.now());
            peers.put(peerId, info);

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
            semaphore.release();
        }
    }

    public void updatePeerResources(String peerId, Set<String> newResources) {
        try {
            semaphore.acquire();
            PeerInfo oldInfo = peers.get(peerId);
            if (oldInfo == null) return;
            PeerInfo updated = new PeerInfo(peerId, oldInfo.getAddress(), oldInfo.getPort(), newResources, Instant.now());
            peers.put(peerId, updated);

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

    public void removePeer(String peerId) {
        try {
            semaphore.acquire();
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
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            semaphore.release();
        }
    }

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

    // Interno: restituisce il set immutabile dei peerId
    private Set<String> fetchPeerIds(String resource) {
        tableLock.readLock().lock();
        try {
            Set<String> set = resourceToPeers.get(resource);
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
        Set<String> ids = fetchPeerIds(resource);
        if (ids.isEmpty()) {
            return Protocol.RESOURCE_NOT_FOUND + " " + resource;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(Protocol.PEER_FOR_RESOURCE).append(" ").append(ids.size());
        for (String pid : ids) {
            PeerInfo info = peers.get(pid);
            sb.append(" ").append(pid).append(" ").append(info.getAddress().getHostAddress()).append(" ").append(info.getPort());
        }
        return sb.toString();
    }

    public String handleDownloadFail(String resource, String failedPeer) {
        String nextPeer = null;
        try {
            semaphore.acquire();
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
            Set<String> rem = fetchPeerIds(resource);
            if (!rem.isEmpty()) {
                nextPeer = rem.iterator().next();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            semaphore.release();
        }
        return nextPeer;
    }

    public void addDownloadLog(DownloadLogEntry entry) {
    System.out.println("[DEBUG] Aggiungo log: " + entry);
    downloadLog.add(entry);
    }


    public List<DownloadLogEntry> getLogEntries() {
        return List.copyOf(downloadLog);
    }

    public PeerInfo inspectPeer(String peerId) {
        return peers.get(peerId);
    }

    public Set<String> inspectPeersByResource(String resource) {
        return fetchPeerIds(resource);
    }
}
