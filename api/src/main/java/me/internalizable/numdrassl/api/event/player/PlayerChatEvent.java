package me.internalizable.numdrassl.api.event.player;

import me.internalizable.numdrassl.api.event.Cancellable;
import me.internalizable.numdrassl.api.player.Player;

import javax.annotation.Nonnull;

/**
 * Event fired when a player sends a chat message.
 * This is fired BEFORE the message is sent to the server.
 *
 * <p>This event is cancellable. If cancelled, the chat message will not be sent.</p>
 */
public class PlayerChatEvent implements Cancellable {

    private final Player player;
    private String message;
    private boolean cancelled;

    public PlayerChatEvent(@Nonnull Player player, @Nonnull String message) {
        this.player = player;
        this.message = message;
        this.cancelled = false;
    }

    /**
     * Get the player who sent the message.
     *
     * @return the player
     */
    @Nonnull
    public Player getPlayer() {
        return player;
    }

    /**
     * Get the chat message.
     *
     * @return the message
     */
    @Nonnull
    public String getMessage() {
        return message;
    }

    /**
     * Set the chat message.
     * This allows plugins to modify the message before it's sent.
     *
     * @param message the new message
     */
    public void setMessage(@Nonnull String message) {
        this.message = message;
    }

    /**
     * Check if this is a command (starts with /).
     *
     * @return true if this is a command
     */
    public boolean isCommand() {
        return message.startsWith("/");
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

