package me.internalizable.numdrassl.cluster.handler;

import me.internalizable.numdrassl.api.messaging.MessagingService;
import me.internalizable.numdrassl.api.messaging.Subscription;
import me.internalizable.numdrassl.api.messaging.channel.Channels;
import me.internalizable.numdrassl.api.messaging.message.ServerListMessage;
import me.internalizable.numdrassl.plugin.server.NumdrasslRegisteredServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Handles backend server list synchronization across the cluster.
 *
 * <p>When a proxy registers or unregisters a backend server, it publishes a
 * {@link ServerListMessage} to notify other proxies. This handler:</p>
 * <ul>
 *   <li>Receives server registration/unregistration messages</li>
 *   <li>Maintains a map of remote servers (grouped by proxy ID)</li>
 *   <li>Provides callbacks for server add/remove events</li>
 *   <li>Automatically cleans up servers when a proxy disconnects</li>
 * </ul>
 *
 * <h2>Server Ownership</h2>
 * <p>Servers are tracked by both name and owning proxy ID. This allows:</p>
 * <ul>
 *   <li>Multiple proxies to have servers with the same name (different backends)</li>
 *   <li>Automatic cleanup when a proxy leaves the cluster</li>
 *   <li>Proper conflict resolution (local servers take precedence)</li>
 * </ul>
 */
