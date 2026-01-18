package me.internalizable.numdrassl.plugin.bridge;

import me.internalizable.numdrassl.api.event.connection.DisconnectEvent;
import me.internalizable.numdrassl.api.event.connection.PostLoginEvent;
import me.internalizable.numdrassl.api.event.connection.PreLoginEvent;
import me.internalizable.numdrassl.api.event.server.ServerConnectedEvent;
import me.internalizable.numdrassl.api.event.server.ServerPreConnectEvent;
import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.server.RegisteredServer;
import me.internalizable.numdrassl.config.BackendServer;
import me.internalizable.numdrassl.event.api.NumdrasslEventManager;
import me.internalizable.numdrassl.plugin.NumdrasslProxy;
import me.internalizable.numdrassl.plugin.player.NumdrasslPlayer;
import me.internalizable.numdrassl.plugin.server.NumdrasslRegisteredServer;
import me.internalizable.numdrassl.session.ProxySession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * Handles session lifecycle events and fires corresponding API events.
 *
 * <p>Manages the following lifecycle events:</p>
 * <ul>
 *   <li>{@link PreLoginEvent} - When a new connection is established</li>
 *   <li>{@link PostLoginEvent} - When authentication completes</li>
 *   <li>{@link DisconnectEvent} - When a session closes</li>
 *   <li>{@link ServerPreConnectEvent} - Before connecting to a backend</li>
 *   <li>{@link ServerConnectedEvent} - After successful backend connection</li>
 * </ul>
 */
