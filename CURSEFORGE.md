# Numdrassl - Hytale Proxy Server

A high-performance, extensible Hytale proxy server written in Java that enables multi-server networks, player transfers, plugin support, and a flexible permission system.

---

## What is Numdrassl?

Numdrassl is a **BungeeCord/Velocity-style proxy** for Hytale. It sits between players and your backend servers, allowing you to:

- Connect multiple Hytale servers into a single network
- Transfer players seamlessly between servers
- Intercept and modify packets
- Create plugins with custom commands and events
- Manage permissions with groups and wildcards

---

## Features

- **Multi-Server Network** - Connect multiple backend Hytale servers (lobby, minigames, etc.)
- **Player Transfers** - Seamlessly move players between servers with `/server <name>`
- **Plugin System** - Extend functionality with custom plugins
- **Permission System** - Built-in YAML-based permissions with groups, wildcards, and external provider support
- **Event System** - Hook into player connections, chat, commands, and more
- **QUIC Protocol** - Native support for Hytale's QUIC transport with BBR congestion control
- **Secure Authentication** - HMAC-signed referrals between proxy and backends

---

## Requirements

- **Java 25** or higher
- **Hytale Server(s)** to use as backends

---

## Installation

### Step 1: Download

Download the latest release:
- `proxy-*.jar` - The proxy server
- `bridge-*.jar` - Plugin for your backend servers

### Step 2: Run the Proxy

```bash
java -jar proxy-1.0-SNAPSHOT.jar
```

On first run, the proxy will:
1. Generate TLS certificates in `certs/`
2. Create `config/proxy.yml` configuration file
3. Prompt you to authenticate with Hytale

### Step 3: Authenticate with Hytale

In the proxy console, run:
```
auth login
```

Visit the URL shown and enter the device code to link your Hytale account.

---

## Proxy Configuration

Edit `config/proxy.yml`:

```yaml
# Network Settings
bindAddress: "0.0.0.0"
bindPort: 45585

# Public address (for server transfers)
publicAddress: "play.yourserver.com"
publicPort: 45585

# Shared secret - MUST match your Bridge plugin config!
proxySecret: "change-this-to-a-secure-secret"

# Backend Servers
backends:
  - name: "lobby"
    host: "127.0.0.1"
    port: 5520
    defaultServer: true    # Players join here first
    
  - name: "survival"
    host: "127.0.0.1"
    port: 5521
    defaultServer: false
    
  - name: "minigames"
    host: "192.168.1.100"
    port: 5520
    defaultServer: false
```

### Configuration Options

| Option | Description |
|--------|-------------|
| `bindAddress` | IP to listen on (`0.0.0.0` for all interfaces) |
| `bindPort` | Port to listen on (default: `45585`) |
| `publicAddress` | Public hostname/IP for server transfers |
| `publicPort` | Public port for server transfers |
| `proxySecret` | Shared secret for backend authentication |
| `backends` | List of backend servers |
| `debugMode` | Enable verbose logging (default: `false`) |

---

## Backend Server Setup

Each backend server needs the **Bridge plugin** to authenticate connections from the proxy.

### Step 1: Install Bridge Plugin

Copy `bridge-1.0-SNAPSHOT.jar` to your Hytale server's `plugins/` folder.

### Step 2: Start Server in Insecure Mode

```bash
java -jar HytaleServer.jar --auth-mode insecure --transport QUIC
```

> **Important:** The `--auth-mode insecure` flag is required because the proxy handles Hytale authentication, not the backend.

### Step 3: Configure Bridge

On first run, the Bridge creates `plugins/Bridge/config.json`:

```json
{
  "proxySecret": "change-this-to-a-secure-secret",
  "serverName": "lobby"
}
```

| Option | Description |
|--------|-------------|
| `proxySecret` | **Must match** the `proxySecret` in your proxy config! |
| `serverName` | Name of this server (for logging/identification) |

### Step 4: Secure Your Backend (Important!)

**Block direct connections** to your backend servers. Only allow the proxy's IP:

```bash
# Example: Only allow proxy (192.168.1.50) to connect on port 5520
iptables -A INPUT -p udp --dport 5520 -s 192.168.1.50 -j ACCEPT
iptables -A INPUT -p udp --dport 5520 -j DROP
```

Without this, players could bypass the proxy and connect directly!

---

## Player Commands

