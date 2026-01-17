package me.internalizable.numdrassl.event.mapping.connection;

import com.hypixel.hytale.protocol.packets.connection.Disconnect;
import me.internalizable.numdrassl.api.event.connection.DisconnectEvent;
import me.internalizable.numdrassl.event.mapping.PacketContext;
import me.internalizable.numdrassl.event.mapping.PacketEventMapping;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Maps Disconnect packet (server -> client) to DisconnectEvent.
 *
 * <p>Note: Session close events are handled separately by session lifecycle.</p>
 */
public final class DisconnectMapping implements PacketEventMapping<Disconnect, DisconnectEvent> {

    private static final String TIMEOUT_KEYWORD = "timeout";

    @Override
    @Nonnull
    public Class<Disconnect> getPacketClass() {
        return Disconnect.class;
    }

    @Override
    @Nonnull
    public Class<DisconnectEvent> getEventClass() {
        return DisconnectEvent.class;
    }

    @Override
    @Nullable
    public DisconnectEvent createEvent(@Nonnull PacketContext context, @Nonnull Disconnect packet) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(packet, "packet");

        if (!context.isServerToClient()) {
            return null;
        }

        DisconnectEvent.DisconnectReason reason = determineReason(packet);
        return new DisconnectEvent(context.getPlayer(), reason);
    }

    private DisconnectEvent.DisconnectReason determineReason(Disconnect packet) {
        if (packet.reason != null && packet.reason.toLowerCase().contains(TIMEOUT_KEYWORD)) {
            return DisconnectEvent.DisconnectReason.TIMEOUT;
        }
        return DisconnectEvent.DisconnectReason.KICKED;
    }

    @Override
    @Nullable
    public Disconnect applyChanges(@Nonnull PacketContext context,
                                    @Nonnull Disconnect packet,
                                    @Nonnull DisconnectEvent event) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(event, "event");
        return packet;
    }

    @Override
    public boolean isCancelled(@Nonnull DisconnectEvent event) {
        Objects.requireNonNull(event, "event");
        return false; // Disconnect events can't be cancelled
    }
}
