package me.internalizable.numdrassl.event;

import com.hypixel.hytale.protocol.Packet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents a mapping from a protocol packet to a high-level API event.
 * This allows automatic translation of low-level packets to meaningful events
 * that plugin developers can work with.
 *
 * @param <P> the packet type
 * @param <E> the event type
 */
public interface PacketEventMapping<P extends Packet, E> {

    /**
     * Get the packet class this mapping handles.
     *
     * @return the packet class
     */
    @Nonnull
    Class<P> getPacketClass();

    /**
     * Get the event class this mapping produces.
     *
     * @return the event class
     */
    @Nonnull
    Class<E> getEventClass();

    /**
     * Create an event from the packet. May return null if the event
     * should not be fired for this particular packet instance.
     *
     * @param context the packet context (player, direction, etc.)
     * @param packet the packet
     * @return the event, or null to skip
     */
    @Nullable
    E createEvent(@Nonnull PacketContext context, @Nonnull P packet);

    /**
     * Apply any changes from the event back to the packet.
     * Called after all event handlers have processed the event.
     *
     * @param context the packet context
     * @param packet the original packet
     * @param event the processed event
     * @return the modified packet, or null if the packet should be cancelled
     */
    @Nullable
    P applyChanges(@Nonnull PacketContext context, @Nonnull P packet, @Nonnull E event);

    /**
     * Check if the event was cancelled.
     *
     * @param event the event
     * @return true if cancelled
     */
    boolean isCancelled(@Nonnull E event);
}

