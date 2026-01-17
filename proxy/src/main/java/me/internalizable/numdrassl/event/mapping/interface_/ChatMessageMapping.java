package me.internalizable.numdrassl.event.mapping.interface_;

import com.hypixel.hytale.protocol.packets.interface_.ChatMessage;
import me.internalizable.numdrassl.api.event.Cancellable;
import me.internalizable.numdrassl.api.event.player.PlayerChatEvent;
import me.internalizable.numdrassl.api.event.player.PlayerCommandEvent;
import me.internalizable.numdrassl.event.mapping.PacketContext;
import me.internalizable.numdrassl.event.mapping.PacketEventMapping;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Maps ChatMessage packet (client -> server) to PlayerChatEvent or PlayerCommandEvent.
 */
public final class ChatMessageMapping implements PacketEventMapping<ChatMessage, Object> {

    private static final String COMMAND_PREFIX = "/";

    @Override
    @Nonnull
    public Class<ChatMessage> getPacketClass() {
        return ChatMessage.class;
    }

    @Override
    @Nonnull
    public Class<Object> getEventClass() {
        return Object.class; // Can be PlayerChatEvent or PlayerCommandEvent
    }

    @Override
    @Nullable
    public Object createEvent(@Nonnull PacketContext context, @Nonnull ChatMessage packet) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(packet, "packet");

        if (!context.isClientToServer()) {
            return null;
        }

        String message = packet.message;
        if (message == null || message.isEmpty()) {
            return null;
        }

        return message.startsWith(COMMAND_PREFIX)
            ? new PlayerCommandEvent(context.getPlayer(), message)
            : new PlayerChatEvent(context.getPlayer(), message);
    }

    @Override
    @Nullable
    public ChatMessage applyChanges(@Nonnull PacketContext context,
                                     @Nonnull ChatMessage packet,
                                     @Nonnull Object event) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(event, "event");

        if (event instanceof PlayerCommandEvent cmdEvent) {
            if (!cmdEvent.shouldForwardToServer()) {
                return null;
            }
            packet.message = cmdEvent.getCommandLine();
        } else if (event instanceof PlayerChatEvent chatEvent) {
            packet.message = chatEvent.getMessage();
        }

        return packet;
    }

    @Override
    public boolean isCancelled(@Nonnull Object event) {
        Objects.requireNonNull(event, "event");
        return event instanceof Cancellable cancellable && cancellable.isCancelled();
    }
}