| Command | Aliases | Description |
|---------|---------|-------------|
| `/server` | `/srv` | List available servers |
| `/server <name>` | `/srv <name>` | Transfer to a server |
| `/numdrassl` | `/nd`, `/proxy` | Proxy management commands |
| `/numdrassl version` | | Show proxy version |
| `/numdrassl perm ...` | | Permission management |

---

## Permission System

Permissions are stored in YAML files under `data/permissions/`:

```
data/permissions/
├── players/
│   └── {uuid}.yml       # Per-player permissions
├── groups/
│   ├── default.yml      # Default group (all players)
│   └── admin.yml        # Custom groups
└── player-groups.yml    # Player-to-group assignments
```

### Player Permissions (`players/{uuid}.yml`)

```yaml
permissions:
  - numdrassl.command.server
  - numdrassl.command.help
  - -numdrassl.command.stop  # Denied (prefix with -)
```

### Group Permissions (`groups/admin.yml`)

```yaml
name: admin
default: false
permissions:
  - numdrassl.command.*     # Wildcard
  - numdrassl.admin
```

### Permission Commands

| Command | Description |
|---------|-------------|
| `/numdrassl perm user <player> info` | Show player's groups/permissions |
| `/numdrassl perm user <player> add <perm>` | Grant a permission |
| `/numdrassl perm user <player> remove <perm>` | Remove a permission |
| `/numdrassl perm user <player> addgroup <group>` | Add player to group |
| `/numdrassl perm user <player> removegroup <group>` | Remove from group |
| `/numdrassl perm group <group> add <perm>` | Add permission to group |
| `/numdrassl perm group <group> remove <perm>` | Remove from group |
| `/numdrassl perm reload` | Reload permissions from disk |

---

## Console Commands

| Command | Description |
|---------|-------------|
| `auth login` | Authenticate with Hytale |
| `auth status` | Show authentication status |
| `auth logout` | Clear stored credentials |
| `sessions` | List connected players |
| `stop` | Shut down the proxy |
| `help` | Show available commands |

---

## Plugin Development

Create plugins to extend the proxy! Plugins can:
- Listen to events (player join, chat, commands, etc.)
- Register custom commands
- Modify packets
- Interact with players and servers

**API Dependency:**
```kotlin
dependencies {
    compileOnly("me.internalizable.numdrassl:api:1.0-SNAPSHOT")
}
```

**Example Plugin:**
```java
@Plugin(id = "my-plugin", name = "My Plugin", version = "1.0.0")
public class MyPlugin {
    
    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        if (event.getMessage().contains("hello")) {
            event.getPlayer().sendMessage("Hello to you too!");
        }
    }
}
```

