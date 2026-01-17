# Numdrassl Event System Architecture

This document describes the internal architecture of the Numdrassl event system.

## Overview

The event system has two layers:

1. **API Layer** (`api/`) - Interfaces and events that plugins depend on
2. **Implementation Layer** (`proxy/`) - Internal implementation

```
┌─────────────────────────────────────────────────────────────────┐
│                         PLUGINS                                  │
│                    (depend on api/ only)                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      API MODULE (api/)                          │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │  EventManager   │  │    Events       │  │   @Subscribe    │ │
│  │   (interface)   │  │  (classes)      │  │  (annotation)   │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    PROXY MODULE (proxy/)                        │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              NumdrasslEventManager                       │   │
│  │         (implements EventManager)                        │   │
│  └─────────────────────────────────────────────────────────┘   │
│                              │                                  │
│           ┌──────────────────┼──────────────────┐              │
│           ▼                  ▼                  ▼              │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐  │
│  │ PacketEvent     │ │  ApiEventBridge │ │ PacketEvent     │  │
│  │ Registry        │ │                 │ │ Mappings        │  │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Event Flow

### 1. Packet Events (Client → Server)

```
Client sends packet
       │
       ▼
┌──────────────────┐
│ ClientPacket     │
│ Handler          │
└──────────────────┘
       │
       ▼
┌──────────────────┐
│ Internal         │
│ EventManager     │◄─── dispatchClientPacket()
└──────────────────┘
       │
       ▼
┌──────────────────┐
│ ApiEventBridge   │◄─── onClientPacket()
└──────────────────┘
       │
       ▼
┌──────────────────┐
│ PacketEvent      │◄─── processPacket()
│ Registry         │
└──────────────────┘
       │
       ├─── Find mapping for packet class
       │
       ▼
┌──────────────────┐
│ PacketEvent      │     Example:
│ Mapping          │     ChatMessageMapping
└──────────────────┘
       │
       ├─── createEvent() → PlayerChatEvent or PlayerCommandEvent
       │
       ▼
┌──────────────────┐
│ Numdrassl        │◄─── fireSync(event)
│ EventManager     │
└──────────────────┘
       │
       ├─── Sort handlers by priority
       ├─── Invoke each handler
       │
       ▼
┌──────────────────┐
│ Plugin Handlers  │     @Subscribe methods
└──────────────────┘
       │
       ▼
┌──────────────────┐
│ PacketEvent      │◄─── applyChanges() / isCancelled()
│ Mapping          │
└──────────────────┘
       │
       ├─── If cancelled: return null (drop packet)
       ├─── If modified: update packet fields
       │
       ▼
┌──────────────────┐
│ Forward to       │
│ Backend Server   │
└──────────────────┘
```

### 2. Packet Events (Server → Client)

Same flow but in reverse direction:
- `BackendPacketHandler` receives packet
- `ApiEventBridge.onServerPacket()` processes it
- Packet is forwarded to client (or dropped if cancelled)

### 3. Session Lifecycle Events

These are NOT packet-based:

```
New TCP Connection
       │
       ▼
┌──────────────────┐
│ Session Created  │
└──────────────────┘
       │
       ▼
┌──────────────────┐
│ ApiEventBridge   │◄─── onSessionCreated()
│                  │
│ Fires:           │
│ PreLoginEvent    │     (can deny connection)
└──────────────────┘
       │
       ▼
┌──────────────────┐
│ Authentication   │
│ Process          │
└──────────────────┘
       │
       ▼
┌──────────────────┐
│ ConnectAccept    │
│ Received         │
└──────────────────┘
       │
       ▼
┌──────────────────┐
│ ApiEventBridge   │◄─── firePostLoginEvent()
│                  │
│ Fires:           │
│ PostLoginEvent   │
└──────────────────┘
       │
       ▼
┌──────────────────┐
│ Connection       │
│ Closed           │
└──────────────────┘
       │
       ▼
┌──────────────────┐
│ ApiEventBridge   │◄─── onSessionClosed()
│                  │
│ Fires:           │
│ DisconnectEvent  │
└──────────────────┘
```

## Command Flow

```
Player sends "/command args"
       │
       ▼
