# Numdrassl - Hytale QUIC Proxy Server

[![Discord](https://img.shields.io/badge/Discord-5865F2?logo=discord&logoColor=white)](https://discord.gg/54VFfuyUE8)
[![Build Status](https://github.com/Numdrassl/proxy/actions/workflows/build.yml/badge.svg)](https://github.com/Numdrassl/proxy/actions/workflows/build.yml)
[![Release](https://github.com/Numdrassl/proxy/actions/workflows/release.yml/badge.svg)](https://github.com/Numdrassl/proxy/actions/workflows/release.yml)
![Java](https://img.shields.io/badge/Java-21+-orange)
![License](https://img.shields.io/badge/License-Proprietary-red)

A BungeeCord/Velocity-style proxy server for Hytale, built using Netty QUIC. Allows you to connect multiple backend servers, intercept packets, create plugins, and manage players across your network.

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Backend Server Setup (Bridge)](#backend-server-setup-bridge)
- [Architecture](#architecture)
- [Plugin Development](#plugin-development)
- [API Reference](#api-reference)
- [Supported Packets](#supported-packets)
- [Building from Source](#building-from-source)
- [Console Commands](#console-commands)
- [Troubleshooting](#troubleshooting)
- [CI/CD](#cicd)
- [References](#references)

---

## Features

### Core Functionality
- **QUIC Protocol Support** - Native QUIC transport with BBR congestion control for low-latency connections
- **Multi-Backend Support** - Route players to different backend servers (lobby, minigames, etc.)
- **Player Transfer** - Seamless server switching via `/server` command
- **Packet Interception** - Decode, inspect, modify, or cancel packets in transit

### Security & Authentication
- **Secret-Based Auth** - Secure proxy-to-backend authentication using HMAC-signed referrals
- **OAuth Device Flow** - Authenticate proxy with your Hytale account
- **HAProxy PROXY Protocol** - Support for DDoS protection services to preserve client IPs

### Plugin System
- **Event-Driven API** - Create plugins with event listeners and commands
- **Permissions System** - Built-in permission management with provider support
- **Scheduler API** - Run tasks synchronously or asynchronously

### Cluster Mode (Multi-Proxy)
- **Redis-Backed Pub/Sub** - Multi-proxy deployments with real-time synchronization
- **Cross-Proxy Messaging** - Send messages to players on any proxy in the cluster
- **Global Player Management** - Track players across all proxies
- **Load Balancing** - Route players to the least loaded proxy

### Monitoring & Profiling
- **Built-in Metrics** - HTTP endpoints for Prometheus, real-time dashboards
- **Performance Tracking** - Packet throughput, response times, memory usage
- **Historical Data** - Peak values, period averages, trend analysis

---

## Requirements

| Requirement | Version | Notes |
|-------------|---------|-------|
| **Java** | 21+ | Tested with Java 21 and 25 |
| **Hytale Server** | Latest | Backend servers for players to connect to |
| **Operating System** | Linux/Windows/macOS | Linux recommended for production |

### Optional Requirements

| Requirement | When Needed |
|-------------|-------------|
| **Redis** | Cluster mode (multi-proxy deployments) |
| **TLS Certificates** | Auto-generated on first run, or provide your own |

---

## Quick Start

### 1. Download the Latest Release

Download the following files from the [Releases page](https://github.com/Numdrassl/proxy/releases):

| File | Purpose |
|------|---------|
| `proxy-*.jar` | The main proxy server |
| `bridge-*.jar` | Backend server plugin (goes in `mods/` folder) |
| `bridge-packets-*.jar` | Packet definitions (goes in `earlyplugins/` folder) |

### 2. Set Up the Proxy Server

```bash
# Create a directory for the proxy
mkdir numdrassl-proxy
cd numdrassl-proxy

# Run the proxy (first run generates config)
java -jar proxy-*.jar
```

### 3. Authenticate with Hytale

On first run, use the `auth login` command in the console:

```
> auth login
===========================================
  To authenticate, visit:
  https://accounts.hytale.com/device
  
  Enter code: XXXX-XXXX
===========================================
```

### 4. Set Up Your Backend Server(s)

See [Backend Server Setup](#backend-server-setup-bridge) for detailed instructions.

### 5. Configure and Connect

1. Edit `config/proxy.yml` to set your backend servers
2. Ensure the `proxySecret` matches your Bridge plugin config
3. Start the proxy and backend servers
4. Connect your Hytale client to `your-server-ip:24322`

---

## Configuration

### Proxy Configuration (`config/proxy.yml`)

```yaml
# Numdrassl Proxy Configuration

# ==================== Network Configuration ====================

# Address to bind the proxy server to
bindAddress: "0.0.0.0"
# Port to listen on (default: 24322)
bindPort: 24322

# Public address for player transfers (sent in ClientReferral packets)
# Set this to your server's public domain/IP if behind NAT
publicAddress: "play.myserver.com"
publicPort: 24322

# ==================== TLS Configuration ====================

# TLS certificates (auto-generated if missing)
certificatePath: "certs/server.crt"
privateKeyPath: "certs/server.key"

# ==================== Connection Limits ====================

# Maximum concurrent connections
maxConnections: 1000
# Connection timeout in seconds
connectionTimeoutSeconds: 30

# ==================== Debug Options ====================

# Enable verbose logging for debugging
debugMode: false
# Passthrough mode (forward packets without inspection)
passthroughMode: false

# ==================== Backend Authentication ====================

# Shared secret for backend authentication (HMAC signing)
# Must match the secret in your Bridge plugin config
# Auto-generated on first run if not set
proxySecret: "your-shared-secret-here"

# ==================== Backend Servers ====================

# List of backend servers players can connect to
backends:
  - name: "lobby"
    host: "127.0.0.1"
    port: 5520
    defaultServer: true
  - name: "survival"
    host: "192.168.1.100"
    port: 5520
    defaultServer: false
  - name: "minigames"
    host: "192.168.1.101"
    port: 5520
    defaultServer: false

# ==================== Metrics Configuration ====================

# Enable metrics collection and HTTP endpoint
metricsEnabled: true
# Port for metrics HTTP server (Prometheus scrape endpoint)
metricsPort: 9090
# Interval for logging metrics summary (0 to disable)
metricsLogIntervalSeconds: 60

# ==================== Cluster Configuration ====================

# Enable cluster mode for multi-proxy deployments
clusterEnabled: false
# Unique identifier for this proxy instance (auto-generated if null)
proxyId: null
# Region identifier for load balancing (e.g., "eu-west", "us-east")
proxyRegion: "default"

# ==================== Redis Configuration ====================

# Redis connection settings (only used when clusterEnabled: true)
redisHost: "localhost"
redisPort: 6379
redisPassword: null
redisSsl: false
redisDatabase: 0

# ==================== Proxy Protocol (HAProxy) ====================

# Enable HAProxy PROXY protocol support for DDoS protection services
proxyProtocol:
  enabled: false
  required: true
  headerTimeoutSeconds: 5
  trustedProxies: []
```

### Environment Variables

The proxy supports the following environment variables which override config values:

| Variable | Description |
|----------|-------------|
| `NUMDRASSL_SECRET` | Overrides `proxySecret` from config |

---

## Backend Server Setup (Bridge)

The Bridge plugin authenticates proxy connections using HMAC-signed referral data. This allows the backend to run in `--auth-mode insecure` while validating that connections come from your trusted proxy.

### Required Files

Download from the [Releases page](https://github.com/Numdrassl/proxy/releases):

| File | Destination | Purpose |
|------|-------------|---------|
| `bridge-*.jar` | `mods/` | Main Bridge plugin |
| `bridge-packets-*.jar` | `earlyplugins/` | Packet definitions (required) |

### 1. Install the Bridge Components

```bash
# Copy Bridge plugin to mods folder
cp bridge-*.jar /path/to/hytale-server/mods/

# Copy Bridge packets to earlyplugins folder
cp bridge-packets-*.jar /path/to/hytale-server/earlyplugins/
```

### 2. Start Backend with Required Flags

```bash
java -jar HytaleServer.jar --auth-mode insecure --transport QUIC --accept-early-plugins
```

> ⚠️ **Important**: The `--accept-early-plugins` flag is required for the bridge-packets module to load correctly.

### 3. Configure the Bridge

On first run, the Bridge creates `plugins/Bridge/config.json`:

```json
{
  "SecretKey": "your-shared-secret-here",
  "ServerName": "lobby"
}
```

> ⚠️ **Critical**: The `SecretKey` must match the `proxySecret` in your proxy's `config/proxy.yml`!

### 4. Security: Firewall Your Backend

Block direct connections to your backend server. Only allow connections from your proxy:

```bash
# UFW (Ubuntu/Debian)
sudo ufw allow from <proxy-ip> to any port 5520 proto udp
sudo ufw deny 5520/udp

# iptables
iptables -A INPUT -p udp --dport 5520 -s <proxy-ip> -j ACCEPT
iptables -A INPUT -p udp --dport 5520 -j DROP
```

### Bridge Environment Variables

| Variable | Description |
|----------|-------------|
| `NUMDRASSL_SERVERNAME` | Overrides `ServerName` from config |
| `NUMDRASSL_SECRET` | Overrides `SecretKey` from config |

### Complete Backend Startup Command

```bash
java -jar HytaleServer.jar \
  --auth-mode insecure \
  --transport QUIC \
  --accept-early-plugins
```

---

## Architecture

### Single Proxy Mode

```
┌─────────────┐     QUIC/TLS      ┌─────────────┐     QUIC/TLS      ┌─────────────────┐
│   Hytale    │ ───────────────── │  Numdrassl  │ ───────────────── │ Backend Server  │
│   Client    │                   │    Proxy    │                   │ (lobby, game1)  │
└─────────────┘                   └─────────────┘                   └─────────────────┘
       │                                 │                                   │
       │  1. Connect with identity       │                                   │
       │  token (Hytale auth)            │                                   │
       │ ─────────────────────────────── │                                   │
       │                                 │  2. Forward Connect with          │
       │                                 │  signed referral (HMAC secret)    │
       │                                 │ ─────────────────────────────────│
       │                                 │                                   │
       │                                 │  3. Backend validates secret      │
       │                                 │  and accepts connection           │
       │                                 │ <────────────────────────────────│
       │                                 │                                   │
       │  4. Full packet proxying        │  (bidirectional)                  │
       │ <─────────────────────────────> │ <───────────────────────────────>│
```

### Cluster Mode (Multi-Proxy)

```
                                    ┌─────────────────┐
                                    │      Redis      │
                                    │  (Pub/Sub Hub)  │
                                    └────────┬────────┘
                                             │
            ┌────────────────────────────────┼────────────────────────────────┐
            │                                │                                │
            ▼                                ▼                                ▼
   ┌─────────────────┐              ┌─────────────────┐              ┌─────────────────┐
   │  Proxy EU-1     │              │  Proxy US-1     │              │  Proxy AS-1     │
   │  (eu-west)      │◄────────────►│  (us-east)      │◄────────────►│  (ap-southeast) │
   └────────┬────────┘              └────────┬────────┘              └────────┬────────┘
            │                                │                                │
    ┌───────┴───────┐               ┌───────┴───────┐               ┌───────┴───────┐
    │               │               │               │               │               │
    ▼               ▼               ▼               ▼               ▼               ▼
┌───────┐       ┌───────┐       ┌───────┐       ┌───────┐       ┌───────┐       ┌───────┐
│Lobby  │       │Game1  │       │Lobby  │       │Game2  │       │Lobby  │       │Game3  │
│Server │       │Server │       │Server │       │Server │       │Server │       │Server │
└───────┘       └───────┘       └───────┘       └───────┘       └───────┘       └───────┘

Cross-Proxy Communication:
  • Heartbeats: Proxy liveness monitoring
  • Chat: Global chat messages
  • Broadcasts: Server-wide announcements
  • Player Count: Synchronized player counts
  • Transfers: Cross-proxy player transfers
  • Plugin Messages: Custom plugin data
```

### Authentication Flow

1. **Client → Proxy**: Player connects with Hytale identity token
2. **Proxy authenticates**: Validates token with Hytale session service
3. **Proxy → Backend**: Forwards connection with HMAC-signed referral data
4. **Backend validates**: Bridge plugin verifies the shared secret
5. **Connection established**: Packets flow bidirectionally through proxy

---


## Project Structure

```
Numdrassl/
├── api/                    # Plugin API (plugins depend on this)
│   └── src/main/java/
│       └── me/internalizable/numdrassl/api/
│           ├── Numdrassl.java          # Main entry point
│           ├── ProxyServer.java        # Server interface
│           ├── command/                # Command system
│           ├── event/                  # Event system (@Subscribe)
│           ├── messaging/              # Cross-proxy messaging
│           │   ├── MessagingService.java
│           │   ├── Subscription.java
│           │   ├── ChannelMessage.java
│           │   ├── annotation/         # @MessageSubscribe, @TypeAdapter
│           │   ├── channel/            # MessageChannel, Channels, SystemChannel
│           │   ├── handler/            # MessageHandler, PluginMessageHandler
│           │   └── message/            # Message types (Chat, Heartbeat, etc.)
│           ├── player/                 # Player API
│           ├── plugin/                 # Plugin annotations
│           ├── scheduler/              # Task scheduler
│           └── server/                 # Backend server API
│
├── proxy/                  # Proxy implementation
│   └── src/main/java/
│       ├── com/hypixel/hytale/protocol/  # Hytale protocol
│       │   ├── packets/auth/             # Auth packets
│       │   ├── packets/connection/       # Connect/Disconnect
│       │   └── packets/interface_/       # Chat, ServerMessage
│       └── me/internalizable/numdrassl/
│           ├── Main.java               # Entry point
│           ├── auth/                   # OAuth & session management
│           ├── cluster/                # Cluster management
│           │   ├── ClusterManager.java
│           │   ├── ProxyRegistry.java
│           │   └── handler/            # Message handlers
│           ├── command/                # Command handling
│           ├── config/                 # Configuration
│           ├── event/                  # Event dispatching
│           ├── messaging/              # Messaging implementation
│           │   ├── redis/              # Redis pub/sub
│           │   ├── local/              # Local (non-cluster) messaging
│           │   ├── codec/              # JSON serialization
│           │   └── subscription/       # Subscription management
│           ├── pipeline/               # Netty handlers
│           ├── plugin/                 # Plugin loading
│           ├── server/                 # Backend connections
│           └── session/                # Player sessions
│
├── bridge/                 # Backend server plugin
│   └── src/main/java/
│       └── me/internalizable/numdrassl/
│           ├── Bridge.java            # Main plugin class
│           └── BridgeConfig.java      # Configuration
│
├── common/                 # Shared utilities
│   └── src/main/java/
│       └── me/internalizable/numdrassl/common/
│           ├── SecretMessageUtil.java # HMAC signing
│           └── RandomUtil.java        # Random generation
│
└── docs/                   # Documentation
    ├── PLUGIN_DEVELOPMENT.md
    ├── EVENT_ARCHITECTURE.md
    └── AUTHENTICATION_ARCHITECTURE.md
```

---

## Building from Source

### 1. Build the Project

```bash
./gradlew build
```

### 2. Run the Proxy

```bash
# Using Gradle
./gradlew :proxy:run

# Or using the JAR directly
java -jar proxy/build/libs/proxy-*.jar
```

### Build Artifacts

After building, the following JARs are created:

| File | Location |
|------|----------|
| Proxy JAR | `proxy/build/libs/proxy-*.jar` |
| Bridge Plugin | `bridge/build/libs/bridge-*.jar` |
| Bridge Packets | `bridge-packets/build/libs/bridge-packets-*.jar` |
| API JAR | `api/build/libs/api-*.jar` |

---

## Plugin Development

Plugins allow you to extend the proxy with custom functionality.

### Dependency Setup

#### Maven

```xml
<dependency>
    <groupId>me.internalizable.numdrassl</groupId>
    <artifactId>numdrassl-api</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

#### Gradle (Kotlin DSL)

```kotlin
plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("me.internalizable.numdrassl:numdrassl-api:1.0.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
```

#### Gradle (Groovy)

```groovy
plugins {
    id 'java'
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly 'me.internalizable.numdrassl:numdrassl-api:1.0.0'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

> **Note:** For snapshot versions (development), add the Sonatype snapshots repository:
> ```kotlin
> repositories {
>     maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
> }
> ```

### Plugin Structure

A basic plugin looks like this:

```java
package com.example.myplugin;

import me.internalizable.numdrassl.api.ProxyServer;
import me.internalizable.numdrassl.api.event.Subscribe;
import me.internalizable.numdrassl.api.event.proxy.ProxyInitializeEvent;
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
        logger.info("My plugin loaded! {} players online.", server.getPlayerCount());
    }
}
```

### Event Listeners

```java
import me.internalizable.numdrassl.api.event.Subscribe;
import me.internalizable.numdrassl.api.event.EventPriority;
import me.internalizable.numdrassl.api.event.player.PlayerChatEvent;
import me.internalizable.numdrassl.api.event.connection.LoginEvent;
import me.internalizable.numdrassl.api.event.connection.DisconnectEvent;

public class MyListener {

    @Subscribe
    public void onLogin(LoginEvent event) {
        System.out.println("Player connecting: " + event.getUsername());
        
        // Cancel with reason
        // event.setResult(LoginEvent.Result.denied("Server is full!"));
    }

    @Subscribe(priority = EventPriority.HIGH)
    public void onChat(PlayerChatEvent event) {
        String message = event.getMessage();
        
        // Block certain words
        if (message.contains("badword")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("Watch your language!");
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        System.out.println("Player left: " + event.getUsername());
    }
}
```

### Commands

```java
import me.internalizable.numdrassl.api.command.*;

// Register in your plugin's init method:
proxy.getCommandManager().register(this, "ping", (source, args) -> {
    source.sendMessage("Pong!");
    return CommandResult.success();
});

// With arguments
proxy.getCommandManager().register(this, "server", (source, args) -> {
    if (args.length == 0) {
        source.sendMessage("Usage: /server <name>");
        return CommandResult.error("Missing server name");
    }
    
    if (source instanceof Player player) {
        player.transfer(args[0]);
        return CommandResult.success();
    }
    
    return CommandResult.error("Only players can use this command");
});
```

### Working with Players

```java
import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.Numdrassl;

// Get all online players
for (Player player : Numdrassl.getProxy().getPlayers()) {
    player.sendMessage("Hello, " + player.getUsername() + "!");
}

// Get player by UUID
Player player = Numdrassl.getProxy().getPlayer(uuid);
if (player != null) {
    player.transfer("lobby");
    player.disconnect("Kicked!");
}
```

### Available Events

Events use the `@Subscribe` annotation from `me.internalizable.numdrassl.api.event`.

| Event | Description |
|-------|-------------|
| `ProxyInitializeEvent` | Proxy has started |
| `ProxyShutdownEvent` | Proxy is shutting down |
| `LoginEvent` | Player is connecting (cancellable) |
| `PostLoginEvent` | Player has fully connected |
| `DisconnectEvent` | Player has disconnected |
| `PlayerChatEvent` | Player sent a chat message (cancellable) |
| `PlayerCommandEvent` | Player executed a command (cancellable) |
| `ServerConnectEvent` | Player connecting to backend (cancellable) |
| `ServerConnectedEvent` | Player connected to backend |
| `ServerDisconnectEvent` | Player disconnected from backend |
| `ProxyJoinClusterEvent` | A proxy joined the cluster (cluster mode) |
| `ProxyLeaveClusterEvent` | A proxy left the cluster (cluster mode) |

### Cross-Proxy Messaging

Cross-proxy messages use the `@MessageSubscribe` annotation from `me.internalizable.numdrassl.api.messaging.annotation`.

| System Channel | Message Type | Description |
|----------------|--------------|-------------|
| `HEARTBEAT` | `HeartbeatMessage` | Proxy liveness pings |
| `CHAT` | `ChatMessage` | Cross-proxy chat |
| `BROADCAST` | `BroadcastMessage` | Server-wide announcements |
| `PLAYER_COUNT` | `PlayerCountMessage` | Player count updates |
| `TRANSFER` | `TransferMessage` | Cross-proxy transfers |
| `PLUGIN` | `PluginMessage` | Custom plugin messages |

### Custom Packets (Proxy ↔ Backend Communication)

You can create custom packets for communication between the proxy and backend servers. This is useful for features like health checks, synchronization, or custom game logic.

#### Packet Structure

Custom packets must implement the Hytale `Packet` interface:

```java
package me.internalizable.numdrassl.packet;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.io.ValidationResult;
import io.netty.buffer.ByteBuf;

import javax.annotation.Nonnull;

public class ProxyPing implements Packet {

    public static final int PACKET_ID = 998;  // Choose a unique ID (avoid Hytale's reserved IDs)
    
    public long nonce;
    public long timestamp;

    public ProxyPing() {}

    public ProxyPing(long nonce, long timestamp) {
        this.nonce = nonce;
        this.timestamp = timestamp;
    }

    @Override
    public int getId() {
        return PACKET_ID;
    }

    @Override
    public void serialize(@Nonnull ByteBuf buf) {
        buf.writeLong(nonce);
        buf.writeLong(timestamp);
    }

    @Override
    public int computeSize() {
        return 16;  // 2 longs = 16 bytes
    }

    public static ProxyPing deserialize(@Nonnull ByteBuf buf, int offset) {
        ProxyPing ping = new ProxyPing();
        ping.nonce = buf.getLong(offset);
        ping.timestamp = buf.getLong(offset + Long.BYTES);
        return ping;
    }

    public static ValidationResult validateStructure(@Nonnull ByteBuf buf, int offset) {
        int readable = buf.readableBytes() - offset;
        if (readable < 16) {
            return ValidationResult.error("ProxyPing too small: " + readable + " bytes");
        }
        return ValidationResult.OK;
    }
}
```

#### Registering Custom Packets

Register your packet in the `PacketRegistry` so it can be serialized/deserialized:

```java
// In your plugin initialization
PacketRegistry.registerCustomPacket(
    ProxyPing.PACKET_ID,
    "ProxyPing",
    ProxyPing.class,
    16,  // Fixed size (0 for variable)
    16,  // Max size
    false,  // Compressed
    ProxyPing::validateStructure,
    ProxyPing::deserialize
);
```

#### Built-in Custom Packets

Numdrassl includes these custom packets for proxy-backend communication:

| Packet | ID | Direction | Purpose |
|--------|-----|-----------|---------|
| `ProxyPing` | 998 | Proxy → Backend | Health check / latency measurement |
| `ProxyPong` | 999 | Backend → Proxy | Response to ProxyPing |

> **Note:** The `bridge-packets` module (placed in `earlyplugins/`) enables custom packet registration on the Hytale server by patching the `PacketRegistry` at startup.

### Installing Plugins

Place your plugin JAR in the `plugins/` directory and restart the proxy.

---

## API Reference

### Numdrassl (Entry Point)

```java
// Get the proxy server instance
ProxyServer proxy = Numdrassl.getProxy();
```

### ProxyServer

```java
// Players
Collection<Player> players = proxy.getPlayers();
Player player = proxy.getPlayer(uuid);
int count = proxy.getPlayerCount();

// Servers
Collection<RegisteredServer> servers = proxy.getServers();
Optional<RegisteredServer> server = proxy.getServer("lobby");

// Managers
EventManager events = proxy.getEventManager();
CommandManager commands = proxy.getCommandManager();
Scheduler scheduler = proxy.getScheduler();

// Cluster (when clusterEnabled: true)
ClusterManager cluster = proxy.getClusterManager();
MessagingService messaging = proxy.getMessagingService();
int globalCount = proxy.getGlobalPlayerCount();
```

### ClusterManager

```java
// Check if clustering is enabled
if (cluster.isClusterMode()) {
    // Get all online proxies
    Collection<ProxyInfo> proxies = cluster.getOnlineProxies();
    
    // Find least loaded proxy in a region
    Optional<ProxyInfo> best = cluster.getLeastLoadedProxy("eu-west");
    
    // Check if player is online anywhere
    boolean online = cluster.isPlayerOnline(playerUuid);
}
```

### MessagingService

The messaging service enables cross-proxy communication via Redis pub/sub.

**Important:** For cross-proxy messaging, use `@MessageSubscribe` (from `api.messaging.annotation`).  
For local proxy events, use `@Subscribe` (from `api.event`).

```java
import me.internalizable.numdrassl.api.messaging.MessagingService;
import me.internalizable.numdrassl.api.messaging.channel.Channels;
import me.internalizable.numdrassl.api.messaging.message.ChatMessage;
import me.internalizable.numdrassl.api.messaging.message.BroadcastMessage;
import me.internalizable.numdrassl.api.messaging.channel.BroadcastType;

MessagingService messaging = proxy.getMessagingService();

// Subscribe to cross-proxy chat messages
messaging.subscribe(Channels.CHAT, ChatMessage.class, (channel, msg) -> {
    logger.info("Chat from proxy {}: {}", msg.sourceProxyId(), msg.message());
});

// Send broadcast to all proxies
messaging.publish(Channels.BROADCAST, new BroadcastMessage(
    proxyId, Instant.now(), "Server restarting in 5 minutes!", BroadcastType.WARNING
));

// Plugin-specific messages
messaging.subscribePlugin("my-plugin", "scores", ScoreData.class, (sourceProxyId, data) -> {
    logger.info("Score update from {}: {}", sourceProxyId, data);
});

messaging.publishPlugin("my-plugin", "scores", new ScoreData("Steve", 100));
```

#### Annotation-Based Messaging

```java
import me.internalizable.numdrassl.api.messaging.MessagingService;
import me.internalizable.numdrassl.api.messaging.annotation.MessageSubscribe;
import me.internalizable.numdrassl.api.messaging.channel.SystemChannel;
import me.internalizable.numdrassl.api.plugin.Inject;
import me.internalizable.numdrassl.api.plugin.Plugin;

@Plugin(id = "my-plugin", name = "My Plugin", version = "1.0.0")
public class MyPlugin {

    @Inject
    private MessagingService messaging;

    // Plugin channel subscription - plugin ID inferred from @Plugin
    @MessageSubscribe(channel = "scores")
    public void onScoreUpdate(ScoreData data) {
        logger.info("Score: {} - {}", data.playerName(), data.score());
    }

    // System channel subscription
    @MessageSubscribe(SystemChannel.CHAT)
    public void onCrossProxyChat(ChatMessage msg) {
        logger.info("Chat from {}: {}", msg.sourceProxyId(), msg.message());
    }

    // Include messages from self
    @MessageSubscribe(value = SystemChannel.HEARTBEAT, includeSelf = true)
    public void onHeartbeat(HeartbeatMessage msg) {
        logger.info("Proxy {} is alive", msg.sourceProxyId());
    }
    
    // Publish to all proxies
    public void broadcastScore(String player, int score) {
        messaging.publishPlugin("my-plugin", "scores", new ScoreData(player, score));
    }
}
```

### Player

```java
player.getUuid();
player.getUsername();
player.getCurrentServer();
player.sendMessage("Hello!");
player.transfer("game1");
player.disconnect("Goodbye!");
```

---

## Supported Packets

The proxy only decodes packets essential for proxy operation. Unknown packets are forwarded as raw bytes.

| ID | Packet | Direction | Description |
|----|--------|-----------|-------------|
| 0 | Connect | C→S | Initial connection with identity |
| 1 | Disconnect | Both | Disconnection with reason |
| 2 | Ping | C→S | Keepalive ping |
| 3 | Pong | S→C | Keepalive pong |
| 10 | Status | S→C | Server status |
| 11 | AuthGrant | S→C | Authorization grant |
| 12 | AuthToken | C→S | Authorization token |
| 13 | ServerAuthToken | S→C | Server auth token |
| 14 | ConnectAccept | S→C | Connection accepted |
| 18 | ClientReferral | S→C | Server transfer |
| 210 | ServerMessage | S→C | Server chat message |
| 211 | ChatMessage | C→S | Player chat message |

---

## Building

```bash
# Build everything
./gradlew build

# Build specific modules
./gradlew :proxy:build
./gradlew :api:build
./gradlew :bridge:build

# Run the proxy
./gradlew :proxy:run

# Create distribution archives
./gradlew :proxy:distZip
./gradlew :proxy:distTar
```

Output locations:
- Proxy JAR: `proxy/build/libs/proxy-1.0-SNAPSHOT.jar`
- API JAR: `api/build/libs/api-1.0-SNAPSHOT.jar`
- Bridge JAR: `bridge/build/libs/bridge-1.0-SNAPSHOT.jar`

---

## Console Commands

| Command      | Description                            |
|--------------|----------------------------------------|
| `auth login` | Start OAuth device flow authentication |
| `auth status` | Show current authentication status |
| `auth logout` | Clear stored credentials |
| `sessions` | List all connected sessions |
| `metrics` | Show current performance metrics |
| `metrics history` | Show historical averages |
| `metrics peaks` | Show all-time peak values |
| `metrics memory` | Show detailed memory statistics |
| `metrics gc` | Trigger garbage collection |
| `metrics report` | Generate shareable report |
| `stop` | Gracefully shut down the proxy |
| `help` | Show available commands |
| `server`     | List all registered backend servers    |

---

## Monitoring & Profiling

Numdrassl includes a built-in profiling system with HTTP endpoints:

| Endpoint | URL | Description |
|----------|-----|-------------|
| Dashboard | http://localhost:9090/stats | Real-time HTML dashboard |
| History | http://localhost:9090/history | Historical data & peaks |
| Prometheus | http://localhost:9090/metrics | Prometheus scrape endpoint |
| Report | http://localhost:9090/report | Shareable text report |
| Health | http://localhost:9090/health | Health check (JSON) |

### Key Metrics

- **Sessions**: Active connections, accepted/closed counts
- **Throughput**: Real-time packets/sec and bytes/sec
- **Response Times**: Average response time, hanging request detection
- **Historical Data**: Peak values, period averages (5min, 30min, 1hr)
- **Memory**: JVM heap usage, GC stats
- **Errors**: Auth failures, backend connection failures

### Configuration

```yaml
metricsEnabled: true
metricsPort: 9090
metricsLogIntervalSeconds: 60
```

See [Profiling Guide](docs/PROFILING.md) for detailed documentation.

---

## Troubleshooting

### "Proxy not authenticated"

Run `auth login` and complete the device code flow.

### "Invalid player info message (is your proxy secret valid?)"

The `proxySecret` in your proxy config doesn't match the `SecretKey` in your Bridge config.

### "Connection timed out" to backend

1. Ensure the backend server is running
2. Check firewall rules allow the proxy IP
3. Verify the backend address/port in config

### "Cannot direct join numdrassl backend"

Players must connect through the proxy. Block direct connections with firewall rules.

### Client shows "unexpected packet"

The backend may not have the Bridge plugin installed, or it's not running in `--auth-mode insecure`.

### Debug Mode

Enable `debugMode: true` in config for verbose packet logging.

---

## Documentation

- [API JavaDocs](https://numdrassl.github.io/proxy/) - Online API documentation
- [Plugin Development Guide](docs/PLUGIN_DEVELOPMENT.md) - Complete plugin development reference
- [Event Architecture](docs/EVENT_ARCHITECTURE.md) - Internal event system details
- [Cluster & Messaging](docs/CLUSTER_MESSAGING_ARCHITECTURE.md) - Multi-proxy cluster and Redis messaging
- [Authentication Architecture](docs/AUTHENTICATION_ARCHITECTURE.md) - Auth flow documentation
- [Profiling Guide](docs/PROFILING.md) - Monitoring, metrics, and performance troubleshooting

---

## CI/CD

This project uses GitHub Actions for continuous integration and releases.

### Branches

| Branch | Purpose | Release Type |
|--------|---------|--------------|
| `main` | Latest stable release | 🟢 Stable |
| `dev` | Development builds | 🟡 Pre-release |

### Versioning

Releases are versioned based on the **primary Hytale Server version** they're built for:

```
{primaryHytaleVersion}[-dev]-build.{buildNumber}
```

**Examples:**
- `2026.01.17-4b0f30090-build.42` - Stable release from `main`
- `2026.01.17-4b0f30090-dev-build.43` - Dev release from `dev`

### Workflows

| Workflow | Trigger | Description |
|----------|---------|-------------|
| **Build** | All pushes & PRs to `main`/`dev` | Builds and tests the project |
| **Release** | Push tags (`v*` or Hytale version) | Creates GitHub releases with artifacts |
| **Docs** | Push to `main` or release published | Publishes API JavaDocs to GitHub Pages |

### Creating a Release

**Automatic releases** are created on every push to `main` or `dev`.

**Manual tagged releases:**
```bash
# Stable release
git tag 2026.01.17-4b0f30090
git push origin 2026.01.17-4b0f30090

# Or with v prefix
git tag v1.0.0
git push origin v1.0.0
```

### Updating Hytale Server Compatibility

When testing with Hytale server versions, update `gradle.properties`:

```properties
# Single version
hytaleServerVersions=2026.01.17-4b0f30090

# Multiple compatible versions (first is primary, used for tagging)
hytaleServerVersions=2026.01.17-4b0f30090,2026.01.15-abc12345,2026.01.10-def67890
```

The **first version** in the list is the primary version used for release tagging.
All compatible versions are listed in the release notes.

### Artifacts

Each release includes:
- `proxy-*.jar` - Main proxy server
- `api-*.jar` - Plugin API for developers
- `bridge-*.jar` - Backend server plugin

---

## References

This project draws inspiration from established Minecraft proxy servers and networking libraries:

### Proxy Servers
- **[Velocity](https://github.com/PaperMC/Velocity)** - A modern, high-performance Minecraft proxy
- **[BungeeCord](https://github.com/SpigotMC/BungeeCord)** - The original Minecraft proxy server
- **[Waterfall](https://github.com/PaperMC/Waterfall)** - BungeeCord fork with additional features

### Networking
- **[Netty](https://github.com/netty/netty)** - Asynchronous event-driven network framework
- **[netty-incubator-codec-quic](https://github.com/netty/netty-incubator-codec-quic)** - QUIC protocol implementation for Netty

### Hytale
- **[Hytale](https://hytale.com)** - The game this proxy is built for
- **[Hytale API Documentation](https://hytale.com/news)** - Official Hytale news and updates

---

## License

Private/Proprietary

---

## Contributing

This is a private project. Contact the maintainers for contribution guidelines.
