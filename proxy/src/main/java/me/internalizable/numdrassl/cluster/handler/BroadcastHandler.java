package me.internalizable.numdrassl.cluster.handler;

import me.internalizable.numdrassl.api.messaging.channel.BroadcastType;
import me.internalizable.numdrassl.api.messaging.channel.Channels;
import me.internalizable.numdrassl.api.messaging.MessagingService;
import me.internalizable.numdrassl.api.messaging.Subscription;
import me.internalizable.numdrassl.api.messaging.message.BroadcastMessage;
import me.internalizable.numdrassl.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Handles cluster-wide broadcast messages.
 *
 * <p>When a broadcast is received from another proxy, this handler
 * delivers it to all local players.</p>
 */
public final class BroadcastHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BroadcastHandler.class);

    private final MessagingService messagingService;
    private final SessionManager sessionManager;
    private final String localProxyId;

    private volatile Subscription subscription;

    public BroadcastHandler(
            @Nonnull MessagingService messagingService,
            @Nonnull SessionManager sessionManager,
            @Nonnull String localProxyId) {
        this.messagingService = Objects.requireNonNull(messagingService);
        this.sessionManager = Objects.requireNonNull(sessionManager);
        this.localProxyId = Objects.requireNonNull(localProxyId);
    }

    /**
     * Start listening for broadcast messages.
     */
    public synchronized void start() {
        if (subscription != null) {
            return; // Already started
        }
        subscription = messagingService.subscribe(
                Channels.BROADCAST,
                BroadcastMessage.class,
                (channel, message) -> handleBroadcast(message)
        );
        LOGGER.info("Broadcast handler started");
    }

    /**
     * Stop listening for broadcast messages.
     */
    public synchronized void stop() {
        Subscription sub = subscription;
        if (sub != null) {
            sub.unsubscribe();
            subscription = null;
        }
        LOGGER.info("Broadcast handler stopped");
    }

    /**
     * Send a broadcast to all proxies (including local).
     *
     * @param type the broadcast type
     * @param content the broadcast content
     * @return future that completes when published
     */
    public CompletableFuture<Void> broadcast(@Nonnull BroadcastType type, @Nonnull String content) {
        BroadcastMessage message = new BroadcastMessage(
                localProxyId,
                Instant.now(),
                type.getId(),
                content
        );
        return messagingService.publish(Channels.BROADCAST, message);
    }

    /**
     * Send a broadcast to all proxies (including local).
     *
     * @param type the broadcast type as string (for backward compatibility)
     * @param content the broadcast content
     * @return future that completes when published
     * @deprecated Use {@link #broadcast(BroadcastType, String)} instead
     */
    @Deprecated
    public CompletableFuture<Void> broadcast(@Nonnull String type, @Nonnull String content) {
        BroadcastType broadcastType = BroadcastType.fromId(type, BroadcastType.CUSTOM);
        return broadcast(broadcastType, content);
    }

    private void handleBroadcast(BroadcastMessage message) {
        LOGGER.debug("Received broadcast from {}: [{}] {}",
                message.sourceProxyId(), message.broadcastType(), message.content());

        BroadcastType type = BroadcastType.fromId(message.broadcastType());

        if (type == null) {
            LOGGER.debug("Unknown broadcast type: {}", message.broadcastType());
            return;
        }

        switch (type) {
            case ANNOUNCEMENT -> deliverToAllPlayers(message.content());
            case ALERT -> deliverAlert(message.content());
            case MAINTENANCE -> handleMaintenance(message.content());
            case CUSTOM -> LOGGER.debug("Custom broadcast received: {}", message.content());
        }
    }

    private void deliverToAllPlayers(String content) {
        sessionManager.getAllSessions().forEach(session -> {
            // Send message through the session's packet sender
            // Note: In the future, this should use the Player API
            session.sendChatMessage(content);
        });
    }

    private void deliverAlert(String content) {
        // Alerts could be styled differently
        String alertMessage = "[ALERT] " + content;
        deliverToAllPlayers(alertMessage);
    }

    private void handleMaintenance(String content) {
        LOGGER.warn("Maintenance broadcast: {}", content);
        String maintenanceMessage = "[MAINTENANCE] " + content;
        deliverToAllPlayers(maintenanceMessage);
    }
}

