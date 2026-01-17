package me.internalizable.numdrassl.plugin.bridge;

import com.hypixel.hytale.protocol.Packet;
import me.internalizable.numdrassl.event.api.NumdrasslEventManager;
import me.internalizable.numdrassl.event.mapping.PacketContext;
import me.internalizable.numdrassl.event.mapping.PacketEventRegistry;
import me.internalizable.numdrassl.event.packet.PacketEvent;
import me.internalizable.numdrassl.plugin.NumdrasslProxy;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Bridges packets to API events.
 *
 * <p>Delegates packet processing to {@link PacketEventRegistry} which maps
 * low-level protocol packets to high-level API events that plugins can handle.</p>
 */
public final class PacketEventBridge {

    private final PacketEventRegistry packetRegistry;

    public PacketEventBridge(@Nonnull NumdrasslProxy proxy, @Nonnull NumdrasslEventManager eventManager) {
        Objects.requireNonNull(proxy, "proxy");
        Objects.requireNonNull(eventManager, "eventManager");
        this.packetRegistry = new PacketEventRegistry(proxy, eventManager);
    }

    /**
     * Processes a client-to-server packet.
     *
     * @param event the packet event
     * @return the potentially modified packet, or null if cancelled
     */
    @Nullable
    public <T extends Packet> T processClientPacket(@Nonnull PacketEvent<T> event) {
        Objects.requireNonNull(event, "event");
        return packetRegistry.processPacket(
            event.getSession(),
            event.getPacket(),
            PacketContext.Direction.CLIENT_TO_SERVER
        );
    }

    /**
     * Processes a server-to-client packet.
     *
     * @param event the packet event
     * @return the potentially modified packet, or null if cancelled
     */
    @Nullable
    public <T extends Packet> T processServerPacket(@Nonnull PacketEvent<T> event) {
        Objects.requireNonNull(event, "event");
        return packetRegistry.processPacket(
            event.getSession(),
            event.getPacket(),
            PacketContext.Direction.SERVER_TO_CLIENT
        );
    }

    /**
     * Gets the underlying packet event registry.
     */
    @Nonnull
    public PacketEventRegistry getPacketRegistry() {
        return packetRegistry;
    }
}

