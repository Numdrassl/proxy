package me.internalizable.numdrassl.plugin;

import me.internalizable.numdrassl.api.Numdrassl;
import me.internalizable.numdrassl.api.ProxyServer;
import me.internalizable.numdrassl.api.command.CommandManager;
import me.internalizable.numdrassl.api.event.EventManager;
import me.internalizable.numdrassl.api.permission.PermissionManager;
import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.plugin.PluginManager;
import me.internalizable.numdrassl.api.scheduler.Scheduler;
import me.internalizable.numdrassl.api.server.RegisteredServer;
import me.internalizable.numdrassl.command.CommandEventListener;
import me.internalizable.numdrassl.command.NumdrasslCommandManager;
import me.internalizable.numdrassl.command.builtin.AuthCommand;
import me.internalizable.numdrassl.command.builtin.HelpCommand;
import me.internalizable.numdrassl.command.builtin.NumdrasslCommand;
import me.internalizable.numdrassl.command.builtin.ServerCommand;
import me.internalizable.numdrassl.command.builtin.SessionsCommand;
import me.internalizable.numdrassl.command.builtin.StopCommand;
import me.internalizable.numdrassl.event.api.NumdrasslEventManager;
import me.internalizable.numdrassl.plugin.bridge.ApiEventBridge;
import me.internalizable.numdrassl.plugin.loader.NumdrasslPluginManager;
import me.internalizable.numdrassl.plugin.permission.NumdrasslPermissionManager;
import me.internalizable.numdrassl.plugin.player.NumdrasslPlayer;
import me.internalizable.numdrassl.plugin.server.NumdrasslRegisteredServer;
import me.internalizable.numdrassl.scheduler.NumdrasslScheduler;
import me.internalizable.numdrassl.server.ProxyCore;
import me.internalizable.numdrassl.session.ProxySession;

import javax.annotation.Nonnull;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final String VERSION = "1.0.0-SNAPSHOT";

    // Core reference
    private final ProxyCore core;

    // Internal managers
    private final NumdrasslEventManager eventManager;
    private final NumdrasslCommandManager commandManager;
    private final NumdrasslPluginManager pluginManager;
    private final NumdrasslScheduler scheduler;
    private final NumdrasslPermissionManager permissionManager;
    private final ApiEventBridge eventBridge;

    // Server registry
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
        this.dataDirectory = Paths.get("data");
        this.configDirectory = Paths.get("config");
        this.pluginManager = new NumdrasslPluginManager(this, Paths.get("plugins"));
        this.eventBridge = new ApiEventBridge(this);

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
            servers.put(backend.getName().toLowerCase(), server);
        }
    }

    // ==================== Initialization ====================

    /**
     * Initializes the proxy API layer.
     * Called by {@link ProxyCore} after networking is ready.
     */
    public void initialize() {
        registerBuiltinCommands();
        registerCommandListener();
        loadPlugins();
    }

    private void registerBuiltinCommands() {
        commandManager.register(this, new HelpCommand(commandManager));
        commandManager.register(this, new AuthCommand(core));
        commandManager.register(this, new SessionsCommand(core));
        commandManager.register(this, new StopCommand(core), "shutdown", "end");
        commandManager.register(this, new ServerCommand(), "srv");
        commandManager.register(this, new NumdrasslCommand(), "nd", "proxy");
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
    public PluginManager getPluginManager() {
        return pluginManager;
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

    // ==================== Player Management ====================

    @Override
    @Nonnull
    public Collection<Player> getAllPlayers() {
        List<Player> players = new ArrayList<>();
        for (ProxySession session : core.getSessionManager().getAllSessions()) {
            players.add(new NumdrasslPlayer(session, this));
        }
        return Collections.unmodifiableList(players);
    }

    @Override
    @Nonnull
    public Optional<Player> getPlayer(@Nonnull UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return core.getSessionManager().findByUuid(uuid)
            .map(session -> new NumdrasslPlayer(session, this));
    }

    @Override
    @Nonnull
    public Optional<Player> getPlayer(@Nonnull String username) {
        Objects.requireNonNull(username, "username");
        for (ProxySession session : core.getSessionManager().getAllSessions()) {
            if (username.equalsIgnoreCase(session.getUsername())) {
                return Optional.of(new NumdrasslPlayer(session, this));
            }
        }
        return Optional.empty();
    }

    @Override
    public int getPlayerCount() {
        return core.getSessionManager().getSessionCount();
    }

    // ==================== Server Management ====================

    @Override
    @Nonnull
    public Collection<RegisteredServer> getAllServers() {
        return Collections.unmodifiableCollection(servers.values());
    }

    @Override
    @Nonnull
    public Optional<RegisteredServer> getServer(@Nonnull String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(servers.get(name.toLowerCase()));
    }

    @Override
    @Nonnull
    public RegisteredServer registerServer(@Nonnull String name, @Nonnull InetSocketAddress address) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(address, "address");

        NumdrasslRegisteredServer server = new NumdrasslRegisteredServer(name, address);
        servers.put(name.toLowerCase(), server);
        return server;
    }

    @Override
    public boolean unregisterServer(@Nonnull String name) {
        Objects.requireNonNull(name, "name");
        return servers.remove(name.toLowerCase()) != null;
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
}

