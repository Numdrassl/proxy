# Numdrassl - Hytale QUIC Proxy Server

A BungeeCord/Velocity-style proxy server for Hytale, built using Netty QUIC.

## Architecture

```
┌─────────────┐     QUIC/TLS      ┌─────────────┐     QUIC/TLS      ┌─────────────────┐
│   Hytale    │ ───────────────── │  Numdrassl  │ ───────────────── │ Backend Server  │
│   Client    │                   │    Proxy    │                   │ (lobby, etc.)   │
└─────────────┘                   └─────────────┘                   └─────────────────┘
       │                                 │                                   │
       │  1. Connect with JWT token      │                                   │
       │  (bound to client's cert)       │                                   │
       │ ─────────────────────────────── │                                   │
       │                                 │  2. Forward Connect               │
       │                                 │  (proxy's cert, not client's)     │
       │                                 │ ─────────────────────────────────│
       │                                 │                                   │
       │                                 │  3. Backend trusts proxy IP       │
       │                                 │  (skips cert fingerprint check)   │
       │                                 │ <────────────────────────────────│
       │                                 │                                   │
```

## ⚠️ IMPORTANT: Backend Server Configuration Required

Hytale uses **mutual TLS with certificate binding** - the player's JWT token contains 
a fingerprint of their TLS certificate. When connecting through a proxy, the certificate
fingerprint won't match because the proxy uses its own certificate.

**To make the proxy work, the backend server must be modified to trust the proxy.**

This is similar to how Minecraft's BungeeCord requires `bungeecord: true` in spigot.yml.

### Backend Setup

1. Add the files from `backend-mod/` to your Hytale server:
   - `ProxyModeConfig.java`
   - `ProxyConnectionHandler.java`

2. Enable proxy mode in your server initialization:
   ```java
   ProxyModeConfig.setProxyMode(true, "YOUR_PROXY_IP");
   ```

3. Modify `JWTValidator` to skip certificate fingerprint validation for proxy connections.

4. **FIREWALL**: Block direct connections to the backend port from the internet.
   Only allow connections from your proxy server IP.

See `backend-mod/PROXY_MODE_SETUP.txt` for complete instructions.

## Features

- **QUIC Protocol Support**: Native QUIC transport for Hytale client-server communication
- **Packet Interception**: Decode, inspect, modify, or cancel packets flowing through the proxy
- **Multi-Backend Support**: Route players to different backend servers
- **Player Transfer**: Move players between backend servers seamlessly
- **Event System**: Register listeners to handle packet events
- **Command Handling**: Intercept chat messages for custom command processing

## Project Structure

```
src/main/java/
├── com/hypixel/hytale/protocol/     # Hytale protocol definitions
│   ├── Packet.java                  # Base packet interface
│   ├── PacketRegistry.java          # Packet ID registry
│   ├── io/                          # Packet I/O utilities
│   └── packets/                     # Packet definitions
│       ├── auth/                    # Authentication packets
│       ├── connection/              # Connect, Disconnect, Ping, Pong
│       └── interface_/              # Chat, ServerMessage, etc.
│
└── me/internalizable/numdrassl/     # Proxy implementation
    ├── Main.java                    # Entry point
    ├── api/ProxyAPI.java            # Public API for plugins
    ├── config/                      # Configuration classes
    ├── event/                       # Event system
    ├── pipeline/                    # Netty pipeline handlers
    ├── server/                      # Server & backend connector
    └── session/                     # Player session management
```

## Configuration

Edit `config/proxy.yml`:

```yaml
bindAddress: "0.0.0.0"
bindPort: 45585
certificatePath: "certs/server.crt"
privateKeyPath: "certs/server.key"
maxConnections: 1000
connectionTimeoutSeconds: 30
debugMode: false

backends:
  - name: "lobby"
    host: "127.0.0.1"
    port: 45586
    defaultServer: true
```

## Usage

### Running the Proxy

```bash
./gradlew run
# or
java -jar build/libs/Numdrassl-1.0-SNAPSHOT.jar
```

### Registering Packet Listeners

```java
import me.internalizable.numdrassl.api.ProxyAPI;
import me.internalizable.numdrassl.event.*;

ProxyAPI.registerListener(new PacketListener() {
    @Override
    public <T extends Packet> T onClientPacket(PacketEvent<T> event) {
        // Handle client -> server packets
        if (event.getPacket() instanceof ChatMessage chat) {
            String msg = chat.message;
            
            // Custom command handling
            if (msg != null && msg.startsWith("/server ")) {
                String serverName = msg.substring(8);
                ProxyAPI.transferPlayer(event.getSession(), serverName);
                event.setCancelled(true);
                return null;
            }
        }
        return event.getPacket();
    }
    
    @Override
    public <T extends Packet> T onServerPacket(PacketEvent<T> event) {
        // Handle server -> client packets
        return event.getPacket();
    }
});
```

