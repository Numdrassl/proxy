package me.internalizable.numdrassl.cluster;

import me.internalizable.numdrassl.api.messaging.channel.Channels;
import me.internalizable.numdrassl.api.messaging.MessagingService;
import me.internalizable.numdrassl.api.messaging.message.HeartbeatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/**
 * Publishes periodic heartbeat messages to the cluster.
 *
 * <p>Heartbeats contain the proxy's current state and are used by other proxies
 * to track cluster membership and health.</p>
 */
public final class HeartbeatPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatPublisher.class);
    private static final long HEARTBEAT_INTERVAL_MS = 5_000; // 5 seconds

    private final MessagingService messagingService;
    private final String proxyId;
    private final String region;
    private final InetSocketAddress publicAddress;
    private final IntSupplier playerCountSupplier;
    private final long startTime;

    private final ScheduledExecutorService executor;
    private volatile ScheduledFuture<?> heartbeatTask;

    public HeartbeatPublisher(
            @Nonnull MessagingService messagingService,
            @Nonnull String proxyId,
            @Nonnull String region,
            @Nonnull InetSocketAddress publicAddress,
            @Nonnull IntSupplier playerCountSupplier) {
        this.messagingService = messagingService;
        this.proxyId = proxyId;
        this.region = region;
        this.publicAddress = publicAddress;
        this.playerCountSupplier = playerCountSupplier;
        this.startTime = System.currentTimeMillis();

        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "numdrassl-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Start publishing heartbeats.
     */
    public synchronized void start() {
        if (heartbeatTask != null) {
            return;
        }

        // Send initial heartbeat immediately
        publishHeartbeat(false);

        // Schedule periodic heartbeats
        heartbeatTask = executor.scheduleAtFixedRate(
                () -> publishHeartbeat(false),
                HEARTBEAT_INTERVAL_MS,
                HEARTBEAT_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        LOGGER.info("Heartbeat publisher started (interval: {}ms)", HEARTBEAT_INTERVAL_MS);
    }

    /**
     * Stop publishing heartbeats and send a shutdown notification.
     */
    public synchronized void stop() {
        ScheduledFuture<?> task = heartbeatTask;
        if (task != null) {
            task.cancel(false);
            heartbeatTask = null;
        }

        // Send shutdown heartbeat
        publishHeartbeat(true);

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        LOGGER.info("Heartbeat publisher stopped");
    }

    private void publishHeartbeat(boolean shuttingDown) {
        HeartbeatMessage heartbeat = new HeartbeatMessage(
                proxyId,
                Instant.now(),
                region,
                publicAddress.getHostString(),
                publicAddress.getPort(),
                playerCountSupplier.getAsInt(),
                System.currentTimeMillis() - startTime,
                shuttingDown
        );

        messagingService.publish(Channels.HEARTBEAT, heartbeat)
                .exceptionally(ex -> {
                    LOGGER.warn("Failed to publish heartbeat: {}", ex.getMessage());
                    return null;
                });
    }
}

