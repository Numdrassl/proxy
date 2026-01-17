package me.internalizable.numdrassl.api.event.server;

import me.internalizable.numdrassl.api.event.Cancellable;
import me.internalizable.numdrassl.api.player.Player;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Event fired when the server sends a chat/system message to a player.
 * This event is fired BEFORE the message is delivered to the client.
 *
 * <p>This event is cancellable. If cancelled, the message will not be shown to the player.</p>
 */
public class ServerMessageEvent implements Cancellable {

    /**
     * Type of server message.
     */
    public enum MessageType {
        CHAT,
        SYSTEM,
        ACTION_BAR,
        TITLE,
        SUBTITLE
    }

    private final Player player;
    private final MessageType type;
    private String message;
    private boolean cancelled;

    public ServerMessageEvent(@Nonnull Player player, @Nonnull MessageType type, @Nullable String message) {
        this.player = player;
        this.type = type;
        this.message = message != null ? message : "";
        this.cancelled = false;
    }

    /**
     * Get the player receiving the message.
     *
     * @return the player
     */
    @Nonnull
    public Player getPlayer() {
        return player;
    }

    /**
     * Get the type of message.
     *
     * @return the message type
     */
    @Nonnull
    public MessageType getType() {
        return type;
    }

    /**
     * Get the message content.
     *
     * @return the message
     */
    @Nonnull
    public String getMessage() {
        return message;
    }

    /**
     * Set the message content.
     *
     * @param message the new message
     */
    public void setMessage(@Nonnull String message) {
        this.message = message;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}

