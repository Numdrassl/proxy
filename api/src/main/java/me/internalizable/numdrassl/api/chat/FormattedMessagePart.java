package me.internalizable.numdrassl.api.chat;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Represents a formatted message that can be sent to players.
 *
 * <p>This interface abstracts the underlying protocol implementation,
 * allowing plugins to create formatted messages without depending on
 * protocol internals.</p>
 */
public interface FormattedMessagePart {

    /**
     * Gets the text content of this part.
     *
     * @return the text, or null if this is a container for children
     */
    @Nullable
    String getText();

    /**
     * Gets the hex color of this part (e.g., "#FF5555").
     *
     * @return the color, or null if not set
     */
    @Nullable
    String getColor();

    /**
     * Whether this part is bold.
     *
     * @return true if bold, false if not, null if unset
     */
    @Nullable
    Boolean isBold();

    /**
     * Whether this part is italic.
     *
     * @return true if italic, false if not, null if unset
     */
    @Nullable
    Boolean isItalic();

    /**
     * Whether this part is underlined.
     *
     * @return true if underlined, false if not, null if unset
     */
    @Nullable
    Boolean isUnderlined();

    /**
     * Whether this part has strikethrough.
     *
     * @return true if strikethrough, false if not, null if unset
     */
    @Nullable
    Boolean isStrikethrough();

    /**
     * Gets child message parts.
     *
     * @return list of children, or empty list if none
     */
    @Nonnull
    List<FormattedMessagePart> getChildren();
}

