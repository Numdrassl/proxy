package me.internalizable.numdrassl.api.messaging.message;

import me.internalizable.numdrassl.api.messaging.ChannelMessage;

import javax.annotation.Nonnull;
import java.time.Instant;

/**
 * Heartbeat message for proxy health monitoring and discovery.
 *
 * <p>Proxies periodically publish heartbeats to announce their presence
 * and current state. Other proxies use this information to:</p>
 * <ul>
 *   <li>Track which proxies are online</li>
 *   <li>Aggregate player counts across the cluster</li>
 *   <li>Detect proxy failures (missed heartbeats)</li>
 *   <li>Route players to available proxies</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Subscribe(SystemChannel.HEARTBEAT)
 * public void onHeartbeat(HeartbeatMessage heartbeat) {
 *     logger.info("Proxy {} in {} has {} players (uptime: {}ms)",
 *         heartbeat.sourceProxyId(),
 *         heartbeat.region(),
 *         heartbeat.playerCount(),
 *         heartbeat.uptimeMillis());
 * }
 * }</pre>
 *
 * @param sourceProxyId the unique identifier of the proxy sending the heartbeat
 * @param timestamp when the heartbeat was generated
 * @param region the geographic region of the proxy (e.g., "us-east", "eu-west")
 * @param host the hostname or IP address of the proxy
 * @param port the port the proxy is listening on
 * @param playerCount current number of connected players
 * @param uptimeMillis how long the proxy has been running in milliseconds
 * @param shuttingDown true if the proxy is in the process of shutting down
 */
public record HeartbeatMessage(
        @Nonnull String sourceProxyId,
        @Nonnull Instant timestamp,
        @Nonnull String region,
        @Nonnull String host,
        int port,
        int playerCount,
        long uptimeMillis,
        boolean shuttingDown
) implements ChannelMessage {

    @Override
    @Nonnull
    public String messageType() {
        return "heartbeat";
    }
}

