package me.internalizable.numdrassl.cluster;

import me.internalizable.numdrassl.api.cluster.ProxyInfo;
import me.internalizable.numdrassl.api.messaging.MessagingService;
import me.internalizable.numdrassl.cluster.handler.BroadcastHandler;
import me.internalizable.numdrassl.cluster.handler.ChatHandler;
import me.internalizable.numdrassl.cluster.handler.PlayerCountHandler;
import me.internalizable.numdrassl.cluster.handler.TransferHandler;
import me.internalizable.numdrassl.config.ProxyConfig;
import me.internalizable.numdrassl.event.api.NumdrasslEventManager;
import me.internalizable.numdrassl.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Central coordinator for all cluster-related functionality.
 *
 * <p>This class is the single entry point for cluster features. It manages:</p>
 * <ul>
 *   <li><b>Proxy Discovery</b> - Tracks online proxies via heartbeats</li>
 *   <li><b>Heartbeat Publishing</b> - Announces this proxy's presence</li>
 *   <li><b>System Channels</b> - BROADCAST, CHAT, PLAYER_COUNT, TRANSFER</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <pre>
 * ClusterManager
 *   ├── ProxyRegistry      (receives heartbeats, tracks proxies)
 *   ├── HeartbeatPublisher (publishes heartbeats)
 *   └── handlers/
 *       ├── BroadcastHandler
 *       ├── ChatHandler
 *       ├── PlayerCountHandler
 *       └── TransferHandler
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ClusterManager cluster = new ClusterManager(config, messaging, sessions, events);
 * cluster.start();
 *
 * // Send broadcast
 * cluster.broadcast("announcement", "Server restart in 5 minutes!");
 *
 * // Get global player count
 * int total = cluster.getGlobalPlayerCount();
 *
 * // Get online proxies
 * Collection<ProxyInfo> proxies = cluster.getOnlineProxies();
 * }</pre>
 */
