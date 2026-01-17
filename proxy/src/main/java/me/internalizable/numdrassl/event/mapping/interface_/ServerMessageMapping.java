package me.internalizable.numdrassl.event.mapping.interface_;

import com.hypixel.hytale.protocol.packets.interface_.ServerMessage;
import me.internalizable.numdrassl.api.event.server.ServerMessageEvent;
import me.internalizable.numdrassl.event.mapping.PacketContext;
import me.internalizable.numdrassl.event.mapping.PacketEventMapping;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Maps ServerMessage packet (server -> client) to ServerMessageEvent.
 */
public final class ServerMessageMapping implements PacketEventMapping<ServerMessage, ServerMessageEvent> {

    @Override
    @Nonnull
    public Class<ServerMessage> getPacketClass() {
        return ServerMessage.class;
    }

    @Override
    @Nonnull
    public Class<ServerMessageEvent> getEventClass() {
        return ServerMessageEvent.class;
    }

    @Override
    @Nullable
    public ServerMessageEvent createEvent(@Nonnull PacketContext context, @Nonnull ServerMessage packet) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(packet, "packet");

        if (!context.isServerToClient()) {
            return null;
        }

        String messageText = packet.message != null ? packet.message.toString() : "";
        return new ServerMessageEvent(context.getPlayer(), ServerMessageEvent.MessageType.CHAT, messageText);
    }

    @Override
    @Nullable
    public ServerMessage applyChanges(@Nonnull PacketContext context,
                                       @Nonnull ServerMessage packet,
                                       @Nonnull ServerMessageEvent event) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(event, "event");
        // Note: Modifying FormattedMessage is complex, pass through unchanged
        return packet;
    }

    @Override
    public boolean isCancelled(@Nonnull ServerMessageEvent event) {
        Objects.requireNonNull(event, "event");
        return event.isCancelled();
    }
}
