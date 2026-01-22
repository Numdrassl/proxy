package me.internalizable.numdrassl.api.messaging.channel;

import me.internalizable.numdrassl.api.messaging.message.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Predefined system channels for cross-proxy messaging.
 *
 * <p>These channels are built into Numdrassl and are used for core functionality.
 * Plugins can subscribe to these channels to receive system-level messages.</p>
 *
 * <h2>Usage with @MessageSubscribe</h2>
 * <pre>{@code
 * @MessageSubscribe(SystemChannel.HEARTBEAT)
 * public void onHeartbeat(HeartbeatMessage msg) {
 *     logger.info("Proxy {} is alive", msg.sourceProxyId());
 * }
 *
 * @MessageSubscribe(SystemChannel.CHAT)
 * public void onChat(ChatMessage msg) {
 *     if (msg.isBroadcast()) {
 *         broadcastToAll(msg.message());
 *     }
 * }
 * }</pre>
 *
 * @see me.internalizable.numdrassl.api.messaging.annotation.MessageSubscribe
 * @see Channels
 */
public enum SystemChannel {

    /**
     * No system channel - use this when subscribing to plugin messages.
     */
    NONE(""),

    /**
     * Proxy heartbeat and registration.
     * <p>Message type: {@link HeartbeatMessage}</p>
     */
    HEARTBEAT("numdrassl:heartbeat"),

    /**
     * Player count updates.
     * <p>Message type: {@link PlayerCountMessage}</p>
     */
    PLAYER_COUNT("numdrassl:player-count"),

    /**
     * Cross-proxy chat messages.
     * <p>Message type: {@link ChatMessage}</p>
     */
    CHAT("numdrassl:chat"),

    /**
     * Player transfer coordination.
     * <p>Message type: {@link TransferMessage}</p>
     */
    TRANSFER("numdrassl:transfer"),

    /**
     * Plugin-defined custom messages.
     * <p>Message type: {@link PluginMessage}</p>
     */
    PLUGIN("numdrassl:plugin"),

    /**
     * Broadcast messages to all proxies.
     * <p>Message type: {@link BroadcastMessage}</p>
     */
    BROADCAST("numdrassl:broadcast");

    private final String channelId;

    SystemChannel(String channelId) {
        this.channelId = channelId;
    }

    /**
     * Get the channel ID.
     *
     * @return the full channel ID (e.g., "numdrassl:heartbeat")
     */
    @Nonnull
    public String getChannelId() {
        return channelId;
    }

    /**
     * Get the corresponding MessageChannel instance.
     *
     * <p>For {@link #NONE}, this method returns {@code null}.</p>
     *
     * <p>For all other system channels, this method is guaranteed to return
     * a non-null {@link MessageChannel} because system channels are pre-registered
     * in {@link Channels} during class initialization.</p>
     *
     * @return the MessageChannel, or null if this is {@link #NONE}
     */
    @Nullable
    public MessageChannel toMessageChannel() {
        if (this == NONE) {
            return null;
        }
        // System channels are always pre-registered, use getOrRegister for safety
        return Channels.getOrRegister(channelId);
    }
}

