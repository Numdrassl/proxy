package me.internalizable.numdrassl.event.mapping.inventory;

import com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot;
import me.internalizable.numdrassl.api.event.player.PlayerSlotChangeEvent;
import me.internalizable.numdrassl.event.PacketContext;
import me.internalizable.numdrassl.event.PacketEventMapping;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Maps SetActiveSlot packet to PlayerSlotChangeEvent.
 *
 * <p>Protocol: {@link com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot}</p>
 * <p>Event: {@link PlayerSlotChangeEvent}</p>
 */
public class SetActiveSlotMapping implements PacketEventMapping<SetActiveSlot, PlayerSlotChangeEvent> {

    @Override
    @Nonnull
    public Class<SetActiveSlot> getPacketClass() {
        return SetActiveSlot.class;
    }

    @Override
    @Nonnull
    public Class<PlayerSlotChangeEvent> getEventClass() {
        return PlayerSlotChangeEvent.class;
    }

    @Override
    @Nullable
    public PlayerSlotChangeEvent createEvent(@Nonnull PacketContext context, @Nonnull SetActiveSlot packet) {
        if (!context.isClientToServer()) {
            return null;
        }

        return new PlayerSlotChangeEvent(context.getPlayer(), -1, packet.activeSlot);
    }

    @Override
    @Nullable
    public SetActiveSlot applyChanges(@Nonnull PacketContext context,
                                       @Nonnull SetActiveSlot packet,
                                       @Nonnull PlayerSlotChangeEvent event) {
        // Slot change events are informational, no modifications
        return packet;
    }

    @Override
    public boolean isCancelled(@Nonnull PlayerSlotChangeEvent event) {
        return false; // Not cancellable
    }
}

