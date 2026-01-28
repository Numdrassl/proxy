package me.internalizable.numdrassl.plugin;

import me.internalizable.numdrassl.api.Numdrassl;
import me.internalizable.numdrassl.api.ProxyServer;
import me.internalizable.numdrassl.api.cluster.ClusterManager;
import me.internalizable.numdrassl.api.command.CommandManager;
import me.internalizable.numdrassl.api.command.CommandSource;
import me.internalizable.numdrassl.api.event.EventManager;
import me.internalizable.numdrassl.api.messaging.MessagingService;
import me.internalizable.numdrassl.api.permission.PermissionManager;
import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.plugin.PluginManager;
import me.internalizable.numdrassl.api.plugin.messaging.ChannelRegistrar;
import me.internalizable.numdrassl.api.scheduler.Scheduler;
import me.internalizable.numdrassl.api.server.RegisteredServer;
import me.internalizable.numdrassl.api.event.Subscribe;
import me.internalizable.numdrassl.api.event.cluster.ProxyLeaveClusterEvent;
import me.internalizable.numdrassl.api.messaging.channel.Channels;
import me.internalizable.numdrassl.api.messaging.message.ServerListMessage;
import me.internalizable.numdrassl.cluster.NumdrasslClusterManager;
import me.internalizable.numdrassl.cluster.handler.ServerListHandler;
import me.internalizable.numdrassl.command.CommandEventListener;
import me.internalizable.numdrassl.command.ConsoleCommandSource;
import me.internalizable.numdrassl.command.NumdrasslCommandManager;
import me.internalizable.numdrassl.command.builtin.*;
import me.internalizable.numdrassl.event.api.NumdrasslEventManager;
import me.internalizable.numdrassl.messaging.local.LocalMessagingService;
import me.internalizable.numdrassl.messaging.redis.RedisMessagingService;
import me.internalizable.numdrassl.plugin.bridge.ApiEventBridge;
import me.internalizable.numdrassl.plugin.loader.NumdrasslPluginManager;
import me.internalizable.numdrassl.plugin.messaging.NumdrasslChannelRegistrar;
import me.internalizable.numdrassl.plugin.messaging.NoOpBackendMessagingService;
import me.internalizable.numdrassl.plugin.permission.NumdrasslPermissionManager;
import me.internalizable.numdrassl.plugin.player.NumdrasslPlayer;
import me.internalizable.numdrassl.plugin.server.NumdrasslRegisteredServer;
import me.internalizable.numdrassl.scheduler.NumdrasslScheduler;
import me.internalizable.numdrassl.server.ProxyCore;
import me.internalizable.numdrassl.session.ProxySession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;

/**
 * Implementation of the public {@link ProxyServer} API interface.
 *
 * <p>Bridges the internal {@link ProxyCore} with the public API that plugins use.
 * Delegates calls to appropriate internal components while maintaining separation of concerns.</p>
 *
 * <p>Plugin developers should interact through the {@link ProxyServer} interface
 * obtained via {@link Numdrassl#getProxy()}.</p>
 *
 * @see ProxyServer the public API interface
 * @see ProxyCore the internal implementation
 */
