package me.internalizable.numdrassl.event.packet;

import com.hypixel.hytale.protocol.Packet;
import me.internalizable.numdrassl.session.ProxySession;

import javax.annotation.Nonnull;

/**
 * Listener interface for packet events.
 *
 * <p>Implement this to intercept, modify, or cancel packets flowing through the proxy.</p>
 */
public interface PacketListener {

    /**
     * Called when a packet is received from a client heading to the backend server.
     *
     * @param event the packet event
     * @return the packet to forward (may be modified), or null to cancel
     */
    default <T extends Packet> T onClientPacket(@Nonnull PacketEvent<T> event) {
        return event.isCancelled() ? null : event.getPacket();
    }

    /**
     * Called when a packet is received from the backend server heading to the client.
     *
     * @param event the packet event
     * @return the packet to forward (may be modified), or null to cancel
     */
    default <T extends Packet> T onServerPacket(@Nonnull PacketEvent<T> event) {
        return event.isCancelled() ? null : event.getPacket();
    }

    /**
     * Called when a new session is established (client connected).
     */
    default void onSessionCreated(@Nonnull ProxySession session) {
    }

    /**
     * Called when a session is closed.
     */
    default void onSessionClosed(@Nonnull ProxySession session) {
    }
}

