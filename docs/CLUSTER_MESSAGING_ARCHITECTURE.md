# Numdrassl Cluster & Messaging Architecture

This document describes the cluster management and cross-proxy messaging system.

## Overview

Numdrassl supports two deployment modes:

1. **Standalone Mode**: Single proxy, no Redis required
2. **Cluster Mode**: Multiple proxies with Redis-backed pub/sub messaging

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLUSTER MODE                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌─────────────┐       ┌─────────────┐       ┌─────────────┐              │
│   │  Proxy EU   │       │  Proxy US   │       │  Proxy AS   │              │
│   │  (100 plrs) │       │  (150 plrs) │       │  (80 plrs)  │              │
│   └──────┬──────┘       └──────┬──────┘       └──────┬──────┘              │
│          │                     │                     │                      │
│          └─────────────────────┼─────────────────────┘                      │
│                                │                                            │
│                         ┌──────┴──────┐                                     │
│                         │    Redis    │                                     │
│                         │  Pub/Sub    │                                     │
│                         └─────────────┘                                     │
│                                                                             │
│   Global Player Count: 330                                                  │
│   Channels: HEARTBEAT, CHAT, BROADCAST, PLAYER_COUNT, TRANSFER, PLUGIN     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Package Structure

### API Layer (`api/src/main/java/.../api/messaging/`)

```
messaging/
├── MessagingService.java       # Main service interface
├── Subscription.java           # Active subscription handle
├── ChannelMessage.java         # Base message interface
├── annotation/
│   ├── MessageSubscribe.java   # @MessageSubscribe annotation
│   └── TypeAdapter.java        # Custom JSON serialization
├── channel/
│   ├── MessageChannel.java     # Channel interface
│   ├── Channels.java           # Channel registry (HEARTBEAT, CHAT, etc.)
│   ├── SystemChannel.java      # System channel enum
│   └── BroadcastType.java      # Broadcast message types
├── handler/
│   ├── MessageHandler.java     # Generic message callback
│   └── PluginMessageHandler.java # Plugin-specific callback
└── message/
    ├── HeartbeatMessage.java   # Proxy liveness
    ├── ChatMessage.java        # Cross-proxy chat
    ├── BroadcastMessage.java   # Server announcements
    ├── PlayerCountMessage.java # Player count sync
    ├── TransferMessage.java    # Cross-proxy transfers
    └── PluginMessage.java      # Custom plugin data
```

### Implementation Layer (`proxy/src/main/java/.../`)

```
cluster/
├── NumdrasslClusterManager.java  # Cluster coordination
├── ProxyRegistry.java            # Track online proxies
├── HeartbeatPublisher.java       # Periodic heartbeats
└── handler/
    ├── HeartbeatHandler.java     # Process heartbeats
    ├── ChatHandler.java          # Cross-proxy chat
    ├── BroadcastHandler.java     # Announcements
    ├── PlayerCountHandler.java   # Player sync
    └── TransferHandler.java      # Transfer routing

messaging/
├── redis/
│   ├── RedisMessagingService.java  # Redis pub/sub implementation
│   ├── RedisMessageListener.java   # Message reception
│   └── RedisConnectionException.java
├── local/
│   └── LocalMessagingService.java  # Non-cluster fallback
├── codec/
│   └── MessageCodec.java           # JSON encode/decode
├── processing/
│   ├── SubscribeMethodProcessor.java # @MessageSubscribe handling
│   └── PluginIdExtractor.java        # Extract plugin IDs
└── subscription/
    ├── SubscriptionEntry.java        # Subscription metadata
    ├── RedisSubscription.java        # Redis-backed subscription
    ├── LocalSubscription.java        # Local subscription
    └── CompositeSubscription.java    # Multiple subscriptions
```

## Annotation Distinction

**Important:** There are two `Subscribe` annotations with different purposes:

| Annotation | Package | Purpose |
|------------|---------|---------|
| `@Subscribe` | `api.event` | Local proxy events (player join, chat, commands) |
| `@MessageSubscribe` | `api.messaging.annotation` | Cross-proxy Redis messages |

### Example: Local Events

