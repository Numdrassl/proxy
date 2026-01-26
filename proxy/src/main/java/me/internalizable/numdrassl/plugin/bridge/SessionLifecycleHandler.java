package me.internalizable.numdrassl.plugin.bridge;

import me.internalizable.numdrassl.api.event.connection.AsyncLoginEvent;
import me.internalizable.numdrassl.api.event.connection.DisconnectEvent;
import me.internalizable.numdrassl.api.event.connection.PostLoginEvent;
import me.internalizable.numdrassl.api.event.connection.PreLoginEvent;
import me.internalizable.numdrassl.api.event.server.ServerConnectedEvent;
import me.internalizable.numdrassl.api.event.server.ServerPreConnectEvent;
import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.server.RegisteredServer;
import me.internalizable.numdrassl.cluster.NumdrasslClusterManager;
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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

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

        // Unregister player location from cluster
        if (session.getPlayerUuid() != null) {
            NumdrasslClusterManager clusterManager =
                (NumdrasslClusterManager) proxy.getClusterManager();
            clusterManager.unregisterPlayerLocation(session.getPlayerUuid());
        }

        Player player = getOrCreatePlayer(session);
        if (player != null) {
            // Remove player from their current server's player list
            removePlayerFromCurrentServer(session, player);

            DisconnectEvent event = new DisconnectEvent(player, DisconnectEvent.DisconnectReason.DISCONNECTED);
            eventManager.fireSync(event);
        }
    }

    /**
     * Sets up the player's permissions by firing PermissionSetupEvent.
     * This should be called BEFORE the LoginEvent is fired.
     *
     * @param session the session
     * @return a CompletableFuture containing the player, or completing with null on failure
     */
    @Nonnull
    public CompletableFuture<Player> setupPlayerPermissions(@Nonnull ProxySession session) {
        Objects.requireNonNull(session, "session");

        // Get or create the cached player
        NumdrasslPlayer player = (NumdrasslPlayer) getOrCreatePlayer(session);
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }

        // Set up permissions (fires PermissionSetupEvent and waits for async tasks)
        return player.setupPermissions().thenApply(v -> player);
    }

    /**
     * Handles asynchronous login phase after authentication completes.
     *
     * <p>This method fires the {@link AsyncLoginEvent} and manages the async barrier pattern,
     * allowing plugins to register {@link CompletableFuture} tasks for data loading (e.g.,
     * permissions, database queries) before the player fully joins.</p>
     *
     * @param session the authenticated session
     * @return a CompletableFuture that completes with the AsyncLoginEvent result when all
     * async tasks finish, or completes exceptionally on failure
     */
    @Nonnull
    public CompletableFuture<AsyncLoginEvent.AsyncLoginResult> onAsyncLogin(@Nonnull ProxySession session) {
        Objects.requireNonNull(session, "session");

        // 1. Prepare the Cached Player instance
        Player player = getOrCreatePlayer(session);
        if (player == null) {
            CompletableFuture<AsyncLoginEvent.AsyncLoginResult> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Player initialization failed: Session cache invalid"));
            return future;
        }

        // 2. Instantiate and fire the AsyncLoginEvent
        AsyncLoginEvent asyncEvent = new AsyncLoginEvent(player);
        eventManager.fireSync(asyncEvent);

        // 3. Handle Async Barriers (Synchronization Barrier Pattern)
        List<CompletableFuture<?>> tasks = asyncEvent.getLoginTasks();

        if (tasks.isEmpty()) {
            // Fast Path: No plugins requested a wait. Return immediately with result.
            LOGGER.debug("Session {}: No async login tasks registered, proceeding immediately",
                    session.getSessionId());
            return CompletableFuture.completedFuture(asyncEvent.getResult());
        }

        // Slow Path: Wait for all registered futures (e.g., Database, API calls) to complete.
        LOGGER.info("Session {}: Waiting for {} async login tasks...",
                session.getSessionId(), tasks.size());

        CompletableFuture<AsyncLoginEvent.AsyncLoginResult> resultFuture = new CompletableFuture<>();

        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]))
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        LOGGER.error("Session {}: Async login task failed unexpectedly",
                                session.getSessionId(), ex);
                        resultFuture.completeExceptionally(ex);
                        return;
                    }

                    if (!session.isActive()) {
                        LOGGER.debug("Session {}: Client disconnected during async wait. Aborting login.",
                                session.getSessionId());
                        resultFuture.completeExceptionally(
                                new IllegalStateException("Session closed during async login"));
                        return;
                    }

                    LOGGER.debug("Session {}: All {} async login tasks completed successfully",
                            session.getSessionId(), tasks.size());
                    resultFuture.complete(asyncEvent.getResult());
                });

        return resultFuture;
    }

    /**
     * Handles successful authentication (PostLogin).
     *
     * @param session the authenticated session
     */
    public void onPostLogin(@Nonnull ProxySession session) {
        Objects.requireNonNull(session, "session");

        Player player = getOrCreatePlayer(session);
        if (player != null) {
            // Register player location in cluster
            if (session.getPlayerUuid() != null) {
                NumdrasslClusterManager clusterManager =
                    (NumdrasslClusterManager) proxy.getClusterManager();
                clusterManager.registerPlayerLocation(session.getPlayerUuid());
            }

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

        Player player = getOrCreatePlayer(session);
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

        // Flush any pending messages now that player is connected
        session.flushPendingMessages();

        Player player = getOrCreatePlayer(session);
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
    private Player getOrCreatePlayer(ProxySession session) {
        // Check for cached player first
        Player cached = session.getCachedPlayer();
        if (cached != null) {
            return cached;
        }

        // Create new player if identity is available
        if (session.getPlayerUuid() != null || session.getUsername() != null) {
            NumdrasslPlayer player = new NumdrasslPlayer(session, proxy);
            session.setCachedPlayer(player);
            return player;
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