public final class ServerListHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerListHandler.class);

    private final MessagingService messagingService;
    private final String localProxyId;

    /**
     * Remote servers grouped by proxy ID: proxyId -> (serverName -> server)
     * This allows us to clean up all servers from a proxy when it disconnects.
     */
    private final Map<String, Map<String, NumdrasslRegisteredServer>> remoteServersByProxy = new ConcurrentHashMap<>();

    /**
     * Callback invoked when a remote server is added.
     * Parameters: (server, proxyId)
     */
    private Consumer<ServerAddEvent> onServerAdded;

    /**
     * Callback invoked when a remote server is removed.
     * Parameters: (serverName, proxyId)
     */
    private Consumer<ServerRemoveEvent> onServerRemoved;

    private Subscription subscription;

    public ServerListHandler(
            @Nonnull MessagingService messagingService,
            @Nonnull String localProxyId) {
        this.messagingService = Objects.requireNonNull(messagingService, "messagingService");
        this.localProxyId = Objects.requireNonNull(localProxyId, "localProxyId");
    }

    /**
     * Set callback for when a remote server is added.
     *
     * @param callback the callback (server, proxyId)
     */
    public void setOnServerAdded(@Nonnull Consumer<ServerAddEvent> callback) {
        this.onServerAdded = Objects.requireNonNull(callback, "callback");
    }

    /**
     * Set callback for when a remote server is removed.
     *
     * @param callback the callback (serverName, proxyId)
     */
    public void setOnServerRemoved(@Nonnull Consumer<ServerRemoveEvent> callback) {
        this.onServerRemoved = Objects.requireNonNull(callback, "callback");
    }

    /**
     * Start listening for server list messages.
     */
    public void start() {
        subscription = messagingService.subscribe(
                Channels.SERVER_LIST,
                ServerListMessage.class,
                (channel, message) -> handleMessage(message)
        );
        LOGGER.info("Server list handler started");
    }

    /**
     * Stop listening for server list messages.
     */
    public void stop() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
        remoteServersByProxy.clear();
        LOGGER.info("Server list handler stopped");
    }

    /**
     * Remove all servers owned by a specific proxy.
     * Called when a proxy disconnects from the cluster.
     *
     * @param proxyId the proxy ID whose servers should be removed
     */
    public void removeProxyServers(@Nonnull String proxyId) {
        Map<String, NumdrasslRegisteredServer> servers = remoteServersByProxy.remove(proxyId);
        if (servers != null && onServerRemoved != null) {
            for (String serverName : servers.keySet()) {
                onServerRemoved.accept(new ServerRemoveEvent(serverName, proxyId));
            }
            LOGGER.debug("Removed {} servers from proxy {}", servers.size(), proxyId);
        }
    }

    /**
     * Get all remote servers from all proxies.
     *
     * @return map of server name to server instance
     */
    @Nonnull
    public Map<String, NumdrasslRegisteredServer> getAllRemoteServers() {
        Map<String, NumdrasslRegisteredServer> allServers = new ConcurrentHashMap<>();
        for (Map<String, NumdrasslRegisteredServer> proxyServers : remoteServersByProxy.values()) {
            allServers.putAll(proxyServers);
        }
        return allServers;
    }

    /**
     * Find which proxy owns a server by name.
     *
     * @param serverName the server name (case-insensitive)
     * @return the proxy ID that owns this server, or empty if not found
     */
    @Nonnull
    public Optional<String> findProxyForServer(@Nonnull String serverName) {
        String key = serverName.toLowerCase();
        for (Map.Entry<String, Map<String, NumdrasslRegisteredServer>> entry : remoteServersByProxy.entrySet()) {
            if (entry.getValue().containsKey(key)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    // ==================== Message Handling ====================

    private void handleMessage(@Nonnull ServerListMessage message) {
        // Ignore messages from ourselves
        if (localProxyId.equals(message.sourceProxyId())) {
            return;
        }

        switch (message.type()) {
            case REGISTER -> handleRegister(message);
            case UNREGISTER -> handleUnregister(message);
            case SYNC -> handleSync(message);
        }
    }

    private void handleRegister(@Nonnull ServerListMessage message) {
        String proxyId = message.sourceProxyId();
        String serverName = message.serverName().toLowerCase();

        // Get or create proxy's server map
        Map<String, NumdrasslRegisteredServer> proxyServers = remoteServersByProxy.computeIfAbsent(
                proxyId, k -> new ConcurrentHashMap<>()
        );

        // Create or update server
        NumdrasslRegisteredServer server = new NumdrasslRegisteredServer(
                message.serverName(), // Use original case
                new InetSocketAddress(message.host(), message.port())
        );
        server.setDefault(message.isDefault());

        NumdrasslRegisteredServer existing = proxyServers.put(serverName, server);
        if (existing == null) {
            LOGGER.debug("Registered remote server '{}' from proxy {}", serverName, proxyId);
            if (onServerAdded != null) {
                onServerAdded.accept(new ServerAddEvent(server, proxyId));
            }
        } else {
            LOGGER.debug("Updated remote server '{}' from proxy {}", serverName, proxyId);
            if (onServerAdded != null) {
                onServerAdded.accept(new ServerAddEvent(server, proxyId));
            }
        }
    }

    private void handleUnregister(@Nonnull ServerListMessage message) {
        String proxyId = message.sourceProxyId();
        String serverName = message.serverName().toLowerCase();

        Map<String, NumdrasslRegisteredServer> proxyServers = remoteServersByProxy.get(proxyId);
        if (proxyServers == null) {
            return; // Proxy has no servers
        }

        NumdrasslRegisteredServer removed = proxyServers.remove(serverName);
        if (removed != null) {
            LOGGER.debug("Unregistered remote server '{}' from proxy {}", serverName, proxyId);
            if (onServerRemoved != null) {
                onServerRemoved.accept(new ServerRemoveEvent(serverName, proxyId));
            }

            // Clean up empty proxy map
            if (proxyServers.isEmpty()) {
                remoteServersByProxy.remove(proxyId);
            }
        }
    }

    private void handleSync(@Nonnull ServerListMessage message) {
        // SYNC messages are handled the same as REGISTER
        // They're used when a proxy first joins to request all servers
        handleRegister(message);
    }

    // ==================== Event Records ====================

    /**
     * Event fired when a remote server is added.
     */
    public record ServerAddEvent(
            @Nonnull NumdrasslRegisteredServer server,
            @Nonnull String proxyId
    ) {}

    /**
     * Event fired when a remote server is removed.
     */
    public record ServerRemoveEvent(
            @Nonnull String serverName,
            @Nonnull String proxyId
    ) {}
}
