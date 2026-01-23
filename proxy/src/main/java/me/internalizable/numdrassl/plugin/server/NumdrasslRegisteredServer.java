package me.internalizable.numdrassl.plugin.server;

import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.plugin.messaging.ChannelIdentifier;
import me.internalizable.numdrassl.api.server.PingResult;
import me.internalizable.numdrassl.api.server.RegisteredServer;
import me.internalizable.numdrassl.plugin.player.NumdrasslPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
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

    public NumdrasslRegisteredServer(@Nonnull String name, @Nonnull InetSocketAddress address) {
        this.name = Objects.requireNonNull(name, "name");
        this.address = Objects.requireNonNull(address, "address");
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
     * <p>Sends a plugin message to this backend server.</p>
     */
    @Override
    public boolean sendPluginMessage(@Nonnull ChannelIdentifier channel, @Nonnull byte[] data) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(data, "data");

        // TODO: Implement plugin messaging via Redis pub/sub
        LOGGER.debug("sendPluginMessage not implemented for {}: use Redis for cross-proxy messaging", name);
        return false;
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

