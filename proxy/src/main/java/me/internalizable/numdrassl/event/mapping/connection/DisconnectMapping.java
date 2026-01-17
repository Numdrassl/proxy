package me.internalizable.numdrassl.event.mapping.connection;

import com.hypixel.hytale.protocol.packets.connection.Disconnect;
import me.internalizable.numdrassl.api.event.connection.DisconnectEvent;
import me.internalizable.numdrassl.event.PacketContext;
import me.internalizable.numdrassl.event.PacketEventMapping;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Maps Disconnect packet to DisconnectEvent.
 *
 * <p>Protocol: {@link com.hypixel.hytale.protocol.packets.connection.Disconnect}</p>
 * <p>Event: {@link DisconnectEvent}</p>
 *
 * <p>Note: This handles server-to-client disconnect packets. Session close events
 * are handled separately by the session lifecycle.</p>
 */
public class DisconnectMapping implements PacketEventMapping<Disconnect, DisconnectEvent> {

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
        if (!context.isServerToClient()) {
            return null; // Only handle server-to-client disconnects
        }

        // Determine reason from packet
        DisconnectEvent.DisconnectReason reason = DisconnectEvent.DisconnectReason.KICKED;
        if (packet.reason != null && packet.reason.toLowerCase().contains("timeout")) {
            reason = DisconnectEvent.DisconnectReason.TIMEOUT;
        }

        return new DisconnectEvent(context.getPlayer(), reason);
    }

    @Override
    @Nullable
    public Disconnect applyChanges(@Nonnull PacketContext context,
                                    @Nonnull Disconnect packet,
                                    @Nonnull DisconnectEvent event) {
        // Disconnect events are informational, can't be modified
        return packet;
    }

    @Override
    public boolean isCancelled(@Nonnull DisconnectEvent event) {
        return false; // Disconnect events can't be cancelled
    }
}

