package me.internalizable.numdrassl.event.mapping.interface_;

import com.hypixel.hytale.protocol.packets.interface_.ServerMessage;
import me.internalizable.numdrassl.api.event.server.ServerMessageEvent;
import me.internalizable.numdrassl.event.PacketContext;
import me.internalizable.numdrassl.event.PacketEventMapping;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Maps ServerMessage packet (server -> client) to ServerMessageEvent.
 *
 * <p>Protocol: {@link com.hypixel.hytale.protocol.packets.interface_.ServerMessage}</p>
 * <p>Event: {@link ServerMessageEvent}</p>
 */
public class ServerMessageMapping implements PacketEventMapping<ServerMessage, ServerMessageEvent> {

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
        if (!context.isServerToClient()) {
            return null;
        }

        // Extract message text from FormattedMessage
        String messageText = "";
        if (packet.message != null) {
            // FormattedMessage has a toString or similar - simplified for now
            messageText = packet.message.toString();
        }

        ServerMessageEvent.MessageType type = ServerMessageEvent.MessageType.CHAT;

        return new ServerMessageEvent(context.getPlayer(), type, messageText);
    }

    @Override
    @Nullable
    public ServerMessage applyChanges(@Nonnull PacketContext context,
                                       @Nonnull ServerMessage packet,
                                       @Nonnull ServerMessageEvent event) {
        // Note: Modifying FormattedMessage is complex, may need to reconstruct
        // For now, pass through unchanged
        return packet;
    }

    @Override
    public boolean isCancelled(@Nonnull ServerMessageEvent event) {
        return event.isCancelled();
    }
}
