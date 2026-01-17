package me.internalizable.numdrassl.event.mapping.connection;

import com.hypixel.hytale.protocol.packets.connection.Connect;
import me.internalizable.numdrassl.api.event.connection.LoginEvent;
import me.internalizable.numdrassl.event.mapping.PacketContext;
import me.internalizable.numdrassl.event.mapping.PacketEventMapping;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Maps Connect packet (client -> server) to LoginEvent.
 */
public final class ConnectMapping implements PacketEventMapping<Connect, LoginEvent> {

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
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(packet, "packet");

        if (!context.isClientToServer()) {
            return null;
        }

        return new LoginEvent(context.getPlayer());
    }

    @Override
    @Nullable
    public Connect applyChanges(@Nonnull PacketContext context,
                                 @Nonnull Connect packet,
                                 @Nonnull LoginEvent event) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(event, "event");
        return packet;
    }

    @Override
    public boolean isCancelled(@Nonnull LoginEvent event) {
        Objects.requireNonNull(event, "event");
        return !event.getResult().isAllowed();
    }
}
