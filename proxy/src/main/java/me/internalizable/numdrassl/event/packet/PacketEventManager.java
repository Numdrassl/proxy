package me.internalizable.numdrassl.event.packet;

import com.hypixel.hytale.protocol.Packet;
import me.internalizable.numdrassl.session.ProxySession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages internal packet event listeners and dispatches packet events.
 */
public final class PacketEventManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PacketEventManager.class);

    private final List<PacketListener> listeners = new CopyOnWriteArrayList<>();

    public void registerListener(@Nonnull PacketListener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        LOGGER.info("Registered packet listener: {}", listener.getClass().getSimpleName());
    }

    public void unregisterListener(@Nonnull PacketListener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.remove(listener);
    }

    public void clearListeners() {
        listeners.clear();
    }

    @Nullable
    public <T extends Packet> T dispatchClientPacket(@Nonnull ProxySession session, @Nonnull T packet) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(packet, "packet");
        return dispatchPacket(session, packet, PacketDirection.CLIENT_TO_SERVER, true);
    }

    @Nullable
    public <T extends Packet> T dispatchServerPacket(@Nonnull ProxySession session, @Nonnull T packet) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(packet, "packet");
        return dispatchPacket(session, packet, PacketDirection.SERVER_TO_CLIENT, false);
    }

    private <T extends Packet> T dispatchPacket(
            ProxySession session,
            T packet,
            PacketDirection direction,
            boolean isClientPacket) {

        PacketEvent<T> event = new PacketEvent<>(session, direction, packet);

        for (PacketListener listener : listeners) {
            try {
                T result = isClientPacket
                    ? listener.onClientPacket(event)
                    : listener.onServerPacket(event);

                if (result == null || event.isCancelled()) {
                    return null;
                }
                event.setPacket(result);
            } catch (Exception e) {
                LOGGER.error("Error in packet listener {} processing {} packet",
                    listener.getClass().getSimpleName(),
                    isClientPacket ? "client" : "server", e);
            }
        }

        return event.getPacket();
    }

    public void dispatchSessionCreated(@Nonnull ProxySession session) {
        Objects.requireNonNull(session, "session");
        for (PacketListener listener : listeners) {
            try {
                listener.onSessionCreated(session);
            } catch (Exception e) {
                LOGGER.error("Error in packet listener {} handling session created",
                    listener.getClass().getSimpleName(), e);
            }
        }
    }

    public void dispatchSessionClosed(@Nonnull ProxySession session) {
        Objects.requireNonNull(session, "session");
        for (PacketListener listener : listeners) {
            try {
                listener.onSessionClosed(session);
            } catch (Exception e) {
                LOGGER.error("Error in packet listener {} handling session closed",
                    listener.getClass().getSimpleName(), e);
            }
        }
    }
}
