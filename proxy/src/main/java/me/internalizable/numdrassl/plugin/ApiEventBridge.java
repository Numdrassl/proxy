package me.internalizable.numdrassl.plugin;

import com.hypixel.hytale.protocol.Packet;
import me.internalizable.numdrassl.api.event.connection.DisconnectEvent;
import me.internalizable.numdrassl.api.event.connection.PostLoginEvent;
import me.internalizable.numdrassl.api.event.connection.PreLoginEvent;
import me.internalizable.numdrassl.api.event.server.ServerConnectedEvent;
import me.internalizable.numdrassl.api.event.server.ServerPreConnectEvent;
import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.server.RegisteredServer;
import me.internalizable.numdrassl.config.BackendServer;
import me.internalizable.numdrassl.event.NumdrasslEventManager;
import me.internalizable.numdrassl.event.PacketContext;
import me.internalizable.numdrassl.event.PacketEventRegistry;
import me.internalizable.numdrassl.event.PacketListener;
import me.internalizable.numdrassl.session.ProxySession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;

/**
 * Bridges the internal proxy system with the API event system.
 *
 * <p>This class handles two responsibilities:</p>
 * <ul>
 *   <li><b>Session Lifecycle Events</b> - PreLogin, PostLogin, Disconnect (not packet-based)</li>
 *   <li><b>Packet Processing</b> - Delegates to {@link PacketEventRegistry} for packet-to-event mapping</li>
 * </ul>
 *
 * <p>All packet-to-event mappings are handled by {@link PacketEventRegistry} and its
 * mapping classes in {@code me.internalizable.numdrassl.event.mapping.*}</p>
 */
