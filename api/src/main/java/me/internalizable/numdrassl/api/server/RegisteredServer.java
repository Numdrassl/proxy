package me.internalizable.numdrassl.api.server;

import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.plugin.messaging.ChannelIdentifier;

import javax.annotation.Nonnull;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a registered backend server that players can connect to.
 */
public interface RegisteredServer {

    /**
     * Get the name of this server.
     *
     * @return the server name
     */
    @Nonnull
    String getName();

    /**
     * Get the address of this server.
     *
     * @return the server address
     */
    @Nonnull
    InetSocketAddress getAddress();

    /**
     * Check if this is the default server.
     * New players will be connected to the default server.
     *
     * @return true if this is the default server
     */
    boolean isDefault();

    /**
     * Get all players currently connected to this server.
     *
     * @return an unmodifiable collection of connected players
     */
    @Nonnull
    Collection<Player> getPlayers();

    /**
     * Get the number of players connected to this server.
     *
     * @return the player count
     */
    int getPlayerCount();

    /**
     * Ping the server to check if it's online.
     *
     * @return a future that completes with the ping result
     */
    @Nonnull
    CompletableFuture<PingResult> ping();

    /**
     * Sends a plugin message to this server.
     *
     * <p>Plugin messages allow the proxy to communicate with backend servers
     * through registered channels. The server must have a corresponding plugin
     * that listens for messages on this channel.</p>
     *
     * @param channel the channel to send on
     * @param data the message data
     * @return true if the message was sent (at least one player connected), false otherwise
     */
    boolean sendPluginMessage(@Nonnull ChannelIdentifier channel, @Nonnull byte[] data);
}

