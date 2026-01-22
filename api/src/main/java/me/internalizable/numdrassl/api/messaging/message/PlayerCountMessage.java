package me.internalizable.numdrassl.api.messaging.message;

import me.internalizable.numdrassl.api.messaging.ChannelMessage;

import javax.annotation.Nonnull;
import java.time.Instant;

/**
 * Player count update message for synchronizing counts across proxies.
 *
 * <p>Used to track the total number of players across the proxy cluster.
 * This enables features like:</p>
 * <ul>
 *   <li>Displaying accurate global player counts</li>
 *   <li>Load balancing decisions</li>
 *   <li>Capacity planning</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Subscribe(SystemChannel.PLAYER_COUNT)
 * public void onPlayerCount(PlayerCountMessage msg) {
 *     logger.info("Proxy {} has {}/{} players",
 *         msg.sourceProxyId(), msg.playerCount(), msg.maxPlayers());
 * }
 * }</pre>
 *
 * @param sourceProxyId the proxy reporting its player count
 * @param timestamp when the count was recorded
 * @param playerCount current number of connected players
 * @param maxPlayers maximum player capacity of the proxy
 */
public record PlayerCountMessage(
        @Nonnull String sourceProxyId,
        @Nonnull Instant timestamp,
        int playerCount,
        int maxPlayers
) implements ChannelMessage {

    /**
     * Validates that sourceProxyId and timestamp are not null,
     * and that player counts are non-negative and within capacity.
     */
    public PlayerCountMessage {
        if (sourceProxyId == null) {
            throw new NullPointerException("sourceProxyId must not be null");
        }
        if (timestamp == null) {
            throw new NullPointerException("timestamp must not be null");
        }
        if (playerCount < 0 || maxPlayers < 0) {
            throw new IllegalArgumentException("playerCount and maxPlayers must be non-negative");
        }
        if (playerCount > maxPlayers) {
            throw new IllegalArgumentException("playerCount cannot exceed maxPlayers");
        }
    }

    @Override
    @Nonnull
    public String messageType() {
        return "player_count";
    }
}

