package me.internalizable.numdrassl.api.messaging;

import javax.annotation.Nonnull;
import java.time.Instant;

/**
 * Base interface for all cross-proxy messages.
 *
 * <p>Messages are serialized to JSON for transport over Redis pub/sub.
 * Each message includes metadata about the source proxy and timestamp.</p>
 *
 * <h2>Message Types</h2>
 * <p>All message types are in the {@link me.internalizable.numdrassl.api.messaging.message} package:</p>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.api.messaging.message.HeartbeatMessage} - Proxy health monitoring</li>
 *   <li>{@link me.internalizable.numdrassl.api.messaging.message.PlayerCountMessage} - Player count synchronization</li>
 *   <li>{@link me.internalizable.numdrassl.api.messaging.message.ChatMessage} - Cross-proxy chat</li>
 *   <li>{@link me.internalizable.numdrassl.api.messaging.message.TransferMessage} - Player transfer coordination</li>
 *   <li>{@link me.internalizable.numdrassl.api.messaging.message.PluginMessage} - Custom plugin data</li>
 *   <li>{@link me.internalizable.numdrassl.api.messaging.message.BroadcastMessage} - Cluster-wide broadcasts</li>
 * </ul>
 *
 * <h2>Immutability</h2>
 * <p>All implementations are immutable records for thread safety.</p>
 *
 * @see me.internalizable.numdrassl.api.messaging.message
 */
public interface ChannelMessage {

    /**
     * Get the ID of the proxy that sent this message.
     *
     * @return the source proxy ID
     */
    @Nonnull
    String sourceProxyId();

    /**
     * Get the timestamp when this message was created.
     *
     * @return the message timestamp
     */
    @Nonnull
    Instant timestamp();

    /**
     * Get the message type identifier for serialization.
     *
     * @return the message type (e.g., "heartbeat", "chat", "plugin")
     */
    @Nonnull
    String messageType();
}