public class ApiEventBridge implements PacketListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiEventBridge.class);

    private final NumdrasslProxyServer apiServer;
    private final PacketEventRegistry packetRegistry;

    public ApiEventBridge(NumdrasslProxyServer apiServer) {
        this.apiServer = apiServer;
        this.packetRegistry = new PacketEventRegistry(apiServer, apiServer.getNumdrasslEventManager());
    }

    /**
     * Get the packet event registry for registering custom mappings.
     */
    public PacketEventRegistry getPacketRegistry() {
        return packetRegistry;
    }

    // ==========================================
    // Session Lifecycle Events (not packet-based)
    // ==========================================

    @Override
    public void onSessionCreated(@Nonnull ProxySession session) {
        // Fire PreLoginEvent when a new connection is established (before any packets)
        PreLoginEvent preLoginEvent = new PreLoginEvent(session.getClientAddress());
        apiServer.getNumdrasslEventManager().fireSync(preLoginEvent);

        if (!preLoginEvent.getResult().isAllowed()) {
            String reason = preLoginEvent.getResult().getDenyReason();
            LOGGER.info("Session {}: PreLoginEvent denied: {}", session.getSessionId(), reason);
            session.disconnect(reason != null ? reason : "Connection denied");
        }
    }

    @Override
    public void onSessionClosed(@Nonnull ProxySession session) {
        // Fire DisconnectEvent when session is closed (cleanup event)
        Player player = getPlayerFromSession(session);
        if (player != null) {
            DisconnectEvent disconnectEvent = new DisconnectEvent(player, DisconnectEvent.DisconnectReason.DISCONNECTED);
            apiServer.getNumdrasslEventManager().fireSync(disconnectEvent);
        }
    }

    // ==========================================
    // Packet Processing - Delegates to Registry
    // ==========================================

    @Override
    @Nullable
    public <T extends Packet> T onClientPacket(@Nonnull me.internalizable.numdrassl.event.PacketEvent<T> internalEvent) {
        return packetRegistry.processPacket(
            internalEvent.getSession(),
            internalEvent.getPacket(),
            PacketContext.Direction.CLIENT_TO_SERVER
        );
    }

    @Override
    @Nullable
    public <T extends Packet> T onServerPacket(@Nonnull me.internalizable.numdrassl.event.PacketEvent<T> internalEvent) {
        return packetRegistry.processPacket(
            internalEvent.getSession(),
            internalEvent.getPacket(),
            PacketContext.Direction.SERVER_TO_CLIENT
        );
    }

    // ==========================================
    // Server Connection Events (called externally)
    // ==========================================

    /**
     * Called before a player connects to a backend server.
     * Fires ServerPreConnectEvent and allows plugins to redirect or cancel.
     *
     * @param session the player session
     * @param backend the target backend server
     * @return the result containing the final target server, or null if cancelled
     */
    @Nullable
    public ServerPreConnectResult fireServerPreConnectEvent(ProxySession session, BackendServer backend) {
        Player player = getPlayerFromSession(session);
        if (player == null) {
            return new ServerPreConnectResult(true, backend, null);
        }

        RegisteredServer server = apiServer.getServer(backend.getName()).orElseGet(() ->
            new NumdrasslRegisteredServer(backend.getName(),
                new InetSocketAddress(backend.getHost(), backend.getPort()))
        );

        ServerPreConnectEvent event = new ServerPreConnectEvent(player, server);
        apiServer.getNumdrasslEventManager().fireSync(event);

        ServerPreConnectEvent.ServerResult result = event.getResult();
        if (!result.isAllowed()) {
            LOGGER.info("Session {}: ServerPreConnectEvent denied: {}",
                session.getSessionId(), result.getDenyReason());
            return new ServerPreConnectResult(false, null, result.getDenyReason());
        }

        // Check if redirected to different server
        RegisteredServer targetServer = result.getServer();
        if (targetServer != null && !targetServer.getName().equalsIgnoreCase(backend.getName())) {
            LOGGER.info("Session {}: ServerPreConnectEvent redirected from {} to {}",
                session.getSessionId(), backend.getName(), targetServer.getName());
            BackendServer newBackend = findBackendByName(targetServer.getName());
            if (newBackend != null) {
                return new ServerPreConnectResult(true, newBackend, null);
            }
        }

        return new ServerPreConnectResult(true, backend, null);
    }

    /**
     * Called when a player successfully connects to a backend server.
     */
    public void fireServerConnectedEvent(ProxySession session, @Nullable ProxySession previousSession) {
        Player player = getPlayerFromSession(session);
        RegisteredServer server = getServerFromSession(session);
        if (player != null && server != null) {
            RegisteredServer previousServer = previousSession != null ? getServerFromSession(previousSession) : null;
            ServerConnectedEvent event = new ServerConnectedEvent(player, server, previousServer);
            apiServer.getNumdrasslEventManager().fireSync(event);
        }
    }

    /**
     * Called when a player completes the login process (after authentication).
     */
    public void firePostLoginEvent(ProxySession session) {
        Player player = getPlayerFromSession(session);
        if (player != null) {
            PostLoginEvent event = new PostLoginEvent(player);
            apiServer.getNumdrasslEventManager().fireSync(event);
        }
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    @Nullable
    private Player getPlayerFromSession(ProxySession session) {
        if (session.getPlayerUuid() != null || session.getUsername() != null) {
            return new NumdrasslPlayer(session, apiServer);
        }
        return null;
    }

    @Nullable
    private RegisteredServer getServerFromSession(ProxySession session) {
        String serverName = session.getCurrentServerName();
        if (serverName != null) {
            return apiServer.getServer(serverName).orElse(null);
        }
        return null;
    }

    @Nullable
    private BackendServer findBackendByName(String name) {
        for (BackendServer backend : apiServer.getInternalServer().getConfig().getBackends()) {
            if (backend.getName().equalsIgnoreCase(name)) {
                return backend;
            }
        }
        return null;
    }

    // ==========================================
    // Result Classes
    // ==========================================

    /**
     * Result of firing ServerPreConnectEvent.
     */
    public static class ServerPreConnectResult {
        private final boolean allowed;
        private final BackendServer targetServer;
        private final String denyReason;

        public ServerPreConnectResult(boolean allowed, @Nullable BackendServer targetServer, @Nullable String denyReason) {
            this.allowed = allowed;
            this.targetServer = targetServer;
            this.denyReason = denyReason;
        }

        public boolean isAllowed() { return allowed; }
        @Nullable public BackendServer getTargetServer() { return targetServer; }
        @Nullable public String getDenyReason() { return denyReason; }
    }
}
