package me.internalizable.numdrassl.event.mapping.connection;

import com.hypixel.hytale.protocol.packets.connection.Connect;
import me.internalizable.numdrassl.api.event.connection.LoginEvent;
import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.event.PacketContext;
import me.internalizable.numdrassl.event.PacketEventMapping;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Maps Connect packet to LoginEvent.
 *
 * <p>Protocol: {@link com.hypixel.hytale.protocol.packets.connection.Connect}</p>
 * <p>Event: {@link LoginEvent}</p>
 */
public class ConnectMapping implements PacketEventMapping<Connect, LoginEvent> {

    @Override
    @Nonnull
    public Class<Connect> getPacketClass() {
        return Connect.class;
    }

    @Override
    @Nonnull
    public Class<LoginEvent> getEventClass() {
        return LoginEvent.class;
    }

    @Override
    @Nullable
    public LoginEvent createEvent(@Nonnull PacketContext context, @Nonnull Connect packet) {
        if (!context.isClientToServer()) {
            return null;
        }

        // The player object is already created by PacketContext
        return new LoginEvent(context.getPlayer());
    }

    @Override
    @Nullable
    public Connect applyChanges(@Nonnull PacketContext context,
                                 @Nonnull Connect packet,
                                 @Nonnull LoginEvent event) {
        // Login events don't modify the packet, just allow/deny
        return packet;
    }

    @Override
    public boolean isCancelled(@Nonnull LoginEvent event) {
        return !event.getResult().isAllowed();
    }
}

