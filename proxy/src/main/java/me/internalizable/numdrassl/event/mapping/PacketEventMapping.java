package me.internalizable.numdrassl.event.mapping;

import com.hypixel.hytale.protocol.Packet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents a mapping from a protocol packet to a high-level API event.
 *
 * @param <P> the packet type
 * @param <E> the event type
 */
public interface PacketEventMapping<P extends Packet, E> {

    /**
     * Gets the packet class this mapping handles.
     */
    @Nonnull
    Class<P> getPacketClass();

    /**
     * Gets the event class this mapping produces.
     */
    @Nonnull
    Class<E> getEventClass();

    /**
     * Creates an event from the packet.
     *
     * @param context the packet context
     * @param packet the packet
     * @return the event, or null to skip
     */
    @Nullable
    E createEvent(@Nonnull PacketContext context, @Nonnull P packet);

    /**
     * Applies any changes from the event back to the packet.
     *
     * @param context the packet context
     * @param packet the original packet
     * @param event the processed event
     * @return the modified packet, or null to cancel
     */
    @Nullable
    P applyChanges(@Nonnull PacketContext context, @Nonnull P packet, @Nonnull E event);

    /**
     * Checks if the event was cancelled.
     */
    boolean isCancelled(@Nonnull E event);
}

