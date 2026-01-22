# Numdrassl Plugin Development Guide

Numdrassl is a proxy server for Hytale that allows plugins to intercept, modify, and cancel packets flowing between clients and backend servers.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Plugin Structure](#plugin-structure)
3. [Event System](#event-system)
4. [Command System](#command-system)
5. [Working with Players](#working-with-players)
6. [Server Management](#server-management)
7. [Scheduler](#scheduler)
8. [Best Practices](#best-practices)

---

## Getting Started

### Dependencies

Plugins only depend on the `api` module. Add it to your `build.gradle.kts`:

```kotlin
plugins {
    java
}

repositories {
    mavenCentral()
    // Add Numdrassl repository when published
    maven("https://repo.numdrassl.com/releases")
}

dependencies {
    compileOnly("me.internalizable.numdrassl:api:1.0-SNAPSHOT")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
```

Or for `build.gradle` (Groovy):

```groovy
plugins {
    id 'java'
}

repositories {
    mavenCentral()
    maven { url 'https://repo.numdrassl.com/releases' }
}

dependencies {
    compileOnly 'me.internalizable.numdrassl:api:1.0-SNAPSHOT'
}
```

### Your First Plugin

Create a main class annotated with `@Plugin`:

```java
package com.example.myplugin;

import me.internalizable.numdrassl.api.ProxyServer;
import me.internalizable.numdrassl.api.event.Subscribe;
import me.internalizable.numdrassl.api.event.proxy.ProxyInitializeEvent;
import me.internalizable.numdrassl.api.event.proxy.ProxyShutdownEvent;
import me.internalizable.numdrassl.api.plugin.Inject;
import me.internalizable.numdrassl.api.plugin.Plugin;
import org.slf4j.Logger;

@Plugin(
    id = "my-plugin",
    name = "My Plugin",
    version = "1.0.0",
    authors = {"YourName"},
    description = "My first Numdrassl plugin"
)
public class MyPlugin {

    @Inject
    private ProxyServer server;
    
    @Inject
    private Logger logger;

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("My plugin has been enabled!");
        
        // Register event listeners
        server.getEventManager().register(this, new MyEventListener());
        
        // Register commands
        server.getCommandManager().register(this, "hello", (source, args) -> {
            source.sendMessage("Hello from my plugin!");
            return me.internalizable.numdrassl.api.command.CommandResult.success();
        });
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("My plugin is shutting down!");
    }
}
```

### Dependency Injection

Numdrassl supports automatic dependency injection via the `@Inject` annotation:

```java
@Plugin(id = "my-plugin", name = "My Plugin", version = "1.0.0")
public class MyPlugin {

    // Field injection
    @Inject private ProxyServer server;
    @Inject private Logger logger;
    @Inject private MessagingService messaging;
    @Inject private EventManager eventManager;
    @Inject private CommandManager commandManager;
    @Inject private Scheduler scheduler;
    
    @Inject @DataDirectory
    private Path dataDirectory;  // Plugin's data folder
}
```

Or use constructor injection:

```java
@Plugin(id = "my-plugin", name = "My Plugin", version = "1.0.0")
public class MyPlugin {

    private final ProxyServer server;
    private final Logger logger;

    @Inject
    public MyPlugin(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }
}
```

**Available Injectables:**

| Type                          | Description                        |
|-------------------------------|------------------------------------|
| `ProxyServer`                 | The proxy server instance          |
| `EventManager`                | Event registration and dispatch    |
| `CommandManager`              | Command registration               |
| `MessagingService`            | Cross-proxy messaging              |
| `Scheduler`                   | Task scheduling                    |
| `Logger`                      | Plugin-specific SLF4J logger       |
| `Path` with `@DataDirectory`  | Plugin's data directory            |

### Installing Your Plugin

1. Build your plugin JAR
2. Place it in the `plugins/` folder of your Numdrassl proxy
3. Start/restart the proxy

---

## Plugin Structure

### The @Plugin Annotation

```java
@Plugin(
    id = "my-plugin",           // Required: unique lowercase identifier
    name = "My Plugin",         // Display name
    version = "1.0.0",          // Version string
    authors = {"Author1"},      // List of authors
    description = "Description", // Plugin description
    dependencies = {"other-plugin"}, // Required dependencies
    softDependencies = {"optional-plugin"} // Optional dependencies
)
public class MyPlugin {
    // ...
}
```

### Plugin Lifecycle

1. **Load**: Plugin JAR is discovered and class is loaded
2. **Enable**: `ProxyInitializeEvent` is fired
3. **Running**: Plugin handles events and commands
4. **Disable**: `ProxyShutdownEvent` is fired

---

## Event System

The event system allows plugins to react to proxy events and modify behavior.

> **Important Annotation Distinction:**
> - `@Subscribe` (from `api.event`) - For local proxy events (player joins, commands, etc.)
> - `@MessageSubscribe` (from `api.messaging.annotation`) - For cross-proxy Redis messages
>
> This section covers `@Subscribe` for local events. See [Cluster Messaging](#cross-proxy-messaging) for `@MessageSubscribe`.

### Listening for Events

Use the `@Subscribe` annotation on methods:

```java
public class MyEventListener {

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        // Handle chat messages
        String message = event.getMessage();
        
        if (message.contains("badword")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("Watch your language!");
        }
    }
}
```

Register your listener:

```java
proxy.getEventManager().register(this, new MyEventListener());
```

### Event Priority

Control the order handlers are called:

```java
@Subscribe(priority = EventPriority.EARLY)
public void onChatEarly(PlayerChatEvent event) {
    // Called before NORMAL priority handlers
}

@Subscribe(priority = EventPriority.LATE)
public void onChatLate(PlayerChatEvent event) {
    // Called after NORMAL priority handlers
}
```

Priorities (in order): `FIRST` → `EARLY` → `NORMAL` → `LATE` → `LAST`

### Available Events

#### Connection Events
| Event | Description | Cancellable |
|-------|-------------|-------------|
| `PreLoginEvent` | Before authentication, only IP known | Yes |
| `LoginEvent` | During authentication, player info known | Yes |
| `PostLoginEvent` | After successful authentication | No |
| `DisconnectEvent` | Player disconnected | No |

#### Player Events
| Event | Description | Cancellable |
|-------|-------------|-------------|
| `PlayerChatEvent` | Player sends chat message | Yes |
| `PlayerCommandEvent` | Player executes command | Yes |
| `PlayerMoveEvent` | Player moves | Yes |
| `PlayerBlockPlaceEvent` | Player places block | Yes |
| `PlayerSlotChangeEvent` | Player changes hotbar slot | No |

#### Server Events
| Event | Description | Cancellable |
|-------|-------------|-------------|
| `ServerPreConnectEvent` | Before connecting to backend | Yes (can redirect) |
| `ServerConnectedEvent` | After connected to backend | No |
| `ServerDisconnectEvent` | Disconnected from backend | No |
| `ServerMessageEvent` | Server sends message to player | Yes |

#### Proxy Events
| Event | Description |
|-------|-------------|
| `ProxyInitializeEvent` | Proxy has started |
| `ProxyShutdownEvent` | Proxy is shutting down |
| `ProxyReloadEvent` | Proxy config reloaded |

#### Packet Events
| Event | Description | Cancellable |
|-------|-------------|-------------|
| `PacketEvent` | Any packet (low-level) | Yes |

### Modifying Events

Many events allow modification:

```java
@Subscribe
public void onChat(PlayerChatEvent event) {
    // Modify the message
    String newMessage = event.getMessage().toUpperCase();
    event.setMessage(newMessage);
}

@Subscribe
public void onServerConnect(ServerPreConnectEvent event) {
    // Redirect player to different server
    RegisteredServer lobby = Numdrassl.getProxy()
        .getServer("lobby")
        .orElse(null);
    
    if (lobby != null) {
        event.setResult(ServerPreConnectEvent.ServerResult.allowed(lobby));
    }
}
```

### Cancelling Events

```java
@Subscribe
public void onBlockPlace(PlayerBlockPlaceEvent event) {
    if (isProtectedArea(event.getBlockX(), event.getBlockY(), event.getBlockZ())) {
        event.setCancelled(true);
        event.getPlayer().sendMessage("You cannot build here!");
    }
}
```

---

## Command System

### Registering Commands

Simple command with lambda:

```java
proxy.getCommandManager().register(this, "ping", (source, args) -> {
    source.sendMessage("Pong!");
    return CommandResult.success();
});
```

Command with aliases:

```java
proxy.getCommandManager().register(this, "hub", (source, args) -> {
    source.asPlayer().ifPresent(player -> {
        player.transfer("lobby");
    });
    return CommandResult.success();
}, "lobby", "spawn");
```

### Command Class

For complex commands, implement the `Command` interface:

```java
public class ServerCommand implements Command {

    @Override
    public String getName() {
        return "server";
    }

    @Override
    public String getDescription() {
        return "Transfer to another server";
    }

    @Override
    public String getPermission() {
        return "numdrassl.command.server";
    }

    @Override
    public CommandResult execute(CommandSource source, String[] args) {
        if (args.length == 0) {
            source.sendMessage("Usage: /server <name>");
            return CommandResult.failure("No server specified");
        }

        Optional<Player> playerOpt = source.asPlayer();
        if (playerOpt.isEmpty()) {
            source.sendMessage("This command can only be used by players");
            return CommandResult.failure("Not a player");
        }

        Player player = playerOpt.get();
        String serverName = args[0];

        Optional<RegisteredServer> server = Numdrassl.getProxy().getServer(serverName);
        if (server.isEmpty()) {
            source.sendMessage("Server not found: " + serverName);
            return CommandResult.failure("Server not found");
        }

        player.transfer(serverName);
        return CommandResult.success();
    }
}

// Register it
proxy.getCommandManager().register(this, new ServerCommand());
```

### Command Sources

Commands can come from players or console:

```java
@Override
public CommandResult execute(CommandSource source, String[] args) {
    if (source.isConsole()) {
        // Console-only logic
    }
    
    source.asPlayer().ifPresent(player -> {
        // Player-only logic
    });
    
    return CommandResult.success();
}
```

---

## Working with Players

### Getting Players

```java
// All online players
Collection<Player> players = proxy.getAllPlayers();

// By username
Optional<Player> player = proxy.getPlayer("Steve");

// By UUID
Optional<Player> player = proxy.getPlayer(uuid);
```

### Player Methods

```java
Player player = ...;

// Basic info
String username = player.getUsername();
UUID uuid = player.getUniqueId();
InetSocketAddress address = player.getRemoteAddress();
long ping = player.getPing();

// Current server
Optional<RegisteredServer> server = player.getCurrentServer();

// Send message
player.sendMessage("Hello!");

// Transfer to another server
player.transfer("lobby");

// Disconnect
player.disconnect("Goodbye!");
```

---

## Server Management

### Getting Servers

```java
// By name
Optional<RegisteredServer> server = proxy.getServer("lobby");

// All servers
Collection<RegisteredServer> servers = proxy.getAllServers();
```

### Server Methods

```java
RegisteredServer server = ...;

// Info
String name = server.getName();
InetSocketAddress address = server.getAddress();

// Players on this server
Collection<Player> players = server.getPlayers();

// Ping the server
server.ping().thenAccept(result -> {
    if (result.isSuccess()) {
        int playerCount = result.getPlayerCount();
    }
});
```

---

## Scheduler

Run tasks asynchronously or with delays:

```java
Scheduler scheduler = proxy.getScheduler();

// Run async
scheduler.runAsync(this, () -> {
    // Heavy work here
});

// Run with delay
scheduler.runDelayed(this, () -> {
    // Runs after 5 seconds
}, 5, TimeUnit.SECONDS);

// Repeating task
ScheduledTask task = scheduler.runRepeating(this, () -> {
    // Runs every 30 seconds
}, 30, TimeUnit.SECONDS);

// Cancel a task
task.cancel();
```

---

## Best Practices

### 1. Always Check Optionals

```java
proxy.getPlayer("Steve").ifPresent(player -> {
    // Safe to use player
});
```

### 2. Handle Events Efficiently

Don't do heavy work in event handlers—use the scheduler:

```java
@Subscribe
public void onChat(PlayerChatEvent event) {
    // Quick check
    if (needsProcessing(event.getMessage())) {
        // Heavy work async
        proxy.getScheduler().runAsync(plugin, () -> {
            processMessage(event.getPlayer(), event.getMessage());
        });
    }
}
```

### 3. Unregister on Disable

```java
@Subscribe
public void onShutdown(ProxyShutdownEvent event) {
    proxy.getEventManager().unregisterAll(this);
    proxy.getCommandManager().unregisterAll(this);
}
```

### 4. Use Logging

```java
private static final Logger LOGGER = LoggerFactory.getLogger(MyPlugin.class);

LOGGER.info("Plugin enabled");
LOGGER.debug("Debug info: {}", value);
LOGGER.error("Something went wrong", exception);
```

### 5. Respect Event Results

```java
@Subscribe(priority = EventPriority.LATE)
public void onPreConnect(ServerPreConnectEvent event) {
    // Check if another plugin already handled this
    if (!event.getResult().isAllowed()) {
        return; // Don't override
    }
    
    // Your logic here
}
```

---

## Cross-Proxy Messaging

When running in cluster mode (multiple proxies with Redis), you can send messages between proxies.

> **Note:** Use `@MessageSubscribe` from `api.messaging.annotation` for cross-proxy messages.
> This is different from `@Subscribe` which is for local events.

### Programmatic API

```java
import me.internalizable.numdrassl.api.messaging.MessagingService;
import me.internalizable.numdrassl.api.messaging.channel.Channels;
import me.internalizable.numdrassl.api.messaging.message.BroadcastMessage;
import me.internalizable.numdrassl.api.messaging.channel.BroadcastType;

// Get the messaging service
MessagingService messaging = Numdrassl.getProxy().getMessagingService();

// Subscribe to cross-proxy chat
messaging.subscribe(Channels.CHAT, ChatMessage.class, (channel, msg) -> {
    logger.info("Chat from {}: {}", msg.sourceProxyId(), msg.message());
});

// Send a broadcast to all proxies
messaging.publish(Channels.BROADCAST, new BroadcastMessage(
    proxyId, Instant.now(), "Server restart in 5 minutes", BroadcastType.WARNING
));

// Plugin-specific messages
messaging.subscribePlugin("my-plugin", "events", MyEventData.class,
    (sourceProxyId, data) -> handleEvent(data));

messaging.publishPlugin("my-plugin", "events", new MyEventData("something happened"));
```

### Annotation-Based API

```java
import me.internalizable.numdrassl.api.event.Subscribe;  // For local events
import me.internalizable.numdrassl.api.event.proxy.ProxyInitializeEvent;
import me.internalizable.numdrassl.api.messaging.annotation.MessageSubscribe;  // For cross-proxy messages
import me.internalizable.numdrassl.api.messaging.channel.SystemChannel;
import me.internalizable.numdrassl.api.messaging.message.ChatMessage;
import me.internalizable.numdrassl.api.messaging.message.HeartbeatMessage;
import me.internalizable.numdrassl.api.plugin.Inject;
import me.internalizable.numdrassl.api.plugin.Plugin;

@Plugin(id = "my-plugin", name = "My Plugin", version = "1.0.0")
public class MyPlugin {

    @Inject
    private MessagingService messaging;

    @Subscribe  // Local event - registers message listeners on proxy init
    public void onInit(ProxyInitializeEvent event) {
        // Register this class for @MessageSubscribe methods
        messaging.registerListener(this);
    }

    // Cross-proxy chat (from other proxies)
    @MessageSubscribe(SystemChannel.CHAT)
    public void onCrossProxyChat(ChatMessage msg) {
        logger.info("Chat from proxy {}: {}", msg.sourceProxyId(), msg.message());
    }

    // Custom plugin channel - plugin ID inferred from @Plugin annotation
    @MessageSubscribe(channel = "game-events")
    public void onGameEvent(GameEventData data) {
        logger.info("Game event: {}", data);
    }

    // Include messages from this proxy too
    @MessageSubscribe(value = SystemChannel.HEARTBEAT, includeSelf = true)
    public void onAnyHeartbeat(HeartbeatMessage msg) {
        logger.info("Proxy {} is alive with {} players", 
            msg.sourceProxyId(), msg.playerCount());
    }
}
```

### Custom Message Types

Define your own message types for plugin-specific communication:

```java
// Your custom data class
public record ScoreUpdate(String playerName, int score, String gameMode) {}

// Publishing
messaging.publishPlugin("my-plugin", "scores", new ScoreUpdate("Steve", 100, "survival"));

// Subscribing
@MessageSubscribe(channel = "scores")
public void onScoreUpdate(ScoreUpdate update) {
    logger.info("{} scored {} in {}", 
        update.playerName(), update.score(), update.gameMode());
}
```

### System Channels

| Channel | Message Type | Purpose |
|---------|--------------|---------|
| `SystemChannel.HEARTBEAT` | `HeartbeatMessage` | Proxy liveness |
| `SystemChannel.CHAT` | `ChatMessage` | Cross-proxy chat |
| `SystemChannel.BROADCAST` | `BroadcastMessage` | Announcements |
| `SystemChannel.PLAYER_COUNT` | `PlayerCountMessage` | Player sync |
| `SystemChannel.TRANSFER` | `TransferMessage` | Cross-proxy transfers |

For more details, see [Cluster Messaging Architecture](CLUSTER_MESSAGING_ARCHITECTURE.md).

---

## Example: Complete Plugin

```java
package com.example.welcomeplugin;

import me.internalizable.numdrassl.api.Numdrassl;
import me.internalizable.numdrassl.api.ProxyServer;
import me.internalizable.numdrassl.api.event.Subscribe;
import me.internalizable.numdrassl.api.event.connection.PostLoginEvent;
import me.internalizable.numdrassl.api.event.proxy.ProxyInitializeEvent;
import me.internalizable.numdrassl.api.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Plugin(
    id = "welcome-plugin",
    name = "Welcome Plugin",
    version = "1.0.0",
    description = "Welcomes players when they join"
)
public class WelcomePlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(WelcomePlugin.class);

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        LOGGER.info("Welcome Plugin enabled!");
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        event.getPlayer().sendMessage("Welcome to the server, " + 
            event.getPlayer().getUsername() + "!");
        
        // Announce to all players
        Numdrassl.getProxy().getAllPlayers().forEach(player -> {
            if (!player.equals(event.getPlayer())) {
                player.sendMessage(event.getPlayer().getUsername() + " joined the game!");
            }
        });
    }
}
```

