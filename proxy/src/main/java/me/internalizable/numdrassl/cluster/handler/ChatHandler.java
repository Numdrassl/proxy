package me.internalizable.numdrassl.cluster.handler;

import me.internalizable.numdrassl.api.messaging.channel.Channels;
import me.internalizable.numdrassl.api.messaging.MessagingService;
import me.internalizable.numdrassl.api.messaging.Subscription;
import me.internalizable.numdrassl.api.messaging.message.ChatMessage;
import me.internalizable.numdrassl.session.ProxySession;
import me.internalizable.numdrassl.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles cross-proxy chat messages.
 *
 * <p>When a chat message is received from another proxy, this handler
 * delivers it to the target player if they're on this proxy.</p>
 */
public final class ChatHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatHandler.class);

    private final MessagingService messagingService;
    private final SessionManager sessionManager;
    private final String localProxyId;

    private Subscription subscription;

    public ChatHandler(
            @Nonnull MessagingService messagingService,
            @Nonnull SessionManager sessionManager,
            @Nonnull String localProxyId) {
        this.messagingService = Objects.requireNonNull(messagingService);
        this.sessionManager = Objects.requireNonNull(sessionManager);
        this.localProxyId = Objects.requireNonNull(localProxyId);
    }

    /**
     * Start listening for chat messages.
     */
    public void start() {
        subscription = messagingService.subscribe(
                Channels.CHAT,
                ChatMessage.class,
                (channel, message) -> handleChat(message)
        );
        LOGGER.info("Chat handler started");
    }

    /**
     * Stop listening for chat messages.
     */
    public void stop() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
        LOGGER.info("Chat handler stopped");
    }

    /**
     * Send a private message to a player (potentially on another proxy).
     *
     * @param targetUuid target player UUID
     * @param targetName target player name
     * @param message the message content
     * @param senderName the sender's display name
     * @param senderUuid the sender's UUID (null for system messages)
     * @return future that completes when published
     */
    public CompletableFuture<Void> sendPrivateMessage(
            @Nonnull UUID targetUuid,
            @Nonnull String targetName,
            @Nonnull String message,
            @Nonnull String senderName,
            @Nullable UUID senderUuid) {

        ChatMessage chatMessage = new ChatMessage(
                localProxyId,
                Instant.now(),
                targetUuid,
                targetName,
                message,
                senderName,
                senderUuid
        );
        return messagingService.publish(Channels.CHAT, chatMessage);
    }

    /**
     * Send a broadcast chat message to all proxies.
     *
     * @param message the message content
     * @param senderName the sender's display name
     * @param senderUuid the sender's UUID (null for system messages)
     * @return future that completes when published
     */
    public CompletableFuture<Void> broadcastChat(
            @Nonnull String message,
            @Nonnull String senderName,
            @Nullable UUID senderUuid) {

        ChatMessage chatMessage = new ChatMessage(
                localProxyId,
                Instant.now(),
                null, // No target = broadcast
                null,
                message,
                senderName,
                senderUuid
        );
        return messagingService.publish(Channels.CHAT, chatMessage);
    }

    private void handleChat(ChatMessage message) {
        if (message.isBroadcast()) {
            handleBroadcastChat(message);
        } else {
            handlePrivateChat(message);
        }
    }

    private void handleBroadcastChat(ChatMessage message) {
        LOGGER.debug("Received broadcast chat from {}: {} says: {}",
                message.sourceProxyId(), message.senderName(), message.message());

        String formatted = formatChatMessage(message.senderName(), message.message());
        sessionManager.getAllSessions().forEach(session -> {
            session.sendChatMessage(formatted);
        });
    }

    private void handlePrivateChat(ChatMessage message) {
        UUID targetUuid = message.targetPlayerUuid();
        if (targetUuid == null) {
            return;
        }

        // Find the target player on this proxy
        sessionManager.findByUuid(targetUuid).ifPresent(session -> {
            String formatted = formatPrivateMessage(message.senderName(), message.message());
            session.sendChatMessage(formatted);
            LOGGER.debug("Delivered private message from {} to session {}",
                    message.senderName(), session.getSessionId());
        });
    }

    private String formatChatMessage(String sender, String content) {
        return "<" + sender + "> " + content;
    }

    private String formatPrivateMessage(String sender, String content) {
        return "[PM from " + sender + "] " + content;
    }
}

