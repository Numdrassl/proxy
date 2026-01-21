package me.internalizable.numdrassl.api.messaging;

import me.internalizable.numdrassl.api.messaging.message.*;

import javax.annotation.Nonnull;

/**
 * Predefined system channels for cross-proxy messaging.
 *
 * <p>These channels are built into Numdrassl and are used for core functionality.
 * Plugins can subscribe to these channels to receive system-level messages.</p>
 *
 * <h2>Usage with @Subscribe</h2>
 * <pre>{@code
 * @Subscribe(SystemChannel.HEARTBEAT)
 * public void onHeartbeat(HeartbeatMessage msg) {
 *     logger.info("Proxy {} is alive", msg.sourceProxyId());
 * }
 *
 * @Subscribe(SystemChannel.CHAT)
 * public void onChat(ChatMessage msg) {
 *     if (msg.isBroadcast()) {
 *         broadcastToAll(msg.message());
 *     }
 * }
 * }</pre>
 *
 * @see Subscribe
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
     * @return the MessageChannel, or null for NONE
     */
    public MessageChannel toMessageChannel() {
        if (this == NONE) {
            return null;
        }
        return Channels.get(channelId);
    }
}