### Transferring Players

```java
ProxySession session = ProxyAPI.getSession(playerUuid);
ProxyAPI.transferPlayer(session, "survival");
```

### Sending Packets

```java
// Send to client
ProxyAPI.sendToClient(session, packet);

// Send to backend server
ProxyAPI.sendToBackend(session, packet);

// Broadcast to all players
ProxyAPI.broadcast(packet);
```

## Supported Packets

The proxy supports these essential packets:

**Connection:**
- Connect (0)
- Disconnect (1)
- Ping (2)
- Pong (3)

**Authentication:**
- Status (10)
- AuthGrant (11)
- AuthToken (12)
- ServerAuthToken (13)
- ConnectAccept (14)
- PasswordResponse (15)
- PasswordAccepted (16)
- PasswordRejected (17)
- ClientReferral (18)

**Interface:**
- ServerMessage (210)
- ChatMessage (211)
- ShowEventTitle (216)

Unknown packets are forwarded as raw bytes without decoding.

## Building

```bash
./gradlew build
```

The JAR will be created at `build/libs/Numdrassl-1.0-SNAPSHOT.jar`

## Requirements

- Java 25+
- Hytale backend server(s) to proxy to

## Testing

### Quick Test with Mock Backend

1. **Start the mock backend server** (simulates a Hytale server):
   ```bash
   # In terminal 1 - Start mock backend on port 45586
   ./gradlew run --args="mock"
   # Or run the MockBackendServer class directly
   ```

2. **Start the proxy server** (in a separate terminal):
   ```bash
   # In terminal 2 - Start the proxy on port 45585
   ./gradlew run
   ```

3. **Connect a Hytale client** to `localhost:45585`

### Manual Testing Steps

1. **Build the project**:
   ```bash
   ./gradlew build
   ```

2. **Configure the proxy** - Edit `config/proxy.yml`:
   ```yaml
   bindAddress: "0.0.0.0"
   bindPort: 45585
   debugMode: true  # Enable for verbose logging
   
   backends:
     - name: "lobby"
       host: "127.0.0.1"
       port: 45586      # Your actual Hytale server port
       defaultServer: true
   ```

3. **Run the proxy**:
   ```bash
   java -jar build/libs/Numdrassl-1.0-SNAPSHOT.jar
   ```

4. **Check the logs** for:
   - `Hytale QUIC Proxy Server started on 0.0.0.0:45585`
   - Connection messages when clients connect
   - Packet flow in debug mode

### Verifying Packet Flow

With `debugMode: true`, you'll see logs like:
```
[client] Decoded packet: Connect (id=0)
Session 1: Received Connect from PlayerName (uuid)
Session 1: Connecting to backend lobby
[backend] Decoded packet: ConnectAccept (id=14)
Session 1: Backend accepted connection
```

### Testing Packet Interception

Add a custom listener in `Main.java`:
```java
proxyServer.getEventManager().registerListener(new PacketListener() {
    @Override
    public <T extends Packet> T onClientPacket(PacketEvent<T> event) {
        System.out.println("CLIENT -> SERVER: " + event.getPacket().getClass().getSimpleName());
        return event.getPacket();
    }
    
    @Override
    public <T extends Packet> T onServerPacket(PacketEvent<T> event) {
        System.out.println("SERVER -> CLIENT: " + event.getPacket().getClass().getSimpleName());
        return event.getPacket();
    }
});
```

## License

Private/Proprietary

## Plugin Development

Numdrassl supports plugins that can intercept events, register commands, and modify player behavior.

### Quick Start

1. **Add the API dependency** to your plugin's `build.gradle.kts`:

```kotlin
dependencies {
    compileOnly("me.internalizable.numdrassl:api:1.0-SNAPSHOT")
}
```

2. **Create your main class**:

```java
@Plugin(id = "my-plugin", name = "My Plugin", version = "1.0.0")
public class MyPlugin {

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        Numdrassl.getProxy().getEventManager().register(this, new MyListener());
    }
}
```

3. **Listen for events**:

```java
public class MyListener {

    @Subscribe
    public void onChat(PlayerChatEvent event) {
        // Modify or cancel chat messages
    }

    @Subscribe
    public void onCommand(PlayerCommandEvent event) {
        // Handle commands
    }
}
```

### Documentation

- [Plugin Development Guide](docs/PLUGIN_DEVELOPMENT.md) - Full guide for plugin developers
- [Event Architecture](docs/EVENT_ARCHITECTURE.md) - Internal event system documentation

### API Dependency

Plugins only depend on the `api/` module. The proxy implementation (`proxy/`) is not needed at compile time.

## Notes

- Self-signed certificates are auto-generated on first run
- Debug mode enables verbose packet logging
- The proxy forwards unknown packet types without modification

