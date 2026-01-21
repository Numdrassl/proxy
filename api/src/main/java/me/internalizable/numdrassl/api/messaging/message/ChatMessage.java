package me.internalizable.numdrassl.api.messaging.message;

import me.internalizable.numdrassl.api.messaging.ChannelMessage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.UUID;

/**
 * Cross-proxy chat message for delivering messages to players on other proxies.
 *
 * <p>Enables features like:</p>
 * <ul>
 *   <li>Private messaging across proxies</li>
 *   <li>Global chat broadcasts</li>
 *   <li>Staff communication channels</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Send a private message to a player (might be on another proxy)
 * ChatMessage msg = new ChatMessage(
 *     proxyId, Instant.now(),
 *     targetUuid, targetName,
 *     "Hello from another proxy!",
 *     senderName, senderUuid
 * );
 * messaging.publish(Channels.CHAT, msg);
 *
 * // Subscribe to receive chat messages
 * @Subscribe(SystemChannel.CHAT)
 * public void onChat(ChatMessage chat) {
 *     if (chat.isBroadcast()) {
 *         // Global message
 *     } else {
 *         // Targeted message
 *     }
 * }
 * }</pre>
 *
 * @param sourceProxyId the proxy that originated the message
 * @param timestamp when the message was sent
 * @param targetPlayerUuid UUID of target player (null for broadcast)
 * @param targetPlayerName name of target player (null for broadcast)
 * @param message the chat message content
 * @param senderName display name of the sender
 * @param senderUuid UUID of the sender (null for system messages)
 */
public record ChatMessage(
        @Nonnull String sourceProxyId,
        @Nonnull Instant timestamp,
        @Nullable UUID targetPlayerUuid,
        @Nullable String targetPlayerName,
        @Nonnull String message,
        @Nonnull String senderName,
        @Nullable UUID senderUuid
) implements ChannelMessage {

    @Override
    @Nonnull
    public String messageType() {
        return "chat";
    }

    /**
     * Check if this is a broadcast message (no specific target).
     *
     * @return true if this message should be delivered to all players
     */
    public boolean isBroadcast() {
        return targetPlayerUuid == null && targetPlayerName == null;
    }
}