public final class SessionLifecycleHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionLifecycleHandler.class);

    private final NumdrasslProxy proxy;
    private final NumdrasslEventManager eventManager;

    public SessionLifecycleHandler(@Nonnull NumdrasslProxy proxy) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.eventManager = proxy.getNumdrasslEventManager();
    }

    // ==================== Connection Events ====================

    /**
     * Handles a new session being created (PreLogin).
     *
     * @param session the new session
     * @return true if the connection is allowed, false if denied
     */
    public boolean onSessionCreated(@Nonnull ProxySession session) {
        Objects.requireNonNull(session, "session");

        PreLoginEvent event = new PreLoginEvent(session.getClientAddress());
        eventManager.fireSync(event);

        if (!event.getResult().isAllowed()) {
            String reason = event.getResult().getDenyReason();
            LOGGER.info("Session {}: PreLogin denied: {}", session.getSessionId(), reason);
            session.disconnect(reason != null ? reason : "Connection denied");
            return false;
        }

        return true;
    }

    /**
     * Handles a session being closed (Disconnect).
     *
     * @param session the closed session
     */
    public void onSessionClosed(@Nonnull ProxySession session) {
        Objects.requireNonNull(session, "session");

        Player player = createPlayer(session);
        if (player != null) {
            // Remove player from their current server's player list
            removePlayerFromCurrentServer(session, player);

            DisconnectEvent event = new DisconnectEvent(player, DisconnectEvent.DisconnectReason.DISCONNECTED);
            eventManager.fireSync(event);
        }
    }

    /**
     * Handles successful authentication (PostLogin).
     *
     * @param session the authenticated session
     */
    public void onPostLogin(@Nonnull ProxySession session) {
        Objects.requireNonNull(session, "session");

        Player player = createPlayer(session);
        if (player != null) {
            PostLoginEvent event = new PostLoginEvent(player);
            eventManager.fireSync(event);
        }
    }

    // ==================== Server Connection Events ====================

    /**
     * Handles pre-connect to a backend server.
     *
     * @param session the player session
     * @param backend the target backend server
     * @return the result containing the final target, or denial reason
     */
    @Nonnull
    public ServerPreConnectResult onServerPreConnect(
            @Nonnull ProxySession session,
            @Nonnull BackendServer backend) {

        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(backend, "backend");

        Player player = createPlayer(session);
        if (player == null) {
            return ServerPreConnectResult.allow(backend);
        }

        RegisteredServer server = resolveServer(backend);
        ServerPreConnectEvent event = new ServerPreConnectEvent(player, server);
        eventManager.fireSync(event);

        return processServerPreConnectResult(session, backend, event.getResult());
    }

    private ServerPreConnectResult processServerPreConnectResult(
            ProxySession session,
            BackendServer originalBackend,
            ServerPreConnectEvent.ServerResult result) {

        if (!result.isAllowed()) {
            LOGGER.info("Session {}: ServerPreConnect denied: {}",
                session.getSessionId(), result.getDenyReason());
            return ServerPreConnectResult.deny(result.getDenyReason());
        }

        RegisteredServer targetServer = result.getServer();
        if (targetServer != null && !targetServer.getName().equalsIgnoreCase(originalBackend.getName())) {
            BackendServer newBackend = findBackend(targetServer.getName());
            if (newBackend != null) {
                LOGGER.info("Session {}: Redirected from {} to {}",
                    session.getSessionId(), originalBackend.getName(), newBackend.getName());
                return ServerPreConnectResult.redirect(newBackend);
            }
        }

        return ServerPreConnectResult.allow(originalBackend);
    }

    /**
     * Handles successful connection to a backend server.
     *
     * @param session the player session
     * @param previousSession the previous session (for server switch), or null
     */
    public void onServerConnected(
            @Nonnull ProxySession session,
            @Nullable ProxySession previousSession) {

        Objects.requireNonNull(session, "session");

        Player player = createPlayer(session);
        RegisteredServer server = resolveServerFromSession(session);

        LOGGER.debug("Session {}: onServerConnected - player={}, server={}, serverName={}",
            session.getSessionId(),
            player != null ? player.getUsername() : "null",
            server != null ? server.getName() : "null",
            session.getCurrentServerName());

        if (player != null && server != null) {
            RegisteredServer previousServer = previousSession != null
                ? resolveServerFromSession(previousSession)
                : null;

            // Remove from previous server
            if (previousServer instanceof NumdrasslRegisteredServer prevNumdrasslServer) {
                prevNumdrasslServer.removePlayer(player);
                LOGGER.debug("Session {}: Removed player from previous server {}",
                    session.getSessionId(), previousServer.getName());
            }

            // Add to new server
            if (server instanceof NumdrasslRegisteredServer numdrasslServer) {
                numdrasslServer.addPlayer(player);
                LOGGER.debug("Session {}: Added player to server {}, playerCount now {}",
                    session.getSessionId(), server.getName(), numdrasslServer.getPlayerCount());
            }

            ServerConnectedEvent event = new ServerConnectedEvent(player, server, previousServer);
            eventManager.fireSync(event);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Removes a player from their current server's player list.
     */
    private void removePlayerFromCurrentServer(ProxySession session, Player player) {
        RegisteredServer currentServer = resolveServerFromSession(session);
        if (currentServer instanceof NumdrasslRegisteredServer numdrasslServer) {
            numdrasslServer.removePlayer(player);
        }
    }

    @Nullable
    private Player createPlayer(ProxySession session) {
        if (session.getPlayerUuid() != null || session.getUsername() != null) {
            return new NumdrasslPlayer(session, proxy);
        }
        return null;
    }

    @Nonnull
    private RegisteredServer resolveServer(BackendServer backend) {
        return proxy.getServer(backend.getName())
            .orElseGet(() -> new NumdrasslRegisteredServer(
                backend.getName(),
                new InetSocketAddress(backend.getHost(), backend.getPort())
            ));
    }

    @Nullable
    private RegisteredServer resolveServerFromSession(ProxySession session) {
        String serverName = session.getCurrentServerName();
        return serverName != null ? proxy.getServer(serverName).orElse(null) : null;
    }

    @Nullable
    private BackendServer findBackend(String name) {
        for (BackendServer backend : proxy.getCore().getConfig().getBackends()) {
            if (backend.getName().equalsIgnoreCase(name)) {
                return backend;
            }
        }
        return null;
    }
}