See the full [Plugin Development Guide](https://github.com/Numdrassl/proxy/blob/main/docs/PLUGIN_DEVELOPMENT.md) for more details.

---

## Troubleshooting

### "Proxy not authenticated"
Run `auth login` in the proxy console and complete the device code flow.

### "Invalid player info message (is your proxy secret valid?)"
The `proxySecret` in your proxy config doesn't match the Bridge plugin's `proxySecret`.

### "Connection timed out" to backend
1. Check the backend server is running
2. Verify firewall allows the proxy IP
3. Confirm the address/port in config is correct

### "Cannot direct join numdrassl backend"
Players are trying to connect directly to the backend. Set up firewall rules to block direct connections.

### Client shows "unexpected packet"
The backend server may not have the Bridge plugin installed, or isn't running with `--auth-mode insecure`.

### Redis connection failed (Cluster Mode)
1. Verify Redis server is running and accessible
2. Check `redis.host` and `redis.port` in your config
3. If using authentication, ensure `redis.password` is correct
4. Verify firewall allows connections to Redis port

### Players not synced across proxies (Cluster Mode)
1. Ensure all proxies have `cluster.enabled: true`
2. Verify all proxies connect to the same Redis instance
3. Check that `cluster.proxyId` is unique for each proxy

---

## Architecture Overview

### Single Proxy Mode

```
┌─────────────┐                    ┌─────────────┐                    ┌─────────────┐
│   Player    │ ──── QUIC/TLS ──── │   Proxy     │ ──── QUIC/TLS ──── │   Backend   │
│   Client    │                    │  Numdrassl  │                    │   Server    │
└─────────────┘                    └─────────────┘                    └─────────────┘
                                          │
                                          ├── lobby (default)
                                          ├── survival
                                          └── minigames
```

### Cluster Mode (Multi-Proxy)

```
                              ┌──────────────────────────────────┐
                              │            Redis                 │
                              │   ┌────────────────────────┐     │
                              │   │  Pub/Sub Channels      │     │
                              │   │  • numdrassl:heartbeat │     │
                              │   │  • numdrassl:chat      │     │
                              │   │  • numdrassl:transfer  │     │
                              │   │  • numdrassl:broadcast │     │
                              │   └────────────────────────┘     │
                              │   ┌────────────────────────┐     │
                              │   │  Shared State          │     │
                              │   │  • Player locations    │     │
                              │   │  • Proxy registry      │     │
                              │   │  • Player counts       │     │
                              │   └────────────────────────┘     │
                              └──────────────┬───────────────────┘
                                             │
           ┌─────────────────────────────────┼─────────────────────────────────┐
           │                                 │                                 │
    ┌──────▼──────┐                   ┌──────▼──────┐                   ┌──────▼──────┐
    │ Proxy (EU)  │                   │ Proxy (US)  │                   │ Proxy (Asia)│
    │ proxy-eu-1  │◄─── Transfer ────►│ proxy-us-1  │◄─── Transfer ────►│ proxy-as-1  │
    └──────┬──────┘                   └──────┬──────┘                   └──────┬──────┘
           │                                 │                                 │
    ┌──────▼──────┐                   ┌──────▼──────┐                   ┌──────▼──────┐
    │  Backends   │                   │  Backends   │                   │  Backends   │
    │  • lobby    │                   │  • lobby    │                   │  • lobby    │
    │  • games    │                   │  • games    │                   │  • games    │
    └─────────────┘                   └─────────────┘                   └─────────────┘
```

### Authentication Flow

```
┌────────┐          ┌───────────┐          ┌─────────────┐          ┌─────────┐
│ Player │          │   Proxy   │          │   Hytale    │          │ Backend │
└───┬────┘          └─────┬─────┘          │  Sessions   │          └────┬────┘
    │                     │                └──────┬──────┘               │
    │  1. Connect         │                       │                      │
    │  (identity_token)   │                       │                      │
    │────────────────────►│                       │                      │
    │                     │  2. Request auth      │                      │
    │                     │     grant             │                      │
    │                     │──────────────────────►│                      │
    │                     │                       │                      │
    │                     │  3. auth_grant        │                      │
    │                     │◄──────────────────────│                      │
    │  4. AuthGrant       │                       │                      │
    │◄────────────────────│                       │                      │
    │                     │                       │                      │
    │  5. AuthToken       │                       │                      │
    │────────────────────►│  6. Exchange token    │                      │
    │                     │──────────────────────►│                      │
    │                     │◄──────────────────────│                      │
    │  7. ServerAuthToken │                       │                      │
    │◄────────────────────│                       │                      │
    │                     │                       │                      │
    │                     │  8. Connect + HMAC-signed referral           │
    │                     │─────────────────────────────────────────────►│
    │                     │                       │                      │
    │                     │  9. ConnectAccept (secret validated)         │
    │                     │◄─────────────────────────────────────────────│
    │                     │                       │                      │
```

### Cross-Proxy Transfer Flow

```
┌────────┐     ┌──────────┐                    ┌──────────┐     ┌─────────┐
│ Player │     │ Proxy A  │                    │ Proxy B  │     │ Backend │
│        │     │ (source) │      Redis         │ (target) │     │ Server  │
└───┬────┘     └────┬─────┘        │           └────┬─────┘     └────┬────┘
    │               │              │                │                │
    │ /server hub   │              │                │                │
    │──────────────►│              │                │                │
    │               │              │                │                │
    │               │  1. Publish  │                │                │
    │               │  TransferMsg │                │                │
    │               │─────────────►│                │                │
    │               │              │                │                │
    │               │              │  2. Subscribe  │                │
    │               │              │  receives msg  │                │
    │               │              │───────────────►│                │
    │               │              │                │                │
    │ 3. ClientReferral            │                │                │
    │ (reconnect to Proxy B)       │                │                │
    │◄──────────────│              │                │                │
    │               │              │                │                │
    │ 4. New connection            │                │                │
    │─────────────────────────────────────────────►│                │
    │               │              │                │                │
    │               │              │                │  5. Forward    │
    │               │              │                │  to backend    │
    │               │              │                │───────────────►│
```

1. **Player connects** to the proxy with their Hytale credentials
2. **Proxy authenticates** the player with Hytale session service
3. **Proxy forwards** the connection to the default backend with signed referral
4. **Backend validates** the referral using the shared secret
5. **Packets flow** bidirectionally through the proxy
6. **In cluster mode**, player state and messages sync via Redis pub/sub

---

## Cluster Mode (Multi-Proxy Networks)

For large networks, you can run multiple Numdrassl proxies across different regions with shared state using Redis.

### Overview

```
                                    ┌─────────────────┐
                                    │     Redis       │
                                    │  (Pub/Sub Hub)  │
                                    └────────┬────────┘
                    ┌───────────────────────┼───────────────────────┐
                    │                       │                       │
            ┌───────▼───────┐       ┌───────▼───────┐       ┌───────▼───────┐
            │  Proxy (EU)   │       │  Proxy (US)   │       │  Proxy (Asia) │
            │  Region: eu   │       │  Region: us   │       │  Region: asia │
            └───────┬───────┘       └───────┬───────┘       └───────┬───────┘
                    │                       │                       │
            ┌───────▼───────┐       ┌───────▼───────┐       ┌───────▼───────┐
            │   Backends    │       │   Backends    │       │   Backends    │
            └───────────────┘       └───────────────┘       └───────────────┘
```

### Cluster Features

- **Cross-Proxy Player Tracking** - Know which players are online across all proxies
- **Cross-Proxy Messaging** - Send messages to players on other proxies
- **Cross-Proxy Transfers** - Transfer players between servers on different proxies
- **Load Balancing** - Route players to the least loaded proxy in their region
- **Health Monitoring** - Automatic heartbeat and stale proxy detection

### Redis Configuration

Add to your `config/proxy.yml`:

```yaml
# Cluster Settings
cluster:
  enabled: true
  
  # Unique ID for this proxy instance (auto-generated if not set)
  proxyId: "proxy-eu-1"
  
  # Region identifier for geographic load balancing
  region: "eu"
  
  # Redis connection settings
  redis:
    host: "redis.yourserver.com"
    port: 6379
    password: "your-redis-password"  # Optional
    database: 0
    
    # Connection pool settings
    poolSize: 10
    timeout: 5000  # milliseconds
```

### Cluster Configuration Options

| Option | Description |
|--------|-------------|
| `cluster.enabled` | Enable cluster mode (`true`/`false`) |
| `cluster.proxyId` | Unique identifier for this proxy instance |
| `cluster.region` | Geographic region (e.g., `eu`, `us`, `asia`) |
| `redis.host` | Redis server hostname |
| `redis.port` | Redis server port (default: `6379`) |
| `redis.password` | Redis authentication password (optional) |
| `redis.database` | Redis database number (default: `0`) |
| `redis.poolSize` | Connection pool size (default: `10`) |
| `redis.timeout` | Connection timeout in ms (default: `5000`) |

### Cross-Proxy Commands

When cluster mode is enabled:

| Command | Description |
|---------|-------------|
| `/server <name>` | Transfer to a server (works across proxies) |
| `/numdrassl cluster info` | Show cluster status and connected proxies |
| `/numdrassl cluster players` | List players across all proxies |

### Plugin Messaging API

Plugins can send messages across the cluster:

```java
@Plugin(id = "my-plugin", name = "My Plugin", version = "1.0.0")
public class MyPlugin {
    
    @Inject
    private MessagingService messaging;
    
    // Subscribe to custom messages
    @Subscribe(channel = "my-channel")
    public void onCustomMessage(MyCustomData data) {
        // Handle message from any proxy
    }
    
    // Publish messages to all proxies
    public void broadcastToCluster(String message) {
        messaging.publish("my-channel", new MyCustomData(message));
    }
}
```

### System Channels

The cluster uses these internal channels (plugins can subscribe):

| Channel | Purpose |
|---------|---------|
| `numdrassl:heartbeat` | Proxy health monitoring |
| `numdrassl:player_count` | Player count synchronization |
| `numdrassl:chat` | Cross-proxy chat messages |
| `numdrassl:transfer` | Cross-proxy player transfers |
| `numdrassl:broadcast` | Server-wide announcements |

---

## Links

- [GitHub Repository](https://github.com/Numdrassl/proxy)
- [API Documentation](https://numdrassl.github.io/proxy/)
- [Plugin Development Guide](https://github.com/Numdrassl/proxy/blob/main/docs/PLUGIN_DEVELOPMENT.md)
- [Releases](https://github.com/Numdrassl/proxy/releases)

---

## License

Proprietary - All rights reserved.