```java
import me.internalizable.numdrassl.api.event.Subscribe;
import me.internalizable.numdrassl.api.event.player.PlayerChatEvent;

public class MyListener {
    
    @Subscribe  // Local event - happens on THIS proxy only
    public void onPlayerChat(PlayerChatEvent event) {
        // Player on this proxy sent a chat message
        event.getPlayer().sendMessage("You said: " + event.getMessage());
    }
}
```

### Example: Cross-Proxy Messages

```java
import me.internalizable.numdrassl.api.messaging.MessagingService;
import me.internalizable.numdrassl.api.messaging.annotation.MessageSubscribe;
import me.internalizable.numdrassl.api.messaging.channel.SystemChannel;
import me.internalizable.numdrassl.api.messaging.message.ChatMessage;
import me.internalizable.numdrassl.api.plugin.Inject;
import me.internalizable.numdrassl.api.plugin.Plugin;
import org.slf4j.Logger;

@Plugin(id = "my-plugin", name = "My Plugin", version = "1.0.0")
public class MyPlugin {

    @Inject
    private MessagingService messaging;
    
    @Inject
    private Logger logger;
    
    @MessageSubscribe(SystemChannel.CHAT)  // Cross-proxy message from Redis
    public void onCrossProxyChat(ChatMessage msg) {
        // Chat message from ANOTHER proxy
        logger.info("Player on {} said: {}", msg.sourceProxyId(), msg.message());
    }
    
    @MessageSubscribe(channel = "scores")  // Plugin-specific channel
    public void onScoreUpdate(ScoreData data) {
        // Custom plugin message from another proxy
    }
    
    // Publish to all proxies
    public void broadcastScore(ScoreData data) {
        messaging.publishPlugin("my-plugin", "scores", data);
    }
}
```

## System Channels

| Channel | Message Type | Direction | Purpose |
|---------|--------------|-----------|---------|
| `HEARTBEAT` | `HeartbeatMessage` | All proxies | Liveness monitoring, player count sync |
| `CHAT` | `ChatMessage` | All proxies | Global chat messages |
| `BROADCAST` | `BroadcastMessage` | All proxies | Server announcements |
| `PLAYER_COUNT` | `PlayerCountMessage` | All proxies | Real-time player count |
| `TRANSFER` | `TransferMessage` | Targeted | Cross-proxy player transfers |
| `PLUGIN` | `PluginMessage` | Filtered | Custom plugin data |

## Message Flow

### Publishing a Message

```
Plugin calls publish()
         │
         ▼
┌─────────────────────┐
│  MessagingService   │
│  (Redis or Local)   │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│    MessageCodec     │  Serialize to JSON
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│   Redis PUBLISH     │  Channel: "numdrassl:chat"
└──────────┬──────────┘
           │
           ▼
    ┌──────┴──────┐
    │             │
    ▼             ▼
┌────────┐   ┌────────┐
│Proxy 2 │   │Proxy 3 │  Redis SUBSCRIBE
└────────┘   └────────┘
```

### Receiving a Message

```
Redis SUBSCRIBE notification
         │
         ▼
┌─────────────────────┐
│ RedisMessageListener│
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│    MessageCodec     │  Deserialize from JSON
└──────────┬──────────┘
           │
           ├── Filter: sourceProxyId != localProxyId (unless includeSelf)
           │
           ▼
┌─────────────────────┐
│  SubscriptionEntry  │  Match channel + message type
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│   MessageHandler    │  Invoke plugin callback
└─────────────────────┘
```

## Cluster Management

### Proxy Registration

When a proxy starts in cluster mode:

1. Generate unique `proxyId` (or use configured value)
2. Connect to Redis
3. Subscribe to all system channels
4. Start heartbeat publisher (every 5 seconds)
5. Register with `ProxyRegistry`

### Heartbeat Protocol

```java
HeartbeatMessage {
    sourceProxyId: "proxy-eu-1",
    timestamp: "2026-01-21T10:30:00Z",
    region: "eu-west",
    playerCount: 150
}
```

- Published every 5 seconds
- If no heartbeat received in 30 seconds, proxy marked offline
- `ProxyJoinClusterEvent` fired when new proxy detected
- `ProxyLeaveClusterEvent` fired when proxy times out

