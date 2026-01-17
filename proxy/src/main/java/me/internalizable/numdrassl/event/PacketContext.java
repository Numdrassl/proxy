package me.internalizable.numdrassl.event;

import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.session.ProxySession;

import javax.annotation.Nonnull;

/**
 * Context for packet-to-event translation.
 */
public class PacketContext {

    /**
     * Direction of the packet.
     */
    public enum Direction {
        /** Packet traveling from client to server (serverbound) */
        CLIENT_TO_SERVER,
        /** Packet traveling from server to client (clientbound) */
        SERVER_TO_CLIENT
    }

    private final ProxySession session;
    private final Player player;
    private final Direction direction;

    public PacketContext(@Nonnull ProxySession session, @Nonnull Player player, @Nonnull Direction direction) {
        this.session = session;
        this.player = player;
        this.direction = direction;
    }

    @Nonnull
    public ProxySession getSession() {
        return session;
    }

    @Nonnull
    public Player getPlayer() {
        return player;
    }

    @Nonnull
    public Direction getDirection() {
        return direction;
    }

    public boolean isClientToServer() {
        return direction == Direction.CLIENT_TO_SERVER;
    }

    public boolean isServerToClient() {
        return direction == Direction.SERVER_TO_CLIENT;
    }
}

