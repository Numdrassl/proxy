package me.internalizable.numdrassl.api.command;

import me.internalizable.numdrassl.api.chat.ChatMessageBuilder;
import me.internalizable.numdrassl.api.chat.FormattedMessagePart;
import me.internalizable.numdrassl.api.permission.PermissionSubject;
import me.internalizable.numdrassl.api.player.Player;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Represents a source that can execute commands.
 *
 * <p>This can be a player, the console, or another command source.
 * All command sources are permission subjects and can be checked for permissions.</p>
 *
 * <p>The {@link Player} interface extends this interface, so all players
 * are also command sources.</p>
 */
public interface CommandSource extends PermissionSubject {

    /**
     * Send a message to this command source.
     *
     * @param message the message to send
     */
    void sendMessage(@Nonnull String message);

    /**
     * Send a formatted message to this command source.
     *
     * <p>If this source is a player, the message is sent with colors.
     * If this source is the console, colors are stripped automatically.</p>
     *
     * @param builder the message builder
     */
    default void sendMessage(@Nonnull ChatMessageBuilder builder) {
        Optional<Player> playerOpt = this.asPlayer();

        if (playerOpt.isPresent()) {
            Player player = playerOpt.get();
            player.sendMessage(builder);
            return;
        }

        // Console fallback: strip formatting and send plain text
        StringBuilder sb = new StringBuilder();
        for (FormattedMessagePart part : builder.getParts()) {
            if (part.getText() != null) {
                sb.append(part.getText());
            }
        }

        sendMessage(sb.toString());
    }

    /**
     * Get this source as a player, if applicable.
     *
     * @return the player, or empty if this is not a player
     */
    @Nonnull
    default Optional<Player> asPlayer() {
        if (this instanceof Player player) {
            return Optional.of(player);
        }
        return Optional.empty();
    }

    /**
     * Check if this source is a player.
     *
     * @return true if this is a player
     */
    default boolean isPlayer() {
        return this.asPlayer().isPresent();
    }

    /**
     * Check if this source is the console.
     *
     * @return true if this is the console
     */
    default boolean isConsole() {
        return !isPlayer();
    }
}