### Player Count Synchronization

```java
// Get local player count
int localCount = proxy.getPlayerCount();  // Players on THIS proxy

// Get global player count (all proxies)
int globalCount = proxy.getGlobalPlayerCount();  // Sum across cluster
```

### Cross-Proxy Transfers

When transferring a player to a server on another proxy:

1. Source proxy sends `TransferMessage` to target proxy
2. Target proxy validates player can join
3. Source proxy sends `ClientReferral` to player
4. Player reconnects to target proxy
5. Target proxy routes to specified backend

## Configuration

### Standalone Mode (Default)

```yaml
clusterEnabled: false
# No Redis configuration needed
```

### Cluster Mode

```yaml
clusterEnabled: true

# Redis connection
redisHost: "redis.myserver.com"
redisPort: 6379
redisPassword: "secret"        # Optional
redisSsl: true                 # For production
redisDatabase: 0

# Proxy identity
proxyId: "proxy-eu-1"          # Auto-generated if null
proxyRegion: "eu-west"         # For load balancing
```

## Programmatic API

### MessagingService

```java
MessagingService messaging = proxy.getMessagingService();

// Check connection
boolean connected = messaging.isConnected();

// Publish to system channel
messaging.publish(Channels.BROADCAST, new BroadcastMessage(
    proxyId, Instant.now(), "Server restart in 5 min", BroadcastType.WARNING
));

// Subscribe to system channel
Subscription sub = messaging.subscribe(Channels.CHAT, ChatMessage.class,
    (channel, msg) -> logger.info("Chat: {}", msg.message()));

// Plugin messages
messaging.publishPlugin("my-plugin", "events", myData);
messaging.subscribePlugin("my-plugin", "events", MyData.class,
    (sourceProxyId, data) -> handleData(data));

// Unsubscribe
sub.unsubscribe();

// Register annotation-based listener
messaging.registerListener(myListenerInstance);
```

### ClusterManager

```java
ClusterManager cluster = proxy.getClusterManager();

// Check mode
if (cluster.isClusterMode()) {
    // Get all online proxies
    Collection<ProxyInfo> proxies = cluster.getOnlineProxies();
    
    // Find proxy by region
    Optional<ProxyInfo> euProxy = cluster.getLeastLoadedProxy("eu-west");
    
    // Check if player is online anywhere
    boolean online = cluster.isPlayerOnline(playerUuid);
    
    // Get total players across cluster
    int globalCount = cluster.getGlobalPlayerCount();
}
```

## Best Practices

### 1. Always Check Cluster Mode

```java
if (proxy.getClusterManager().isClusterMode()) {
    // Use cross-proxy messaging
} else {
    // Fallback to local-only behavior
}
```

### 2. Handle Connection Failures

```java
try {
    messaging.publish(channel, message).get(5, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    logger.warn("Redis publish timed out");
}
```

### 3. Use Appropriate Annotations

- `@Subscribe` for events that happen on THIS proxy
- `@MessageSubscribe` for messages from OTHER proxies

### 4. Filter Self-Messages

By default, `@MessageSubscribe` filters out messages from the same proxy.
Use `includeSelf = true` only when needed:

```java
@MessageSubscribe(value = SystemChannel.HEARTBEAT, includeSelf = true)
public void onAnyHeartbeat(HeartbeatMessage msg) {
    // Includes heartbeats from THIS proxy too
}
```

### 5. Keep Messages Small

Redis pub/sub is not designed for large payloads. Keep messages under 1MB.
For large data, use Redis hashes/lists or external storage.

## Troubleshooting

### "Redis connection failed"

1. Check `redisHost` and `redisPort` are correct
2. Verify Redis is running: `redis-cli ping`
3. Check firewall rules
4. If using password, ensure `redisPassword` is set

### "No heartbeat from proxy X"

1. Check proxy X is running
2. Verify both proxies connect to same Redis
3. Check network connectivity between proxy and Redis

### "Message not received"

1. Ensure `@MessageSubscribe` (not `@Subscribe`) is used
2. Verify channel name matches exactly
3. Check `includeSelf` if expecting self-messages
4. Verify message type matches handler parameter

