package me.internalizable.numdrassl.api;

import com.hypixel.hytale.protocol.Packet;
import me.internalizable.numdrassl.config.BackendServer;
import me.internalizable.numdrassl.config.ProxyConfig;
import me.internalizable.numdrassl.event.packet.PacketListener;
import me.internalizable.numdrassl.server.ProxyCore;
import me.internalizable.numdrassl.session.ProxySession;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.UUID;

/**
 * Public API for interacting with the Numdrassl proxy.
 *
 * <p>This class provides static utility methods for common operations.
 * For most plugin development, prefer using the {@link ProxyServer} interface
 * obtained via {@link Numdrassl#server()}.</p>
 *
 * <p>Use this class to:</p>
 * <ul>
 *   <li>Register packet listeners/interceptors</li>
 *   <li>Access connected players</li>
 *   <li>Transfer players between servers</li>
 *   <li>Send packets to players or servers</li>
 * </ul>
 */
public final class ProxyAPI {

    private static volatile ProxyCore core;

    private ProxyAPI() {}

    /**
     * Initialize the API with the proxy core instance.
     * Called internally by the proxy during startup.
     *
     * @param proxyCore the proxy core instance
     */
    public static void init(@Nonnull ProxyCore proxyCore) {
        core = java.util.Objects.requireNonNull(proxyCore, "proxyCore");
    }

    private static ProxyCore getCore() {
        if (core == null) {
            throw new IllegalStateException("ProxyAPI not initialized. Is the proxy running?");
        }
        return core;
    }

    // =========================================
    // Event System
    // =========================================

    /**
     * Register a packet listener to intercept and modify packets.
     *
     * @param listener the packet listener to register
     */
    public static void registerListener(@Nonnull PacketListener listener) {
        java.util.Objects.requireNonNull(listener, "listener");
        getCore().getEventManager().registerListener(listener);
    }

    /**
     * Unregister a previously registered packet listener.
     *
     * @param listener the packet listener to unregister
     */
    public static void unregisterListener(@Nonnull PacketListener listener) {
        java.util.Objects.requireNonNull(listener, "listener");
        getCore().getEventManager().unregisterListener(listener);
    }

    // =========================================
    // Session/Player Management
    // =========================================

    /**
     * Get all connected player sessions.
     *
     * @return collection of active sessions
     */
    @Nonnull
    public static Collection<ProxySession> getAllSessions() {
        return getCore().getSessionManager().getAllSessions();
    }

    /**
     * Get a player session by UUID.
     *
     * @param playerUuid the player's UUID
     * @return the session, or null if not found
     */
    @Nullable
    public static ProxySession getSession(@Nonnull UUID playerUuid) {
        java.util.Objects.requireNonNull(playerUuid, "playerUuid");
        return getCore().getSessionManager().getSession(playerUuid);
    }

    /**
     * Get the number of connected players.
     *
     * @return the player count
     */
    public static int getPlayerCount() {
        return getCore().getSessionManager().getSessionCount();
    }

    /**
     * Disconnect a player with a reason message.
     *
     * @param session the player's session
     * @param reason the disconnect reason
     */
    public static void disconnect(@Nonnull ProxySession session, @Nonnull String reason) {
        java.util.Objects.requireNonNull(session, "session");
        java.util.Objects.requireNonNull(reason, "reason");
        session.disconnect(reason);
    }

    // =========================================
    // Packet Sending
    // =========================================

    /**
     * Send a packet to a player's client.
     *
     * @param session the player's session
     * @param packet the packet to send
     */
    public static void sendToClient(@Nonnull ProxySession session, @Nonnull Packet packet) {
        java.util.Objects.requireNonNull(session, "session");
        java.util.Objects.requireNonNull(packet, "packet");
        session.sendToClient(packet);
    }

    /**
     * Send a packet to the backend server on behalf of a player.
     *
     * @param session the player's session
     * @param packet the packet to send
     */
    public static void sendToBackend(@Nonnull ProxySession session, @Nonnull Packet packet) {
        java.util.Objects.requireNonNull(session, "session");
        java.util.Objects.requireNonNull(packet, "packet");
        session.sendToBackend(packet);
    }

    /**
     * Broadcast a packet to all connected players.
     *
     * @param packet the packet to broadcast
     */
    public static void broadcast(@Nonnull Packet packet) {
        java.util.Objects.requireNonNull(packet, "packet");
        for (ProxySession session : getAllSessions()) {
            session.sendToClient(packet);
        }
    }

    // =========================================
    // Server Transfer
    // =========================================

    /**
     * Transfer a player to a different backend server by name.
     *
     * @param session the player's session
     * @param serverName the target server name
     * @throws IllegalArgumentException if the server is not found
     */
    public static void transferPlayer(@Nonnull ProxySession session, @Nonnull String serverName) {
        java.util.Objects.requireNonNull(session, "session");
        java.util.Objects.requireNonNull(serverName, "serverName");
        BackendServer backend = getCore().getConfig().getBackendByName(serverName);
        if (backend == null) {
            throw new IllegalArgumentException("Unknown server: " + serverName);
        }
        new me.internalizable.numdrassl.server.transfer.PlayerTransfer(getCore()).transfer(session, backend);
    }

    /**
     * Get all configured backend servers.
     *
     * @return list of backend servers
     */
    @Nonnull
    public static java.util.List<BackendServer> getBackendServers() {
        return getCore().getConfig().getBackends();
    }

    /**
     * Get the default backend server.
     *
     * @return the default backend, or null if not configured
     */
    @Nullable
    public static BackendServer getDefaultBackend() {
        return getCore().getConfig().getDefaultBackend();
    }

    // =========================================
    // Proxy Information
    // =========================================

    /**
     * Check if the proxy is running.
     *
     * @return true if running
     */
    public static boolean isRunning() {
        return core != null && core.isRunning();
    }

    /**
     * Get the proxy configuration.
     *
     * @return the proxy config
     */
    @Nonnull
    public static ProxyConfig getConfig() {
        return getCore().getConfig();
    }
}

