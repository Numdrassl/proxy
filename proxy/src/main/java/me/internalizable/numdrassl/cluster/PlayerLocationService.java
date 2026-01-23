package me.internalizable.numdrassl.cluster;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tracks player locations across the cluster using Redis.
 *
 * <p>Maintains a mapping of player UUID -> proxy ID in Redis, allowing
 * any proxy instance to determine where a player is connected.</p>
 *
 * <h2>Redis Keys</h2>
 * <ul>
 *   <li>{@code numdrassl:player:<uuid>} - Contains the proxy ID where the player is connected</li>
 * </ul>
 *
 * <h2>TTL Strategy</h2>
 * <p>Keys are set with a TTL slightly longer than the heartbeat interval.
 * This ensures stale entries are automatically cleaned up if a proxy crashes
 * without sending disconnect notifications.</p>
 */
public final class PlayerLocationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerLocationService.class);

    /**
     * Redis key prefix for player location entries.
     */
    private static final String KEY_PREFIX = "numdrassl:player:";

    /**
     * TTL for player location entries (30 seconds).
     * Should be longer than heartbeat interval to survive brief disconnects.
     */
    private static final long TTL_SECONDS = 30;

    private final String localProxyId;
    private final RedisAsyncCommands<String, String> commands;

    /**
     * Creates a new player location service.
     *
     * @param localProxyId the local proxy's identifier
     * @param connection the Redis connection to use
     */
    public PlayerLocationService(
            @Nonnull String localProxyId,
            @Nonnull StatefulRedisConnection<String, String> connection) {
        this.localProxyId = Objects.requireNonNull(localProxyId, "localProxyId");
        this.commands = Objects.requireNonNull(connection, "connection").async();
    }

    /**
     * Registers a player as connected to this proxy.
     *
     * <p>Sets the player's location in Redis with a TTL. The TTL is refreshed
     * periodically to keep the entry alive while the player is connected.</p>
     *
     * @param playerUuid the player's UUID
     * @return a future that completes when the registration is done
     */
    @Nonnull
    public CompletableFuture<Void> registerPlayer(@Nonnull UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");

        String key = KEY_PREFIX + playerUuid.toString();

        return commands.setex(key, TTL_SECONDS, localProxyId)
                .thenAccept(result -> LOGGER.debug("Registered player {} on proxy {}", playerUuid, localProxyId))
                .exceptionally(e -> {
                    LOGGER.warn("Failed to register player {} in Redis: {}", playerUuid, e.getMessage());
                    return null;
                })
                .toCompletableFuture();
    }

    /**
     * Removes a player's location entry.
     *
     * <p>Called when a player disconnects from this proxy.</p>
     *
     * @param playerUuid the player's UUID
     * @return a future that completes when the removal is done
     */
    @Nonnull
    public CompletableFuture<Void> unregisterPlayer(@Nonnull UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");

        String key = KEY_PREFIX + playerUuid.toString();

        return commands.del(key)
                .thenAccept(count -> LOGGER.debug("Unregistered player {} (deleted: {})", playerUuid, count))
                .exceptionally(e -> {
                    LOGGER.warn("Failed to unregister player {} from Redis: {}", playerUuid, e.getMessage());
                    return null;
                })
                .toCompletableFuture();
    }

    /**
     * Refreshes the TTL for a player's location entry.
     *
     * <p>Should be called periodically (e.g., every heartbeat) to keep
     * the player's entry alive.</p>
     *
     * @param playerUuid the player's UUID
     * @return a future that completes when the refresh is done
     */
    @Nonnull
    public CompletableFuture<Void> refreshPlayer(@Nonnull UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");

        String key = KEY_PREFIX + playerUuid.toString();

        return commands.expire(key, TTL_SECONDS)
                .thenAccept(success -> {
                    if (!success) {
                        // Key doesn't exist, re-register
                        registerPlayer(playerUuid);
                    }
                })
                .exceptionally(e -> {
                    LOGGER.warn("Failed to refresh player {} TTL: {}", playerUuid, e.getMessage());
                    return null;
                })
                .toCompletableFuture();
    }

    /**
     * Finds which proxy a player is connected to.
     *
     * @param playerUuid the player's UUID
     * @return the proxy ID where the player is connected, or empty if not found
     */
    @Nonnull
    public CompletableFuture<Optional<String>> findPlayerProxy(@Nonnull UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");

        String key = KEY_PREFIX + playerUuid.toString();

        return commands.get(key)
                .thenApply(proxyId -> Optional.ofNullable(proxyId))
                .exceptionally(e -> {
                    LOGGER.warn("Failed to lookup player {} in Redis: {}", playerUuid, e.getMessage());
                    return Optional.empty();
                })
                .toCompletableFuture();
    }

    /**
     * Synchronously finds which proxy a player is connected to.
     *
     * <p>Blocks until the Redis query completes. Use {@link #findPlayerProxy(UUID)}
     * for non-blocking operations.</p>
     *
     * @param playerUuid the player's UUID
     * @param timeoutMs maximum time to wait in milliseconds
     * @return the proxy ID where the player is connected, or empty if not found or timeout
     */
    @Nonnull
    public Optional<String> findPlayerProxySync(@Nonnull UUID playerUuid, long timeoutMs) {
        try {
            return findPlayerProxy(playerUuid).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOGGER.warn("Timeout looking up player {} in Redis", playerUuid);
            return Optional.empty();
        }
    }

    /**
     * Checks if a player is online anywhere in the cluster.
     *
     * @param playerUuid the player's UUID
     * @return a future that completes with true if the player is online
     */
    @Nonnull
    public CompletableFuture<Boolean> isPlayerOnline(@Nonnull UUID playerUuid) {
        return findPlayerProxy(playerUuid).thenApply(Optional::isPresent);
    }
}

