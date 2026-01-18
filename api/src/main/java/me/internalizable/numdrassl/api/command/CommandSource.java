package me.internalizable.numdrassl.api.command;

import me.internalizable.numdrassl.api.chat.ChatMessageBuilder;
import me.internalizable.numdrassl.api.chat.FormattedMessagePart;
import me.internalizable.numdrassl.api.permission.PermissionFunction;
import me.internalizable.numdrassl.api.permission.PermissionSubject;
import me.internalizable.numdrassl.api.permission.Tristate;
import me.internalizable.numdrassl.api.player.Player;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Represents a source that can execute commands.
 *
 * <p>This can be a player, the console, or another command source.
 * All command sources are permission subjects and can be checked for permissions.</p>
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
        Optional<Player> player = asPlayer();
        if (player.isPresent()) {
            player.get().sendMessage(builder);
        } else {
            // Strip colors for console
            StringBuilder sb = new StringBuilder();
            for (FormattedMessagePart part : builder.getParts()) {
                if (part.getText() != null) {
                    sb.append(part.getText());
                }
            }
            sendMessage(sb.toString());
        }
    }

    /**
     * Get this source as a player, if applicable.
     *
     * @return the player, or empty if this is not a player
     */
    @Nonnull
    Optional<Player> asPlayer();

    /**
     * Check if this source is a player.
     *
     * @return true if this is a player
     */
    default boolean isPlayer() {
        return asPlayer().isPresent();
    }

    /**
     * Check if this source is the console.
     *
     * @return true if this is the console
     */
    boolean isConsole();

    /**
     * {@inheritDoc}
     *
     * <p>For command sources:</p>
     * <ul>
     *   <li>Console: Returns {@link Tristate#TRUE} for all permissions by default</li>
     *   <li>Player: Delegates to the player's permission function</li>
     * </ul>
     */
    @Override
    @Nonnull
    default Tristate getPermissionValue(@Nonnull String permission) {
        Optional<Player> player = asPlayer();
        if (player.isPresent()) {
            return player.get().getPermissionValue(permission);
        }
        // Console has all permissions by default
        return getPermissionFunction().getPermissionValue(permission);
    }

    /**
     * {@inheritDoc}
     *
     * <p>By default, console sources have all permissions.</p>
     */
    @Override
    default boolean hasPermission(@Nonnull String permission) {
        Optional<Player> player = asPlayer();
        if (player.isPresent()) {
            return player.get().hasPermission(permission);
        }
        // Console has all permissions
        return true;
    }
}

