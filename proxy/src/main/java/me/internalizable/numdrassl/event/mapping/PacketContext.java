package me.internalizable.numdrassl.event.mapping;

import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.session.ProxySession;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Context for packet-to-event translation.
 *
 * <p>Provides access to the session, player, and direction for packet mappings.</p>
 */
public final class PacketContext {

    /**
     * Direction of packet flow.
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

    public PacketContext(
            @Nonnull ProxySession session,
            @Nonnull Player player,
            @Nonnull Direction direction) {
        this.session = Objects.requireNonNull(session, "session");
        this.player = Objects.requireNonNull(player, "player");
        this.direction = Objects.requireNonNull(direction, "direction");
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

