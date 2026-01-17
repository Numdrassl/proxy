package me.internalizable.numdrassl.plugin;

import me.internalizable.numdrassl.api.command.CommandManager;
import me.internalizable.numdrassl.api.event.EventManager;
import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.plugin.PluginManager;
import me.internalizable.numdrassl.api.scheduler.Scheduler;
import me.internalizable.numdrassl.api.server.RegisteredServer;
import me.internalizable.numdrassl.command.NumdrasslCommandManager;
import me.internalizable.numdrassl.event.NumdrasslEventManager;
import me.internalizable.numdrassl.scheduler.NumdrasslScheduler;
import me.internalizable.numdrassl.server.ProxyServer;
import me.internalizable.numdrassl.session.ProxySession;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the API ProxyServer interface.
 * This bridges the internal proxy implementation with the public API.
 */
public class NumdrasslProxyServer implements me.internalizable.numdrassl.api.ProxyServer {

    private final ProxyServer internalServer;
    private final NumdrasslEventManager eventManager;
    private final NumdrasslCommandManager commandManager;
    private final NumdrasslPluginManager pluginManager;
    private final NumdrasslScheduler scheduler;
    private final Map<String, NumdrasslRegisteredServer> servers = new ConcurrentHashMap<>();
    private final Path dataDirectory;
    private final Path configDirectory;

    private final ApiEventBridge eventBridge;

    public NumdrasslProxyServer(ProxyServer internalServer) {
        this.internalServer = internalServer;
        this.eventManager = new NumdrasslEventManager();
        this.commandManager = new NumdrasslCommandManager();
        this.scheduler = new NumdrasslScheduler();
        this.dataDirectory = Paths.get("data");
        this.configDirectory = Paths.get("config");
        this.pluginManager = new NumdrasslPluginManager(this, Paths.get("plugins"));

        // Create the event bridge and register it with the internal event manager
        this.eventBridge = new ApiEventBridge(this);
        internalServer.getEventManager().registerListener(eventBridge);

        // Register backend servers from config
        if (internalServer.getConfig() != null) {
            for (var backend : internalServer.getConfig().getBackends()) {
                InetSocketAddress address = new InetSocketAddress(backend.getHost(), backend.getPort());
                NumdrasslRegisteredServer server = new NumdrasslRegisteredServer(backend.getName(), address);
                if (backend.isDefaultServer()) {
                    server.setDefault(true);
                }
                servers.put(backend.getName().toLowerCase(), server);
            }
        }
    }

    /**
     * Initialize the API server - load plugins, etc.
     */
    public void initialize() {
        // Register the command event listener to handle proxy commands
        var commandListener = new me.internalizable.numdrassl.command.CommandEventListener(commandManager);
        eventManager.register(this, commandListener);

        // Load and enable plugins
        pluginManager.loadPlugins();
        pluginManager.enablePlugins();
    }

    /**
     * Shutdown the API server - disable plugins, etc.
     */
    public void shutdownApi() {
        pluginManager.disablePlugins();
        scheduler.shutdown();
        eventManager.shutdown();
    }

    /**
     * Get the API event bridge for firing specific events.
     */
    public ApiEventBridge getEventBridge() {
        return eventBridge;
    }

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
    public Collection<Player> getAllPlayers() {
        List<Player> players = new ArrayList<>();
        for (ProxySession session : internalServer.getSessionManager().getAllSessions()) {
            players.add(new NumdrasslPlayer(session, this));
        }
        return Collections.unmodifiableList(players);
    }

    @Override
    @Nonnull
    public Optional<Player> getPlayer(@Nonnull UUID uuid) {
        ProxySession session = internalServer.getSessionManager().getSession(uuid);
        if (session != null) {
            return Optional.of(new NumdrasslPlayer(session, this));
        }
        return Optional.empty();
    }

    @Override
    @Nonnull
    public Optional<Player> getPlayer(@Nonnull String username) {
        for (ProxySession session : internalServer.getSessionManager().getAllSessions()) {
            if (session.getUsername() != null && session.getUsername().equalsIgnoreCase(username)) {
                return Optional.of(new NumdrasslPlayer(session, this));
            }
        }
        return Optional.empty();
    }

    @Override
    public int getPlayerCount() {
        return internalServer.getSessionManager().getSessionCount();
    }

    @Override
    @Nonnull
    public Collection<RegisteredServer> getAllServers() {
        return Collections.unmodifiableCollection(servers.values());
    }

    @Override
    @Nonnull
    public Optional<RegisteredServer> getServer(@Nonnull String name) {
        return Optional.ofNullable(servers.get(name.toLowerCase()));
    }

    @Override
    @Nonnull
    public RegisteredServer registerServer(@Nonnull String name, @Nonnull InetSocketAddress address) {
        NumdrasslRegisteredServer server = new NumdrasslRegisteredServer(name, address);
        servers.put(name.toLowerCase(), server);
        return server;
    }

    @Override
    public boolean unregisterServer(@Nonnull String name) {
        return servers.remove(name.toLowerCase()) != null;
    }

    @Override
    @Nonnull
    public InetSocketAddress getBoundAddress() {
        var config = internalServer.getConfig();
        return new InetSocketAddress(config.getBindAddress(), config.getBindPort());
    }

    @Override
    @Nonnull
    public InetSocketAddress getPublicAddress() {
        var config = internalServer.getConfig();
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
        return internalServer.isRunning();
    }

    @Override
    public void shutdown() {
        shutdownApi();
        internalServer.stop();
    }

    @Override
    @Nonnull
    public String getVersion() {
        return "1.0.0-SNAPSHOT";
    }

    /**
     * Get the internal proxy server instance.
     */
    public ProxyServer getInternalServer() {
        return internalServer;
    }

    /**
     * Get the internal event manager for direct event firing.
     */
    public NumdrasslEventManager getNumdrasslEventManager() {
        return eventManager;
    }
}

