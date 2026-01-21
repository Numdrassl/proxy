package me.internalizable.numdrassl.api.cluster;

import javax.annotation.Nonnull;
import java.net.InetSocketAddress;
import java.time.Instant;

/**
 * Information about a proxy instance in the cluster.
 *
 * <p>This record is immutable and represents a snapshot of a proxy's state
 * at a specific point in time.</p>
 *
 * @param proxyId unique identifier for this proxy instance
 * @param region the geographic region (e.g., "eu-west", "us-east")
 * @param address the public address players connect to
 * @param playerCount current number of connected players
 * @param maxPlayers maximum player capacity
 * @param uptimeMillis how long the proxy has been running
 * @param lastHeartbeat when the last heartbeat was received
 * @param version the proxy version string
 */
public record ProxyInfo(
        @Nonnull String proxyId,
        @Nonnull String region,
        @Nonnull InetSocketAddress address,
        int playerCount,
        int maxPlayers,
        long uptimeMillis,
        @Nonnull Instant lastHeartbeat,
        @Nonnull String version
) {

    /**
     * Check if this proxy has capacity for more players.
     *
     * @return true if playerCount is less than maxPlayers
     */
    public boolean hasCapacity() {
        return playerCount < maxPlayers;
    }

    /**
     * Get the load factor (0.0 to 1.0) of this proxy.
     *
     * @return the ratio of current players to max capacity
     */
    public double loadFactor() {
        return maxPlayers > 0 ? (double) playerCount / maxPlayers : 1.0;
    }

    /**
     * Check if this proxy's heartbeat is stale.
     *
     * @param timeoutMillis the timeout threshold in milliseconds
     * @return true if the last heartbeat is older than the timeout
     */
    public boolean isStale(long timeoutMillis) {
        return Instant.now().toEpochMilli() - lastHeartbeat.toEpochMilli() > timeoutMillis;
    }
}

