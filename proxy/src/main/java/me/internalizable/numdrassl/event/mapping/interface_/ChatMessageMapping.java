package me.internalizable.numdrassl.event.mapping.interface_;

import com.hypixel.hytale.protocol.packets.interface_.ChatMessage;
import me.internalizable.numdrassl.api.event.Cancellable;
import me.internalizable.numdrassl.api.event.player.PlayerChatEvent;
import me.internalizable.numdrassl.api.event.player.PlayerCommandEvent;
import me.internalizable.numdrassl.event.PacketContext;
import me.internalizable.numdrassl.event.PacketEventMapping;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Maps ChatMessage packet (client -> server) to PlayerChatEvent or PlayerCommandEvent.
 *
 * <p>Protocol: {@link com.hypixel.hytale.protocol.packets.interface_.ChatMessage}</p>
 * <p>Events: {@link PlayerChatEvent}, {@link PlayerCommandEvent}</p>
 */
public class ChatMessageMapping implements PacketEventMapping<ChatMessage, Object> {

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
        if (!context.isClientToServer()) {
            return null; // Only handle client-to-server
        }

        String message = packet.message;
        if (message == null || message.isEmpty()) {
            return null;
        }

        if (message.startsWith("/")) {
            // Command
            return new PlayerCommandEvent(context.getPlayer(), message);
        } else {
            // Chat
            return new PlayerChatEvent(context.getPlayer(), message);
        }
    }

    @Override
    @Nullable
    public ChatMessage applyChanges(@Nonnull PacketContext context,
                                     @Nonnull ChatMessage packet,
                                     @Nonnull Object event) {
        if (event instanceof PlayerCommandEvent cmdEvent) {
            // If handled by proxy, don't forward to server
            if (!cmdEvent.shouldForwardToServer()) {
                return null;
            }
            // Update message with potentially modified command
            packet.message = cmdEvent.getCommandLine();
            return packet;
        }

        if (event instanceof PlayerChatEvent chatEvent) {
            // Update message with potentially modified content
            packet.message = chatEvent.getMessage();
            return packet;
        }

        return packet;
    }

    @Override
    public boolean isCancelled(@Nonnull Object event) {
        if (event instanceof Cancellable cancellable) {
            return cancellable.isCancelled();
        }
        return false;
    }
}