┌──────────────────┐
│ ChatMessage      │
│ Packet           │
└──────────────────┘
       │
       ▼
┌──────────────────┐
│ ChatMessage      │
│ Mapping          │
└──────────────────┘
       │
       ├─── Detects "/" prefix
       │
       ▼
┌──────────────────┐
│ Creates          │
│ PlayerCommand    │
│ Event            │
└──────────────────┘
       │
       ▼
┌──────────────────┐
│ NumdrasslEvent   │◄─── fireSync(PlayerCommandEvent)
│ Manager          │
└──────────────────┘
       │
       ▼
┌──────────────────┐
│ CommandEvent     │     @Subscribe(priority = EARLY)
│ Listener         │     onPlayerCommand()
└──────────────────┘
       │
       ├─── Check if proxy has this command
       │
       ▼
┌──────────────────────────────────────────────┐
│ If proxy command:                            │
│   1. Execute via CommandManager              │
│   2. Set event.setForwardToServer(false)     │
│   3. Return null from mapping (don't forward)│
│                                              │
│ If NOT proxy command:                        │
│   1. Let event continue                      │
│   2. Forward to backend server               │
└──────────────────────────────────────────────┘
```

## Packet-to-Event Mappings

Mappings are in `me.internalizable.numdrassl.event.mapping.*`:

```
mapping/
├── connection/
│   ├── ConnectMapping.java      → LoginEvent
│   └── DisconnectMapping.java   → DisconnectEvent
├── interface_/
│   ├── ChatMessageMapping.java  → PlayerChatEvent / PlayerCommandEvent
│   └── ServerMessageMapping.java → ServerMessageEvent
├── inventory/
│   └── SetActiveSlotMapping.java → PlayerSlotChangeEvent
└── player/
    ├── ClientMovementMapping.java → PlayerMoveEvent
    └── ClientPlaceBlockMapping.java → PlayerBlockPlaceEvent
```

### Creating a Custom Mapping

```java
public class MyPacketMapping implements PacketEventMapping<MyPacket, MyEvent> {

    @Override
    public Class<MyPacket> getPacketClass() {
        return MyPacket.class;
    }

    @Override
    public Class<MyEvent> getEventClass() {
        return MyEvent.class;
    }

    @Override
    public MyEvent createEvent(PacketContext context, MyPacket packet) {
        if (!context.isClientToServer()) {
            return null; // Only handle one direction
        }
        return new MyEvent(context.getPlayer(), packet.someField);
    }

    @Override
    public MyPacket applyChanges(PacketContext context, MyPacket packet, MyEvent event) {
        // Apply any modifications from the event back to the packet
        packet.someField = event.getModifiedValue();
        return packet;
    }

    @Override
    public boolean isCancelled(MyEvent event) {
        return event.isCancelled();
    }
}

// Register it
apiEventBridge.getPacketRegistry().register(new MyPacketMapping());
```

## Event Priority

Handlers are executed in priority order:

| Priority | Value | Use Case |
|----------|-------|----------|
| `FIRST` | -100 | Logging, metrics (observe only) |
| `EARLY` | -50 | Proxy commands, security checks |
| `NORMAL` | 0 | Default plugins |
| `LATE` | 50 | Plugins that need to see modifications |
| `LAST` | 100 | Final processing, cleanup |

## Thread Safety

- Event registration/unregistration uses `ReadWriteLock`
- Handlers are stored in `CopyOnWriteArrayList`
- Events are fired synchronously on the Netty event loop
- Async firing available via `fire()` which returns `CompletableFuture`

## Key Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `EventManager` | api | Interface for plugins |
| `NumdrasslEventManager` | proxy | Implementation with priority, MethodHandle |
| `PacketEventRegistry` | proxy | Maps packets to events |
| `PacketEventMapping` | proxy | Interface for packet→event translation |
| `ApiEventBridge` | proxy | Integrates with internal packet system |
| `HandlerRegistration` | proxy | Stores handler metadata |
| `EventTypeTracker` | proxy | Tracks event class hierarchy |

