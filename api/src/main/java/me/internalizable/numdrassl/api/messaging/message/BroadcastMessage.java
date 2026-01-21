package me.internalizable.numdrassl.api.messaging.message;

import me.internalizable.numdrassl.api.messaging.ChannelMessage;

import javax.annotation.Nonnull;
import java.time.Instant;

/**
 * Cluster-wide broadcast message for announcements to all proxies.
 *
 * <p>Used for messages that need to reach all proxy instances,
 * such as:</p>
 * <ul>
 *   <li>Server-wide announcements</li>
 *   <li>Maintenance notifications</li>
 *   <li>Emergency alerts</li>
 *   <li>Configuration updates</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Send a broadcast
 * BroadcastMessage broadcast = new BroadcastMessage(
 *     proxyId, Instant.now(),
 *     "announcement",
 *     "Server restarting in 5 minutes!"
 * );
 * messaging.publish(Channels.BROADCAST, broadcast);
 *
 * // Subscribe to broadcasts
 * @Subscribe(SystemChannel.BROADCAST)
 * public void onBroadcast(BroadcastMessage msg) {
 *     if ("announcement".equals(msg.broadcastType())) {
 *         broadcastToAllPlayers(msg.content());
 *     }
 * }
 * }</pre>
 *
 * @param sourceProxyId the proxy that initiated the broadcast
 * @param timestamp when the broadcast was created
 * @param broadcastType categorization of the broadcast (e.g., "announcement", "alert")
 * @param content the broadcast message content
 */
public record BroadcastMessage(
        @Nonnull String sourceProxyId,
        @Nonnull Instant timestamp,
        @Nonnull String broadcastType,
        @Nonnull String content
) implements ChannelMessage {

    @Override
    @Nonnull
    public String messageType() {
        return "broadcast";
    }
}