public final class NumdrasslProxy implements ProxyServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(NumdrasslProxy.class);
    private static final String VERSION = "1.0.0-SNAPSHOT";

    // Core reference
    private final ProxyCore core;

    // Internal managers
    private final NumdrasslEventManager eventManager;
    private final NumdrasslCommandManager commandManager;
    private final NumdrasslPluginManager pluginManager;
    private final NumdrasslScheduler scheduler;
    private final NumdrasslPermissionManager permissionManager;
    private final NumdrasslChannelRegistrar channelRegistrar;
    private final ApiEventBridge eventBridge;
    private final NumdrasslClusterManager clusterManager;
    private MessagingService messagingService;
    private ServerListHandler serverListHandler;

    // Server registry (local servers only)
    private final Map<String, NumdrasslRegisteredServer> servers = new ConcurrentHashMap<>();

    // Paths
    private final Path dataDirectory;
    private final Path configDirectory;

    // ==================== Construction ====================

    public NumdrasslProxy(@Nonnull ProxyCore core) {
        this.core = Objects.requireNonNull(core, "core");
        this.eventManager = new NumdrasslEventManager();
        this.commandManager = new NumdrasslCommandManager();
        this.scheduler = new NumdrasslScheduler();
        this.permissionManager = new NumdrasslPermissionManager();
        this.channelRegistrar = new NumdrasslChannelRegistrar();
        this.dataDirectory = Paths.get("data");
        this.configDirectory = Paths.get("config");
        this.pluginManager = new NumdrasslPluginManager(this, Paths.get("plugins"));
        this.eventBridge = new ApiEventBridge(this);
        this.clusterManager = new NumdrasslClusterManager(core.getConfig(), core.getSessionManager());

        core.getEventManager().registerListener(eventBridge);
        registerConfiguredServers();
    }

    private void registerConfiguredServers() {
        if (core.getConfig() == null) {
            return;
        }

        for (var backend : core.getConfig().getBackends()) {
            InetSocketAddress address = new InetSocketAddress(backend.getHost(), backend.getPort());
            NumdrasslRegisteredServer server = new NumdrasslRegisteredServer(backend.getName(), address);
            server.setDefault(backend.isDefaultServer());
            String serverName = backend.getName().toLowerCase();
            servers.put(serverName, server);
            
            // Publish to cluster if enabled
            if (messagingService != null && clusterManager.isClusterMode()) {
                publishServerRegistration(backend.getName(), backend.getHost(), backend.getPort(), backend.isDefaultServer());
            }
        }
    }

    // ==================== Initialization ====================

    /**
     * Initializes the proxy API layer.
     * Called by {@link ProxyCore} after networking is ready.
     */
    public void initialize() {
        initializeMessaging();
        registerBuiltinCommands();
        registerCommandListener();
        loadPlugins();
    }


    private void initializeMessaging() {
        var config = core.getConfig();

        if (config.isClusterEnabled()) {
            try {
                this.messagingService = RedisMessagingService.create(
                        clusterManager.getLocalProxyId(),
                        config
                );
                clusterManager.initialize(messagingService, eventManager);
                
                // Initialize server list handler for cross-cluster server synchronization
                this.serverListHandler = new ServerListHandler(messagingService, clusterManager.getLocalProxyId());
                serverListHandler.setOnServerAdded(event -> {
                    // Remote server added - already tracked in handler, no action needed
                    LOGGER.debug("Remote server '{}' added from proxy {}", event.server().getName(), event.proxyId());
                });
                serverListHandler.setOnServerRemoved(event -> {
                    // Remote server removed - already removed from handler, no action needed
                    LOGGER.debug("Remote server '{}' removed from proxy {}", event.serverName(), event.proxyId());
                });
                serverListHandler.start();
                
                // Subscribe to proxy leave events to clean up servers
                ProxyLeaveEventListener leaveListener = new ProxyLeaveEventListener();
                eventManager.register(this, leaveListener);
                
                LOGGER.info("Cluster mode enabled - connected to Redis");
            } catch (Exception e) {
                LOGGER.error("Failed to connect to Redis, falling back to local mode", e);
                this.messagingService = new LocalMessagingService(clusterManager.getLocalProxyId());
                // Initialize cluster manager with local service to maintain consistent state
                clusterManager.initializeLocalMode(messagingService, eventManager);
                LOGGER.warn("Cluster manager running in degraded local-only mode");
            }
        } else {
            this.messagingService = new LocalMessagingService(clusterManager.getLocalProxyId());
            clusterManager.initializeLocalMode(messagingService, eventManager);
            LOGGER.info("Running in standalone mode (cluster disabled)");
        }
    }

    private void registerBuiltinCommands() {
        commandManager.register(this, new HelpCommand(commandManager));
        commandManager.register(this, new AuthCommand(core));
        commandManager.register(this, new SessionsCommand(core));
        commandManager.register(this, new StopCommand(core), "shutdown", "end");
        commandManager.register(this, new ServerCommand(), "srv");
        commandManager.register(this, new FindCommand(), "find-server");
        commandManager.register(this, new NumdrasslCommand(), "nd", "proxy");
        commandManager.register(this, new MetricsCommand(), "stats", "perf", "performance");
    }

    private void registerCommandListener() {
        eventManager.register(this, new CommandEventListener(commandManager));
    }

    private void loadPlugins() {
        pluginManager.loadPlugins();
        pluginManager.enablePlugins();
    }

    // ==================== Shutdown ====================

    /**
     * Shuts down the API layer without stopping the core.
     * Called by {@link ProxyCore} during shutdown sequence.
     */
    public void shutdownApi() {
        pluginManager.disablePlugins();
        
        // Stop server list handler
        if (serverListHandler != null) {
            serverListHandler.stop();
            serverListHandler = null;
        }
        
        clusterManager.shutdown();

        if (messagingService instanceof RedisMessagingService redisService) {
            redisService.shutdown();
        }

        scheduler.shutdown();
        eventManager.shutdown();
    }

    // ==================== ProxyServer API ====================

    @Override
    @Nonnull
    public EventManager getEventManager() {
        return eventManager;
    }

    @Override
    @Nonnull
    public CommandManager getCommandManager() {
        return commandManager;
    }

    @Override
    @Nonnull
    public CommandSource getConsoleCommandSource() {
        return ConsoleCommandSource.getInstance();
    }

    @Override
    @Nonnull
    public PluginManager getPluginManager() {
        return pluginManager;
    }

    @Override
    @Nonnull
    public ChannelRegistrar getChannelRegistrar() {
        return channelRegistrar;
    }

    @Override
    @Nonnull
    public Scheduler getScheduler() {
        return scheduler;
    }

    @Override
    @Nonnull
    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    @Override
    @Nonnull
    public MessagingService getMessagingService() {
        if (messagingService == null) {
            throw new IllegalStateException("MessagingService not initialized; call initialize() first");
        }
        return messagingService;
    }

    @Override
    @Nonnull
    public me.internalizable.numdrassl.api.messaging.backend.BackendMessagingService getBackendMessagingService() {
        // TODO: Implement backend messaging via Redis pub/sub
        return NoOpBackendMessagingService.INSTANCE;
    }

    @Override
    @Nonnull
    public ClusterManager getClusterManager() {
        return clusterManager;
    }

    @Override
    public int getGlobalPlayerCount() {
        return clusterManager.getGlobalPlayerCount();
    }

    // ==================== Player Management ====================

    @Override
    @Nonnull
    public Collection<Player> getAllPlayers() {
        List<Player> players = new ArrayList<>();
        for (ProxySession session : core.getSessionManager().getAllSessions()) {
            Player player = getOrCreatePlayer(session);
            if (player != null) {
                players.add(player);
            }
        }
        return Collections.unmodifiableList(players);
    }

    @Override
    @Nonnull
    public Optional<Player> getPlayer(@Nonnull UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return core.getSessionManager().findByUuid(uuid)
            .map(this::getOrCreatePlayer);
    }

    @Override
    @Nonnull
    public Optional<Player> getPlayer(@Nonnull String username) {
        Objects.requireNonNull(username, "username");
        for (ProxySession session : core.getSessionManager().getAllSessions()) {
            if (username.equalsIgnoreCase(session.getUsername())) {
                return Optional.ofNullable(getOrCreatePlayer(session));
            }
        }
        return Optional.empty();
    }

    /**
     * Gets the cached player for a session, or creates and caches a new one.
     */
    @Nullable
    private Player getOrCreatePlayer(@Nonnull ProxySession session) {
        // Check for cached player first
        Player cached = session.getCachedPlayer();
        if (cached != null) {
            return cached;
        }

        // Create new player if identity is available
        if (session.getPlayerUuid() != null || session.getUsername() != null) {
            NumdrasslPlayer player = new NumdrasslPlayer(session, this);
            session.setCachedPlayer(player);
            return player;
        }
        return null;
    }

    @Override
    public int getPlayerCount() {
        return core.getSessionManager().getSessionCount();
    }

    // ==================== Server Management ====================

    @Override
    @Nonnull
    public Collection<RegisteredServer> getAllServers() {
        // Combine local and remote servers
        // Local servers take precedence if there's a name conflict
        Map<String, RegisteredServer> allServers = new LinkedHashMap<>();
        
        // Add remote servers first (lower priority)
        if (serverListHandler != null) {
            for (NumdrasslRegisteredServer remoteServer : serverListHandler.getAllRemoteServers().values()) {
                String key = remoteServer.getName().toLowerCase();
                allServers.put(key, remoteServer);
            }
        }
        
        // Add local servers (higher priority - overwrites remote servers with same name)
        for (NumdrasslRegisteredServer localServer : servers.values()) {
            String key = localServer.getName().toLowerCase();
            allServers.put(key, localServer);
        }
        
        return Collections.unmodifiableCollection(allServers.values());
    }

    @Override
    @Nonnull
    public Optional<RegisteredServer> getServer(@Nonnull String name) {
        Objects.requireNonNull(name, "name");
        String key = name.toLowerCase();
        
        // Check local servers first (take precedence)
        NumdrasslRegisteredServer local = servers.get(key);
        if (local != null) {
            return Optional.of(local);
        }
        
        // Check remote servers
        if (serverListHandler != null) {
            Map<String, NumdrasslRegisteredServer> remoteServers = serverListHandler.getAllRemoteServers();
            NumdrasslRegisteredServer remote = remoteServers.get(key);
            if (remote != null) {
                return Optional.of(remote);
            }
        }
        
        return Optional.empty();
    }

    @Override
    @Nonnull
    public RegisteredServer registerServer(@Nonnull String name, @Nonnull InetSocketAddress address) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(address, "address");

        NumdrasslRegisteredServer server = new NumdrasslRegisteredServer(name, address);
        String serverName = name.toLowerCase();
        servers.put(serverName, server);
        
        // Publish to cluster if enabled
        if (messagingService != null && clusterManager.isClusterMode()) {
            publishServerRegistration(name, address.getHostString(), address.getPort(), false);
        }
        
        return server;
    }

    @Override
    public boolean unregisterServer(@Nonnull String name) {
        Objects.requireNonNull(name, "name");
        String serverName = name.toLowerCase();
        boolean removed = servers.remove(serverName) != null;
        
        // Publish to cluster if enabled
        if (removed && messagingService != null && clusterManager.isClusterMode()) {
            publishServerUnregistration(name);
        }
        
        return removed;
    }
    
    // ==================== Server List Synchronization ====================
    
    /**
     * Publishes a server registration message to the cluster.
     *
     * @param serverName the server name
     * @param host the server host
     * @param port the server port
     * @param isDefault whether this is the default server
     */
    private void publishServerRegistration(@Nonnull String serverName, @Nonnull String host, int port, boolean isDefault) {
        if (messagingService == null || !clusterManager.isClusterMode()) {
            return;
        }
        
        ServerListMessage message = ServerListMessage.register(
                clusterManager.getLocalProxyId(),
                serverName,
                host,
                port,
                isDefault
        );
        
        messagingService.publish(Channels.SERVER_LIST, message)
                .exceptionally(e -> {
                    LOGGER.warn("Failed to publish server registration for '{}': {}", serverName, e.getMessage());
                    return null;
                });
    }
    
    /**
     * Publishes a server unregistration message to the cluster.
     *
     * @param serverName the server name to unregister
     */
    private void publishServerUnregistration(@Nonnull String serverName) {
        if (messagingService == null || !clusterManager.isClusterMode()) {
            return;
        }
        
        ServerListMessage message = ServerListMessage.unregister(
                clusterManager.getLocalProxyId(),
                serverName
        );
        
        messagingService.publish(Channels.SERVER_LIST, message)
                .exceptionally(e -> {
                    LOGGER.warn("Failed to publish server unregistration for '{}': {}", serverName, e.getMessage());
                    return null;
                });
    }
    
    /**
     * Event listener for proxy leave events to clean up remote servers.
     */
    private class ProxyLeaveEventListener {
        @Subscribe
        public void onProxyLeave(@Nonnull ProxyLeaveClusterEvent event) {
            if (serverListHandler != null) {
                serverListHandler.removeProxyServers(event.getProxyId());
                LOGGER.debug("Cleaned up servers from disconnected proxy: {}", event.getProxyId());
            }
        }
    }

    // ==================== Configuration ====================

    @Override
    @Nonnull
    public InetSocketAddress getBoundAddress() {
        var config = core.getConfig();
        return new InetSocketAddress(config.getBindAddress(), config.getBindPort());
    }

    @Override
    @Nonnull
    public InetSocketAddress getPublicAddress() {
        var config = core.getConfig();
        String host = config.getPublicAddress();
        if (host == null || host.isEmpty()) {
            host = config.getBindAddress();
        }
        int port = config.getPublicPort() > 0 ? config.getPublicPort() : config.getBindPort();
        return new InetSocketAddress(host, port);
    }

    @Override
    @Nonnull
    public Path getDataDirectory() {
        return dataDirectory;
    }

    @Override
    @Nonnull
    public Path getConfigDirectory() {
        return configDirectory;
    }

    @Override
    public boolean isRunning() {
        return core.isRunning();
    }

    @Override
    public void shutdown() {
        core.stop();
    }

    @Override
    @Nonnull
    public String getVersion() {
        return VERSION;
    }

    // ==================== Internal Access ====================

    /**
     * Gets the internal proxy core.
     */
    @Nonnull
    public ProxyCore getCore() {
        return core;
    }

    /**
     * Gets the internal event manager.
     */
    @Nonnull
    public NumdrasslEventManager getNumdrasslEventManager() {
        return eventManager;
    }

    /**
     * Gets the API event bridge.
     */
    @Nonnull
    public ApiEventBridge getEventBridge() {
        return eventBridge;
    }

    /**
     * Gets the server list handler (for cluster mode).
     *
     * @return the server list handler, or null if cluster mode is disabled
     */
    @Nullable
    public ServerListHandler getServerListHandler() {
        return serverListHandler;
    }
}

