package me.internalizable.numdrassl.event.mapping.player;

import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import me.internalizable.numdrassl.api.event.player.PlayerMoveEvent;
import me.internalizable.numdrassl.event.PacketContext;
import me.internalizable.numdrassl.event.PacketEventMapping;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Maps ClientMovement packet to PlayerMoveEvent.
 *
 * <p>Protocol: {@link com.hypixel.hytale.protocol.packets.player.ClientMovement}</p>
 * <p>Event: {@link PlayerMoveEvent}</p>
 */
public class ClientMovementMapping implements PacketEventMapping<ClientMovement, PlayerMoveEvent> {

    @Override
    @Nonnull
    public Class<ClientMovement> getPacketClass() {
        return ClientMovement.class;
    }

    @Override
    @Nonnull
    public Class<PlayerMoveEvent> getEventClass() {
        return PlayerMoveEvent.class;
    }

    @Override
    @Nullable
    public PlayerMoveEvent createEvent(@Nonnull PacketContext context, @Nonnull ClientMovement packet) {
        if (!context.isClientToServer()) {
            return null;
        }

        // Extract position data
        double toX = 0, toY = 0, toZ = 0;
        if (packet.absolutePosition != null) {
            toX = packet.absolutePosition.x;
            toY = packet.absolutePosition.y;
            toZ = packet.absolutePosition.z;
        }

        // Extract rotation data
        float toYaw = 0, toPitch = 0;
        if (packet.lookOrientation != null) {
            toYaw = packet.lookOrientation.yaw;
            toPitch = packet.lookOrientation.pitch;
        }

        // Note: We don't have "from" values without tracking state per player
        // For now, use the same values (plugins can track state themselves)
        return new PlayerMoveEvent(
            context.getPlayer(),
            toX, toY, toZ,  // from (same as to for now)
            toX, toY, toZ,  // to
            toYaw, toPitch, // from rotation
            toYaw, toPitch  // to rotation
        );
    }

    @Override
    @Nullable
    public ClientMovement applyChanges(@Nonnull PacketContext context,
                                        @Nonnull ClientMovement packet,
                                        @Nonnull PlayerMoveEvent event) {
        // Apply modified position if changed
        if (packet.absolutePosition != null) {
            packet.absolutePosition.x = event.getToX();
            packet.absolutePosition.y = event.getToY();
            packet.absolutePosition.z = event.getToZ();
        }

        // Apply modified rotation if changed
        if (packet.lookOrientation != null) {
            packet.lookOrientation.yaw = event.getToYaw();
            packet.lookOrientation.pitch = event.getToPitch();
        }

        return packet;
    }

    @Override
    public boolean isCancelled(@Nonnull PlayerMoveEvent event) {
        return event.isCancelled();
    }
}

