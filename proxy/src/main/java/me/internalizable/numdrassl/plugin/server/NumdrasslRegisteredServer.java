package me.internalizable.numdrassl.plugin.server;

import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.plugin.messaging.ChannelIdentifier;
import me.internalizable.numdrassl.api.server.PingResult;
import me.internalizable.numdrassl.api.server.RegisteredServer;
import me.internalizable.numdrassl.plugin.messaging.BackendControlManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the {@link RegisteredServer} API interface.
 *
 * <p>Represents a backend server that players can connect to through the proxy.</p>
 */
public final class NumdrasslRegisteredServer implements RegisteredServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(NumdrasslRegisteredServer.class);

    private final String name;
    private final InetSocketAddress address;
    private final Set<Player> connectedPlayers = ConcurrentHashMap.newKeySet();
    private volatile boolean defaultServer;

    /**
     * Reference to the control manager for plugin messaging.
     * Set via {@link #setControlManager(BackendControlManager)}.
     */
    @Nullable
    private BackendControlManager controlManager;

    public NumdrasslRegisteredServer(@Nonnull String name, @Nonnull InetSocketAddress address) {
        this.name = Objects.requireNonNull(name, "name");
        this.address = Objects.requireNonNull(address, "address");
    }

    /**
     * Sets the control manager for plugin messaging.
     *
     * @param controlManager the control manager
     */
    public void setControlManager(@Nullable BackendControlManager controlManager) {
        this.controlManager = controlManager;
    }

    // ==================== Server Identity ====================

    @Override
    @Nonnull
    public String getName() {
        return name;
    }

    @Override
    @Nonnull
    public InetSocketAddress getAddress() {
        return address;
    }

    @Override
    public boolean isDefault() {
        return defaultServer;
    }

    public void setDefault(boolean defaultServer) {
        this.defaultServer = defaultServer;
    }

    // ==================== Player Tracking ====================

    @Override
    @Nonnull
    public Collection<Player> getPlayers() {
        return Collections.unmodifiableSet(connectedPlayers);
    }

    @Override
    public int getPlayerCount() {
        return connectedPlayers.size();
    }

    /**
     * Adds a player to this server's player list.
     *
     * @param player the player to add
     */
    public void addPlayer(@Nonnull Player player) {
        Objects.requireNonNull(player, "player");
        connectedPlayers.add(player);
    }

    /**
     * Removes a player from this server's player list.
     *
     * @param player the player to remove
     */
    public void removePlayer(@Nonnull Player player) {
        Objects.requireNonNull(player, "player");
        connectedPlayers.remove(player);
    }

    /**
     * Clears all players from this server.
     */
    public void clearPlayers() {
        connectedPlayers.clear();
    }

    // ==================== Server Status ====================

    @Override
    @Nonnull
    public CompletableFuture<PingResult> ping() {
        // TODO: Implement actual server ping via QUIC
        return CompletableFuture.completedFuture(PingResult.success(-1));
    }

    // ==================== Plugin Messaging ====================

    /**
     * {@inheritDoc}
     *
     * <p>Sends a plugin message to this backend server via the control connection.
     * The message will be received by the Bridge plugin on the backend.</p>
     *
     * <p>This works without requiring a player connection - the proxy maintains
     * a dedicated control channel to each backend for plugin messaging.</p>
     */
    @Override
    public boolean sendPluginMessage(@Nonnull ChannelIdentifier channel, @Nonnull byte[] data) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(data, "data");

        if (controlManager == null) {
            LOGGER.warn("Cannot send plugin message to {}: control manager not initialized", name);
            return false;
        }

        boolean sent = controlManager.sendPluginMessage(name, channel, data);
        if (sent) {
            LOGGER.debug("Sent plugin message on channel {} to {} ({} bytes)",
                    channel.getId(), name, data.length);
        } else {
            LOGGER.debug("Failed to send plugin message to {}: no active control connection", name);
        }

        return sent;
    }

    // ==================== Object Methods ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NumdrasslRegisteredServer that)) return false;
        return name.equalsIgnoreCase(that.name);
    }

    @Override
    public int hashCode() {
        return name.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return String.format("RegisteredServer{name=%s, address=%s, players=%d}",
            name, address, connectedPlayers.size());
    }
}

