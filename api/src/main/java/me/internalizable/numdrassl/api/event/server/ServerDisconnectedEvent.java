package me.internalizable.numdrassl.api.event.server;

import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.server.RegisteredServer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Event fired when a backend is disconnected.
 */
public class ServerDisconnectedEvent {

    private final Player player;
    private final RegisteredServer server;
    private final String reason;
    private RegisteredServer fallbackServer;

    public ServerDisconnectedEvent(@Nonnull Player player, @Nonnull RegisteredServer server,
                                   @Nullable String reason) {
        this.player = player;
        this.server = server;
        this.reason = reason;
    }

    /**
     * Get the player who disconnected.
     *
     * @return the player
     */
    @Nonnull
    public Player getPlayer() {
        return player;
    }

    /**
     * Get the server the player disconnected from.
     *
     * @return the server
     */
    @Nonnull
    public RegisteredServer getServer() {
        return server;
    }

    /**
     * Get the reason for the disconnection, if any.
     *
     * @return the disconnect reason, or null
     */
    @Nullable
    public String getReason() {
        return reason;
    }

    public RegisteredServer getFallbackServer() {
        return fallbackServer;
    }

    public void setFallbackServer(RegisteredServer fallbackServer) {
        this.fallbackServer = fallbackServer;
    }

    public ServerDisconnectedResult getResult() {
        return new ServerDisconnectedResult(this.server, this.reason, this.fallbackServer);
    }
}

