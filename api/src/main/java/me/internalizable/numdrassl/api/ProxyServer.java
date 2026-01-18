package me.internalizable.numdrassl.api;

import me.internalizable.numdrassl.api.command.CommandManager;
import me.internalizable.numdrassl.api.event.EventManager;
import me.internalizable.numdrassl.api.permission.PermissionManager;
import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.plugin.PluginManager;
import me.internalizable.numdrassl.api.scheduler.Scheduler;
import me.internalizable.numdrassl.api.server.RegisteredServer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Main entry point for the Numdrassl API.
 *
 * <p>Numdrassl is a QUIC reverse proxy for Hytale that sits between players and backend servers.
 * Players connect to the proxy, which terminates TLS using its own certificates and forwards
 * traffic to backend servers. The proxy intercepts all packets flowing in both directions,
 * inspecting, modifying, or relaying them as needed while keeping the connection transparent
 * to both endpoints.</p>
 *
 * <p>Authentication happens normally: the proxy validates player tokens and relays the authenticated
 * session to backends, which see standard Hytale protocol traffic with no modifications needed.
 * No certificate changes, no custom authentication handling, and no protocol adjustments required
 * on the server side.</p>
 *
 * <p>Numdrassl authenticates with Hytale backend servers by replicating the exact authentication
 * flow used by the official Hytale client/server. The proxy extracts the player's authentication
 * token during the initial handshake, then establishes its own connection to the backend server,
 * presenting the same credentials as if it were a legitimate Hytale client. Since the proxy
 * implements the Hytale protocol natively, mimicking the behavior of the official JAR, backend
 * servers cannot distinguish between a direct player connection and one proxied through Numdrassl.</p>
 *
 * <p>For server transfers, the proxy sends a ClientReferral packet directing the client to
 * reconnect to the proxy's public address (not the backend directly), then routes them to the
 * new backend server.</p>
 */
public interface ProxyServer {

    /**
     * Get the event manager for registering event listeners.
     *
     * @return the event manager
     */
    @Nonnull
    EventManager getEventManager();

    /**
     * Get the command manager for registering commands.
     *
     * @return the command manager
     */
    @Nonnull
    CommandManager getCommandManager();

    /**
     * Get the plugin manager for managing plugins.
     *
     * @return the plugin manager
     */
    @Nonnull
    PluginManager getPluginManager();

    /**
     * Get the scheduler for scheduling tasks.
     *
     * @return the scheduler
     */
    @Nonnull
    Scheduler getScheduler();

    /**
     * Get the permission manager for managing permissions.
     *
     * <p>The permission manager allows external permission plugins (like LuckPerms)
     * to register their permission providers.</p>
     *
     * @return the permission manager
     */
    @Nonnull
    PermissionManager getPermissionManager();

    /**
     * Get all currently connected players.
     *
     * @return an unmodifiable collection of connected players
     */
    @Nonnull
    Collection<Player> getAllPlayers();

    /**
     * Get a player by their UUID.
     *
     * @param uuid the player's UUID
     * @return the player, or empty if not connected
     */
    @Nonnull
    Optional<Player> getPlayer(@Nonnull UUID uuid);

    /**
     * Get a player by their username.
     *
     * @param username the player's username (case-insensitive)
     * @return the player, or empty if not connected
     */
    @Nonnull
    Optional<Player> getPlayer(@Nonnull String username);

    /**
     * Get the number of currently connected players.
     *
     * @return the player count
     */
    int getPlayerCount();

    /**
     * Get all registered backend servers.
     *
     * @return an unmodifiable collection of registered servers
     */
    @Nonnull
    Collection<RegisteredServer> getAllServers();

    /**
     * Get a registered server by its name.
     *
     * @param name the server name (case-insensitive)
     * @return the server, or empty if not registered
     */
    @Nonnull
    Optional<RegisteredServer> getServer(@Nonnull String name);

    /**
     * Register a new backend server.
     *
     * @param name the server name
     * @param address the server address
     * @return the newly registered server
     */
    @Nonnull
    RegisteredServer registerServer(@Nonnull String name, @Nonnull InetSocketAddress address);

    /**
     * Unregister a backend server.
     *
     * @param name the server name
     * @return true if the server was unregistered
     */
    boolean unregisterServer(@Nonnull String name);

    /**
     * Get the address the proxy is bound to.
     *
     * @return the bound address
     */
    @Nonnull
    InetSocketAddress getBoundAddress();

    /**
     * Get the public address players should use to connect.
     *
     * @return the public address, or the bound address if not configured
     */
    @Nonnull
    InetSocketAddress getPublicAddress();

    /**
     * Get the proxy's data directory.
     *
     * @return the path to the data directory
     */
    @Nonnull
    Path getDataDirectory();

    /**
     * Get the proxy's configuration directory.
     *
     * @return the path to the config directory
     */
    @Nonnull
    Path getConfigDirectory();

    /**
     * Check if the proxy is currently running.
     *
     * @return true if the proxy is running
     */
    boolean isRunning();

    /**
     * Shutdown the proxy gracefully.
     */
    void shutdown();

    /**
     * Get the proxy version.
     *
     * @return the version string
     */
    @Nonnull
    String getVersion();
}

