package me.internalizable.numdrassl.cluster;

import me.internalizable.numdrassl.api.cluster.ProxyInfo;
import me.internalizable.numdrassl.api.event.cluster.ProxyJoinClusterEvent;
import me.internalizable.numdrassl.api.event.cluster.ProxyLeaveClusterEvent;
import me.internalizable.numdrassl.api.event.cluster.ProxyLeaveClusterEvent.LeaveReason;
import me.internalizable.numdrassl.api.messaging.Channels;
import me.internalizable.numdrassl.api.messaging.MessagingService;
import me.internalizable.numdrassl.api.messaging.Subscription;
import me.internalizable.numdrassl.api.messaging.message.HeartbeatMessage;
import me.internalizable.numdrassl.event.api.NumdrasslEventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tracks online proxy instances in the cluster.
 *
 * <p>Maintains a local view of the cluster by listening to heartbeat messages.
 * Automatically removes stale proxies that stop sending heartbeats.</p>
 */
public final class ProxyRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyRegistry.class);
    private static final long HEARTBEAT_TIMEOUT_MS = 30_000; // 30 seconds
    private static final long CLEANUP_INTERVAL_MS = 10_000; // 10 seconds

    private final Map<String, ProxyInfo> proxies = new ConcurrentHashMap<>();
    private final MessagingService messagingService;
    private final NumdrasslEventManager eventManager;
    private final String localProxyId;
    private final String version;

    private final ScheduledExecutorService cleanupExecutor;
    private Subscription heartbeatSubscription;

    public ProxyRegistry(
            @Nonnull MessagingService messagingService,
            @Nonnull NumdrasslEventManager eventManager,
            @Nonnull String localProxyId,
            @Nonnull String version) {
        this.messagingService = messagingService;
        this.eventManager = eventManager;
        this.localProxyId = localProxyId;
        this.version = version;

        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "numdrassl-cluster-cleanup");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Start the registry and begin listening for heartbeats.
     */
    public void start() {
        // Subscribe to heartbeat messages
        heartbeatSubscription = messagingService.subscribe(
                Channels.HEARTBEAT,
                HeartbeatMessage.class,
                (channel, message) -> handleHeartbeat(message)
        );

        // Start cleanup task
        cleanupExecutor.scheduleAtFixedRate(
                this::cleanupStaleProxies,
                CLEANUP_INTERVAL_MS,
                CLEANUP_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        LOGGER.info("Proxy registry started");
    }

    /**
     * Stop the registry.
     */
    public void stop() {
        if (heartbeatSubscription != null) {
            heartbeatSubscription.unsubscribe();
        }
        cleanupExecutor.shutdown();
        proxies.clear();
        LOGGER.info("Proxy registry stopped");
    }

    /**
     * Get all online proxies.
     *
     * @return collection of proxy info
     */
    @Nonnull
    public Collection<ProxyInfo> getOnlineProxies() {
        return proxies.values();
    }

    /**
     * Get a specific proxy by ID.
     *
     * @param proxyId the proxy ID
     * @return the proxy info, or empty if not found
     */
    @Nonnull
    public Optional<ProxyInfo> getProxy(@Nonnull String proxyId) {
        return Optional.ofNullable(proxies.get(proxyId));
    }

    /**
     * Get the number of online proxies.
     *
     * @return the proxy count
     */
    public int getProxyCount() {
        return proxies.size();
    }

    /**
     * Get the total player count across all proxies.
     *
     * @return the global player count
     */
    public int getGlobalPlayerCount() {
        return proxies.values().stream()
                .mapToInt(ProxyInfo::playerCount)
                .sum();
    }

    /**
     * Remove a proxy from the registry.
     *
     * @param proxyId the proxy ID
     * @param reason the reason for removal
     */
    public void removeProxy(@Nonnull String proxyId, @Nonnull LeaveReason reason) {
        ProxyInfo removed = proxies.remove(proxyId);
        if (removed != null) {
            LOGGER.info("Proxy {} left cluster: {}", proxyId, reason);
            eventManager.fire(new ProxyLeaveClusterEvent(removed, reason));
        }
    }

    private void handleHeartbeat(HeartbeatMessage heartbeat) {
        String proxyId = heartbeat.sourceProxyId();

        // Handle shutdown heartbeat
        if (heartbeat.shuttingDown()) {
            removeProxy(proxyId, LeaveReason.GRACEFUL_SHUTDOWN);
            return;
        }

        // Check if this is a new proxy
        boolean isNew = !proxies.containsKey(proxyId);

        // Update or add proxy info
        ProxyInfo info = new ProxyInfo(
                proxyId,
                heartbeat.region(),
                new InetSocketAddress(heartbeat.host(), heartbeat.port()),
                heartbeat.playerCount(),
                1000, // Max players - could be included in heartbeat
                heartbeat.uptimeMillis(),
                Instant.now(),
                version
        );

        proxies.put(proxyId, info);

        if (isNew && !proxyId.equals(localProxyId)) {
            LOGGER.info("Proxy {} joined cluster (region: {}, players: {})",
                    proxyId, heartbeat.region(), heartbeat.playerCount());
            eventManager.fire(new ProxyJoinClusterEvent(info));
        }
    }

    private void cleanupStaleProxies() {
        long now = Instant.now().toEpochMilli();

        proxies.entrySet().removeIf(entry -> {
            ProxyInfo info = entry.getValue();
            if (info.isStale(HEARTBEAT_TIMEOUT_MS) && !entry.getKey().equals(localProxyId)) {
                LOGGER.warn("Proxy {} heartbeat timeout (last seen: {}ms ago)",
                        entry.getKey(), now - info.lastHeartbeat().toEpochMilli());
                eventManager.fire(new ProxyLeaveClusterEvent(info, LeaveReason.HEARTBEAT_TIMEOUT));
                return true;
            }
            return false;
        });
    }
}

