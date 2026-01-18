package me.internalizable.numdrassl.api.chat;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Builder for creating formatted chat messages.
 *
 * <p>Provides a fluent API for constructing formatted messages
 * with colors, bold, italic, underline, and other styling.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ChatMessageBuilder message = ChatMessageBuilder.create()
 *     .green("[Success] ")
 *     .white("Operation completed!");
 *
 * player.sendMessage(message);
 * }</pre>
 *
 * @see FormattedMessagePart
 */
public final class ChatMessageBuilder {

    private final List<FormattedMessagePart> parts = new ArrayList<>();

    private ChatMessageBuilder() {
    }

    /**
     * Creates a new builder instance.
     *
     * @return a new ChatMessageBuilder
     */
    @Nonnull
    public static ChatMessageBuilder create() {
        return new ChatMessageBuilder();
    }

    // ==================== Text with Color ====================

    /**
     * Appends text with the specified hex color.
     *
     * @param text the text to append
     * @param hexColor the color in hex format (e.g., "#FF5555")
     * @return this builder
     */
    @Nonnull
    public ChatMessageBuilder text(@Nonnull String text, @Nonnull String hexColor) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(hexColor, "hexColor");
        parts.add(createPart(text, hexColor, null, null, null, null));
        return this;
    }

    /**
     * Appends bold text with the specified hex color.
     *
     * @param text the text to append
     * @param hexColor the color in hex format
     * @return this builder
     */
    @Nonnull
    public ChatMessageBuilder bold(@Nonnull String text, @Nonnull String hexColor) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(hexColor, "hexColor");
        parts.add(createPart(text, hexColor, true, null, null, null));
        return this;
    }

    /**
     * Appends italic text with the specified hex color.
     *
     * @param text the text to append
     * @param hexColor the color in hex format
     * @return this builder
     */
    @Nonnull
    public ChatMessageBuilder italic(@Nonnull String text, @Nonnull String hexColor) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(hexColor, "hexColor");
        parts.add(createPart(text, hexColor, null, true, null, null));
        return this;
    }

    /**
     * Appends underlined text with the specified hex color.
     *
     * @param text the text to append
     * @param hexColor the color in hex format
     * @return this builder
     */
    @Nonnull
    public ChatMessageBuilder underline(@Nonnull String text, @Nonnull String hexColor) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(hexColor, "hexColor");
        parts.add(createPart(text, hexColor, null, null, true, null));
        return this;
    }

    /**
     * Appends strikethrough text with the specified hex color.
     *
     * @param text the text to append
     * @param hexColor the color in hex format
     * @return this builder
     */
    @Nonnull
    public ChatMessageBuilder strikethrough(@Nonnull String text, @Nonnull String hexColor) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(hexColor, "hexColor");
        parts.add(createPart(text, hexColor, null, null, null, true));
        return this;
    }

    // ==================== Color Shortcuts ====================

    /**
     * Appends red colored text.
     */
    @Nonnull
    public ChatMessageBuilder red(@Nonnull String text) {
        return text(text, Colors.RED);
    }

    /**
     * Appends dark red colored text.
     */
    @Nonnull
    public ChatMessageBuilder darkRed(@Nonnull String text) {
        return text(text, Colors.DARK_RED);
    }

    /**
     * Appends green colored text.
     */
    @Nonnull
    public ChatMessageBuilder green(@Nonnull String text) {
        return text(text, Colors.GREEN);
    }

    /**
     * Appends dark green colored text.
     */
    @Nonnull
    public ChatMessageBuilder darkGreen(@Nonnull String text) {
        return text(text, Colors.DARK_GREEN);
    }

    /**
     * Appends gold/orange colored text.
     */
    @Nonnull
    public ChatMessageBuilder gold(@Nonnull String text) {
        return text(text, Colors.GOLD);
    }

    /**
     * Appends yellow colored text.
     */
    @Nonnull
    public ChatMessageBuilder yellow(@Nonnull String text) {
        return text(text, Colors.YELLOW);
    }

    /**
     * Appends aqua/cyan colored text.
     */
    @Nonnull
    public ChatMessageBuilder aqua(@Nonnull String text) {
        return text(text, Colors.AQUA);
    }

    /**
     * Appends dark aqua colored text.
     */
    @Nonnull
    public ChatMessageBuilder darkAqua(@Nonnull String text) {
        return text(text, Colors.DARK_AQUA);
    }

    /**
     * Appends blue colored text.
     */
    @Nonnull
    public ChatMessageBuilder blue(@Nonnull String text) {
        return text(text, Colors.BLUE);
    }

    /**
     * Appends dark blue colored text.
     */
    @Nonnull
    public ChatMessageBuilder darkBlue(@Nonnull String text) {
        return text(text, Colors.DARK_BLUE);
    }

    /**
     * Appends purple/magenta colored text.
     */
    @Nonnull
    public ChatMessageBuilder purple(@Nonnull String text) {
        return text(text, Colors.PURPLE);
    }

    /**
     * Appends dark purple colored text.
     */
    @Nonnull
    public ChatMessageBuilder darkPurple(@Nonnull String text) {
        return text(text, Colors.DARK_PURPLE);
    }

    /**
     * Appends white colored text.
     */
    @Nonnull
    public ChatMessageBuilder white(@Nonnull String text) {
        return text(text, Colors.WHITE);
    }

    /**
     * Appends gray colored text.
     */
    @Nonnull
    public ChatMessageBuilder gray(@Nonnull String text) {
        return text(text, Colors.GRAY);
    }

    /**
     * Appends dark gray colored text.
     */
    @Nonnull
    public ChatMessageBuilder darkGray(@Nonnull String text) {
        return text(text, Colors.DARK_GRAY);
    }

    /**
     * Appends black colored text.
     */
    @Nonnull
    public ChatMessageBuilder black(@Nonnull String text) {
        return text(text, Colors.BLACK);
    }

    // ==================== Build ====================

    /**
     * Gets all the message parts.
     *
     * @return an unmodifiable list of message parts
     */
    @Nonnull
    public List<FormattedMessagePart> getParts() {
        return Collections.unmodifiableList(parts);
    }

    /**
     * Builds the formatted message as a single part containing all children.
     *
     * @return the built FormattedMessagePart
     */
    @Nonnull
    public FormattedMessagePart build() {
        return new SimpleFormattedMessagePart(
            null, null, null, null, null, null,
            new ArrayList<>(parts)
        );
    }

    // ==================== Internal ====================

    private FormattedMessagePart createPart(
            String text, String color,
            Boolean bold, Boolean italic,
            Boolean underlined, Boolean strikethrough) {
        return new SimpleFormattedMessagePart(
            text, color, bold, italic, underlined, strikethrough,
            Collections.emptyList()
        );
    }

    /**
     * Common color constants in hex format.
     *
     * <p>These match the standard Minecraft color codes for familiarity.</p>
     */
    public static final class Colors {
        /** Bright red (#FF5555) */
        public static final String RED = "#FF5555";
        /** Dark red (#AA0000) */
        public static final String DARK_RED = "#AA0000";
        /** Bright green (#55FF55) */
        public static final String GREEN = "#55FF55";
        /** Dark green (#00AA00) */
        public static final String DARK_GREEN = "#00AA00";
        /** Gold/Orange (#FFAA00) */
        public static final String GOLD = "#FFAA00";
        /** Bright yellow (#FFFF55) */
        public static final String YELLOW = "#FFFF55";
        /** Aqua/Cyan (#55FFFF) */
        public static final String AQUA = "#55FFFF";
        /** Dark aqua (#00AAAA) */
        public static final String DARK_AQUA = "#00AAAA";
        /** Bright blue (#5555FF) */
        public static final String BLUE = "#5555FF";
        /** Dark blue (#0000AA) */
        public static final String DARK_BLUE = "#0000AA";
        /** Purple/Magenta (#FF55FF) */
        public static final String PURPLE = "#FF55FF";
        /** Dark purple (#AA00AA) */
        public static final String DARK_PURPLE = "#AA00AA";
        /** White (#FFFFFF) */
        public static final String WHITE = "#FFFFFF";
        /** Gray (#AAAAAA) */
        public static final String GRAY = "#AAAAAA";
        /** Dark gray (#555555) */
        public static final String DARK_GRAY = "#555555";
        /** Black (#000000) */
        public static final String BLACK = "#000000";

        private Colors() {
        }
    }
}

