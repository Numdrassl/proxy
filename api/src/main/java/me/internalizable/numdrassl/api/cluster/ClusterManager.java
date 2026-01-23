package me.internalizable.numdrassl.api.cluster;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manages the cluster of proxy instances.
 *
 * <p>The cluster manager tracks all online proxy instances, aggregates
 * global state (player counts, server lists), and provides methods for
 * cross-proxy coordination.</p>
 *
 * <h2>Cluster State</h2>
 * <p>Each proxy maintains a local view of the cluster state, updated via
 * heartbeat messages. The state is eventually consistent - there may be
 * brief periods where proxies have slightly different views.</p>
 *
 * <h2>Local vs. Global</h2>
 * <ul>
 *   <li><b>Local</b> methods return data from this proxy instance only</li>
 *   <li><b>Global</b> methods aggregate data from all proxies in the cluster</li>
 * </ul>
 */
public interface ClusterManager {

    /**
     * Check if clustering is enabled and connected.
     *
     * @return true if this proxy is part of an active cluster
     */
    boolean isClusterMode();

    /**
     * Get this proxy's unique identifier.
     *
     * @return the local proxy ID
     */
    @Nonnull
    String getLocalProxyId();

    /**
     * Get this proxy's region.
     *
     * @return the configured region (e.g., "eu-west")
     */
    @Nonnull
    String getLocalRegion();

    /**
     * Get information about this proxy instance.
     *
     * @return the local proxy info
     */
    @Nonnull
    ProxyInfo getLocalProxyInfo();

    /**
     * Get all online proxies in the cluster, including this one.
     *
     * @return an unmodifiable collection of proxy info
     */
    @Nonnull
    Collection<ProxyInfo> getOnlineProxies();

    /**
     * Get a specific proxy by its ID.
     *
     * @param proxyId the proxy ID to look up
     * @return the proxy info, or empty if not found/offline
     */
    @Nonnull
    Optional<ProxyInfo> getProxy(@Nonnull String proxyId);

    /**
     * Get all proxies in a specific region.
     *
     * @param region the region to filter by
     * @return proxies in that region
     */
    @Nonnull
    Collection<ProxyInfo> getProxiesInRegion(@Nonnull String region);

    /**
     * Get the total number of players across all proxies.
     *
     * @return the global player count
     */
    int getGlobalPlayerCount();

    /**
     * Get the number of online proxy instances.
     *
     * @return the count of online proxies
     */
    int getProxyCount();

    /**
     * Find which proxy a player is connected to.
     *
     * <p>For async operations, prefer {@link #findPlayerProxyAsync(UUID)}.</p>
     *
     * @param playerUuid the player's UUID
     * @return the proxy ID, or empty if player is not online
     */
    @Nonnull
    Optional<String> findPlayerProxy(@Nonnull UUID playerUuid);

    /**
     * Find which proxy a player is connected to (async).
     *
     * <p>This is the preferred method for cross-cluster lookups as it
     * doesn't block while waiting for Redis responses.</p>
     *
     * @param playerUuid the player's UUID
     * @return a future completing with the proxy ID, or empty if not online
     */
    @Nonnull
    CompletableFuture<Optional<String>> findPlayerProxyAsync(@Nonnull UUID playerUuid);

    /**
     * Check if a player is online anywhere in the cluster.
     *
     * <p>For async operations, prefer {@link #isPlayerOnlineAsync(UUID)}.</p>
     *
     * @param playerUuid the player's UUID
     * @return true if the player is connected to any proxy
     */
    boolean isPlayerOnline(@Nonnull UUID playerUuid);

    /**
     * Check if a player is online anywhere in the cluster (async).
     *
     * <p>This is the preferred method for cross-cluster lookups as it
     * doesn't block while waiting for Redis responses.</p>
     *
     * @param playerUuid the player's UUID
     * @return a future completing with true if the player is online
     */
    @Nonnull
    CompletableFuture<Boolean> isPlayerOnlineAsync(@Nonnull UUID playerUuid);

    /**
     * Get the proxy with the lowest load in a specific region.
     *
     * <p>Useful for load balancing new connections within a region.
     * Use {@link #getLeastLoadedProxy()} for any region.</p>
     *
     * @param region the region to search (must not be null)
     * @return the least loaded proxy in that region, or empty if none available
     */
    @Nonnull
    Optional<ProxyInfo> getLeastLoadedProxy(@Nonnull String region);

    /**
     * Get the proxy with the lowest load across all regions.
     *
     * @return the least loaded proxy, or empty if none available
     */
    @Nonnull
    Optional<ProxyInfo> getLeastLoadedProxy();
}

