package me.internalizable.numdrassl.cluster.handler;

import me.internalizable.numdrassl.api.messaging.channel.Channels;
import me.internalizable.numdrassl.api.messaging.MessagingService;
import me.internalizable.numdrassl.api.messaging.Subscription;
import me.internalizable.numdrassl.api.messaging.message.PlayerCountMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.IntSupplier;

/**
 * Handles player count synchronization across proxies.
 *
 * <p>Periodically publishes local player count and tracks counts from
 * other proxies to provide an accurate global player count.</p>
 */
public final class PlayerCountHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerCountHandler.class);
    private static final long PUBLISH_INTERVAL_MS = 5000; // 5 seconds
    private static final long STALE_THRESHOLD_MS = 15000; // 15 seconds

    private final MessagingService messagingService;
    private final String localProxyId;
    private final IntSupplier localPlayerCount;
    private final int maxPlayers;

    private final Map<String, ProxyPlayerCount> proxyCounts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    private Subscription subscription;
    private ScheduledFuture<?> publishTask;

    public PlayerCountHandler(
            @Nonnull MessagingService messagingService,
            @Nonnull String localProxyId,
            @Nonnull IntSupplier localPlayerCount,
            int maxPlayers) {
        this.messagingService = Objects.requireNonNull(messagingService);
        this.localProxyId = Objects.requireNonNull(localProxyId);
        this.localPlayerCount = Objects.requireNonNull(localPlayerCount);
        this.maxPlayers = maxPlayers;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PlayerCount-Publisher");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Start publishing and listening for player counts.
     */
    public void start() {
        subscription = messagingService.subscribeIncludingSelf(
                Channels.PLAYER_COUNT,
                (channel, message) -> {
                    if (message instanceof PlayerCountMessage pcm) {
                        handlePlayerCount(pcm);
                    }
                }
        );

        publishTask = scheduler.scheduleAtFixedRate(
                this::publishCount,
                0,
                PUBLISH_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        LOGGER.info("Player count handler started");
    }

    /**
     * Stop publishing and listening for player counts.
     */
    public void stop() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
        if (publishTask != null) {
            publishTask.cancel(false);
            publishTask = null;
        }
        scheduler.shutdown();
        LOGGER.info("Player count handler stopped");
    }

    /**
     * Get the total player count across all proxies.
     *
     * @return the global player count
     */
    public int getGlobalPlayerCount() {
        long now = System.currentTimeMillis();
        int total = 0;

        for (ProxyPlayerCount count : proxyCounts.values()) {
            if (now - count.lastUpdate < STALE_THRESHOLD_MS) {
                total += count.playerCount;
            }
        }

        return total;
    }

    /**
     * Get the player count for a specific proxy.
     *
     * @param proxyId the proxy ID
     * @return the player count, or 0 if unknown
     */
    public int getProxyPlayerCount(@Nonnull String proxyId) {
        ProxyPlayerCount count = proxyCounts.get(proxyId);
        if (count == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        return (now - count.lastUpdate < STALE_THRESHOLD_MS) ? count.playerCount : 0;
    }

    /**
     * Get the number of tracked proxies.
     *
     * @return the number of proxies with recent player count data
     */
    public int getTrackedProxyCount() {
        long now = System.currentTimeMillis();
        return (int) proxyCounts.values().stream()
                .filter(c -> now - c.lastUpdate < STALE_THRESHOLD_MS)
                .count();
    }

    private void publishCount() {
        try {
            int count = localPlayerCount.getAsInt();
            PlayerCountMessage message = new PlayerCountMessage(
                    localProxyId,
                    Instant.now(),
                    count,
                    maxPlayers
            );
            messagingService.publish(Channels.PLAYER_COUNT, message);
        } catch (Exception e) {
            LOGGER.error("Failed to publish player count", e);
        }
    }

    private void handlePlayerCount(PlayerCountMessage message) {
        proxyCounts.put(message.sourceProxyId(), new ProxyPlayerCount(
                message.playerCount(),
                message.maxPlayers(),
                System.currentTimeMillis()
        ));
        LOGGER.debug("Updated player count for {}: {}/{}",
                message.sourceProxyId(), message.playerCount(), message.maxPlayers());
    }

    private record ProxyPlayerCount(int playerCount, int maxPlayers, long lastUpdate) {}
}

