package me.internalizable.numdrassl.event.mapping.player;

import com.hypixel.hytale.protocol.packets.player.ClientPlaceBlock;
import me.internalizable.numdrassl.api.event.player.PlayerBlockPlaceEvent;
import me.internalizable.numdrassl.event.PacketContext;
import me.internalizable.numdrassl.event.PacketEventMapping;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Maps ClientPlaceBlock packet to PlayerBlockPlaceEvent.
 *
 * <p>Protocol: {@link com.hypixel.hytale.protocol.packets.player.ClientPlaceBlock}</p>
 * <p>Event: {@link PlayerBlockPlaceEvent}</p>
 */
public class ClientPlaceBlockMapping implements PacketEventMapping<ClientPlaceBlock, PlayerBlockPlaceEvent> {

    @Override
    @Nonnull
    public Class<ClientPlaceBlock> getPacketClass() {
        return ClientPlaceBlock.class;
    }

    @Override
    @Nonnull
    public Class<PlayerBlockPlaceEvent> getEventClass() {
        return PlayerBlockPlaceEvent.class;
    }

    @Override
    @Nullable
    public PlayerBlockPlaceEvent createEvent(@Nonnull PacketContext context, @Nonnull ClientPlaceBlock packet) {
        if (!context.isClientToServer()) {
            return null;
        }

        int x = 0, y = 0, z = 0;
        if (packet.position != null) {
            x = packet.position.x;
            y = packet.position.y;
            z = packet.position.z;
        }

        return new PlayerBlockPlaceEvent(context.getPlayer(), x, y, z, packet.placedBlockId);
    }

    @Override
    @Nullable
    public ClientPlaceBlock applyChanges(@Nonnull PacketContext context,
                                          @Nonnull ClientPlaceBlock packet,
                                          @Nonnull PlayerBlockPlaceEvent event) {
        // Block place events are typically just cancelled or allowed, no modifications
        return packet;
    }

    @Override
    public boolean isCancelled(@Nonnull PlayerBlockPlaceEvent event) {
        return event.isCancelled();
    }
}

