package me.internalizable.numdrassl.event.packet;

import com.hypixel.hytale.protocol.Packet;
import me.internalizable.numdrassl.session.ProxySession;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Represents a packet event that can be intercepted and modified.
 *
 * @param <T> the packet type
 */
public final class PacketEvent<T extends Packet> {

    private final ProxySession session;
    private final PacketDirection direction;
    private T packet;
    private boolean cancelled;

    public PacketEvent(
            @Nonnull ProxySession session,
            @Nonnull PacketDirection direction,
            @Nonnull T packet) {
        this.session = Objects.requireNonNull(session, "session");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.packet = Objects.requireNonNull(packet, "packet");
        this.cancelled = false;
    }

    @Nonnull
    public ProxySession getSession() {
        return session;
    }

    @Nonnull
    public PacketDirection getDirection() {
        return direction;
    }

    @Nonnull
    public T getPacket() {
        return packet;
    }

    public void setPacket(@Nonnull T packet) {
        this.packet = Objects.requireNonNull(packet, "packet");
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public boolean isClientToServer() {
        return direction == PacketDirection.CLIENT_TO_SERVER;
    }

    public boolean isServerToClient() {
        return direction == PacketDirection.SERVER_TO_CLIENT;
    }
}