public final class ClusterManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterManager.class);

    // Configuration
    private final String proxyId;
    private final String region;
    private final InetSocketAddress publicAddress;
    private final int maxPlayers;

    // Dependencies
    private final MessagingService messagingService;
    private final SessionManager sessionManager;
    private final NumdrasslEventManager eventManager;

    // Components (lazily initialized)
    private ProxyRegistry proxyRegistry;
    private HeartbeatPublisher heartbeatPublisher;
    private BroadcastHandler broadcastHandler;
    private ChatHandler chatHandler;
    private PlayerCountHandler playerCountHandler;
    private TransferHandler transferHandler;

    private boolean started = false;

    /**
     * Create a new cluster manager.
     *
     * @param proxyId unique identifier for this proxy
     * @param region the region/datacenter identifier
     * @param publicAddress the public address clients connect to
     * @param maxPlayers maximum player capacity
     * @param messagingService the messaging service (Redis)
     * @param sessionManager the session manager
     * @param eventManager the event manager
     */
    public ClusterManager(
            @Nonnull String proxyId,
            @Nonnull String region,
            @Nonnull InetSocketAddress publicAddress,
            int maxPlayers,
            @Nonnull MessagingService messagingService,
            @Nonnull SessionManager sessionManager,
            @Nonnull NumdrasslEventManager eventManager) {
        this.proxyId = Objects.requireNonNull(proxyId, "proxyId");
        this.region = Objects.requireNonNull(region, "region");
        this.publicAddress = Objects.requireNonNull(publicAddress, "publicAddress");
        this.maxPlayers = maxPlayers;
        this.messagingService = Objects.requireNonNull(messagingService, "messagingService");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.eventManager = Objects.requireNonNull(eventManager, "eventManager");
    }

    // ==================== Lifecycle ====================

    /**
     * Start all cluster services.
     *
     * <p>If the messaging service is not connected, cluster features
     * will be disabled and this method returns immediately.</p>
     */
    public void start() {
        if (started) {
            LOGGER.warn("Cluster manager already started");
            return;
        }

        if (!messagingService.isConnected()) {
            LOGGER.info("Messaging service not connected - cluster features disabled");
            return;
        }

        LOGGER.info("Starting cluster manager for proxy: {} (region: {})", proxyId, region);

        // Core: Proxy discovery
        proxyRegistry = new ProxyRegistry(messagingService, eventManager, proxyId, "1.0.0");
        proxyRegistry.start();

        // Core: Announce our presence
        heartbeatPublisher = new HeartbeatPublisher(
                messagingService, proxyId, region, publicAddress,
                sessionManager::getSessionCount
        );
        heartbeatPublisher.start();

        // System channel handlers
        broadcastHandler = new BroadcastHandler(messagingService, sessionManager, proxyId);
        broadcastHandler.start();

        chatHandler = new ChatHandler(messagingService, sessionManager, proxyId);
        chatHandler.start();

        playerCountHandler = new PlayerCountHandler(
                messagingService, proxyId,
                sessionManager::getSessionCount, maxPlayers
        );
        playerCountHandler.start();

        transferHandler = new TransferHandler(messagingService, sessionManager, proxyId);
        transferHandler.setProxyRegistry(proxyRegistry); // Wire up dependency
        transferHandler.start();

        started = true;
        LOGGER.info("Cluster manager started - all handlers active");
    }

    /**
     * Stop all cluster services gracefully.
     */
    public void stop() {
        if (!started) {
            return;
        }

        LOGGER.info("Stopping cluster manager...");

        // Stop in reverse order
        stopSafely(transferHandler, "TransferHandler");
        stopSafely(playerCountHandler, "PlayerCountHandler");
        stopSafely(chatHandler, "ChatHandler");
        stopSafely(broadcastHandler, "BroadcastHandler");
        stopSafely(heartbeatPublisher, "HeartbeatPublisher");
        stopSafely(proxyRegistry, "ProxyRegistry");

        started = false;
        LOGGER.info("Cluster manager stopped");
    }

    private void stopSafely(Object handler, String name) {
        if (handler == null) return;
        try {
            if (handler instanceof ProxyRegistry r) r.stop();
            else if (handler instanceof HeartbeatPublisher h) h.stop();
            else if (handler instanceof BroadcastHandler b) b.stop();
            else if (handler instanceof ChatHandler c) c.stop();
            else if (handler instanceof PlayerCountHandler p) p.stop();
            else if (handler instanceof TransferHandler t) t.stop();
        } catch (Exception e) {
            LOGGER.error("Error stopping {}", name, e);
        }
    }

    // ==================== Cluster State ====================

    /**
     * Check if cluster features are enabled and running.
     */
    public boolean isEnabled() {
        return started;
    }

    /**
     * Get all online proxies in the cluster.
     */
    @Nonnull
    public Collection<ProxyInfo> getOnlineProxies() {
        return proxyRegistry != null ? proxyRegistry.getOnlineProxies() : java.util.Collections.emptyList();
    }

    /**
     * Get info about a specific proxy.
     */
    @Nonnull
    public Optional<ProxyInfo> getProxy(@Nonnull String proxyId) {
        return proxyRegistry != null ? proxyRegistry.getProxy(proxyId) : Optional.empty();
    }

    /**
     * Get the number of online proxies.
     */
    public int getOnlineProxyCount() {
        return proxyRegistry != null ? proxyRegistry.getOnlineProxies().size() : 1;
    }

    // ==================== Player Counts ====================

    /**
     * Get the global player count across all proxies.
     */
    public int getGlobalPlayerCount() {
        return playerCountHandler != null
                ? playerCountHandler.getGlobalPlayerCount()
                : sessionManager.getSessionCount();
    }

    /**
     * Get the local player count on this proxy.
     */
    public int getLocalPlayerCount() {
        return sessionManager.getSessionCount();
    }

    // ==================== Broadcast ====================

    /**
     * Send a broadcast message to all proxies.
     *
     * @param type the broadcast type (e.g., "announcement", "alert", "maintenance")
     * @param content the message content
     */
    public void broadcast(@Nonnull String type, @Nonnull String content) {
        if (broadcastHandler != null) {
            broadcastHandler.broadcast(type, content);
        } else {
            // Fallback: deliver locally only
            sessionManager.getAllSessions().forEach(s -> s.sendChatMessage(content));
        }
    }

    // ==================== Cross-Proxy Chat ====================

    /**
     * Send a private message to a player (potentially on another proxy).
     */
    public void sendPrivateMessage(
            @Nonnull java.util.UUID targetUuid,
            @Nonnull String targetName,
            @Nonnull String message,
            @Nonnull String senderName,
            @javax.annotation.Nullable java.util.UUID senderUuid) {
        if (chatHandler != null) {
            chatHandler.sendPrivateMessage(targetUuid, targetName, message, senderName, senderUuid);
        }
    }

    /**
     * Broadcast a chat message to all proxies.
     */
    public void broadcastChat(@Nonnull String message, @Nonnull String senderName,
                              @javax.annotation.Nullable java.util.UUID senderUuid) {
        if (chatHandler != null) {
            chatHandler.broadcastChat(message, senderName, senderUuid);
        }
    }

    // ==================== Cross-Proxy Transfers ====================

    /**
     * Transfer a player to another proxy.
     *
     * <p>This method sends the player to the specified proxy instance.
     * For transfers between backend servers on the SAME proxy, use
     * {@link me.internalizable.numdrassl.server.transfer.PlayerTransfer} instead.</p>
     *
     * @param session the player's session
     * @param targetProxyId the target proxy ID (from {@link #getOnlineProxies()})
     * @param targetServer the backend server to connect to on the target proxy
     * @return future with the transfer result
     */
    public CompletableFuture<me.internalizable.numdrassl.api.player.TransferResult> transferToProxy(
            @Nonnull me.internalizable.numdrassl.session.ProxySession session,
            @Nonnull String targetProxyId,
            @Nonnull String targetServer) {
        if (transferHandler != null) {
            return transferHandler.transferToProxy(session, targetProxyId, targetServer);
        }
        return CompletableFuture.completedFuture(
                me.internalizable.numdrassl.api.player.TransferResult.failure("Cluster not enabled"));
    }

    /**
     * Check if a player has a pending incoming transfer.
     */
    public boolean hasPendingTransfer(@Nonnull java.util.UUID playerUuid) {
        return transferHandler != null && transferHandler.hasPendingTransfer(playerUuid);
    }

    /**
     * Get and consume a pending transfer for a player.
     *
     * @param playerUuid the player's UUID
     * @return the pending transfer, or null if none
     */
    @Nullable
    public TransferHandler.PendingTransfer consumePendingTransfer(@Nonnull java.util.UUID playerUuid) {
        return transferHandler != null ? transferHandler.consumePendingTransfer(playerUuid) : null;
    }

    // ==================== Internal Accessors (for advanced use) ====================

    /** Get the proxy registry. */
    public ProxyRegistry getProxyRegistry() { return proxyRegistry; }

    /** Get the broadcast handler. */
    public BroadcastHandler getBroadcastHandler() { return broadcastHandler; }

    /** Get the chat handler. */
    public ChatHandler getChatHandler() { return chatHandler; }

    /** Get the player count handler. */
    public PlayerCountHandler getPlayerCountHandler() { return playerCountHandler; }

    /** Get the transfer handler. */
    public TransferHandler getTransferHandler() { return transferHandler; }
}
