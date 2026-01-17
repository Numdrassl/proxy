package me.internalizable.numdrassl.server.network;

import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.MaybeBool;
import com.hypixel.hytale.protocol.packets.interface_.ChatType;
import com.hypixel.hytale.protocol.packets.interface_.ServerMessage;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builder for creating formatted chat messages.
 *
 * <p>Provides a fluent API for constructing Hytale {@link FormattedMessage} objects
 * with proper color codes and styling.</p>
 */
public final class ChatMessageBuilder {

    private final List<FormattedMessage> parts = new ArrayList<>();

    private ChatMessageBuilder() {
    }

    /**
     * Creates a new builder instance.
     */
    @Nonnull
    public static ChatMessageBuilder create() {
        return new ChatMessageBuilder();
    }

    /**
     * Appends text with the specified color.
     *
     * @param text the text to append
     * @param hexColor the color in hex format (e.g., "#FF5555")
     * @return this builder
     */
    @Nonnull
    public ChatMessageBuilder text(@Nonnull String text, @Nonnull String hexColor) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(hexColor, "hexColor");

        parts.add(createPart(text, hexColor, false));
        return this;
    }

    /**
     * Appends bold text with the specified color.
     *
     * @param text the text to append
     * @param hexColor the color in hex format
     * @return this builder
     */
    @Nonnull
    public ChatMessageBuilder bold(@Nonnull String text, @Nonnull String hexColor) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(hexColor, "hexColor");

        parts.add(createPart(text, hexColor, true));
        return this;
    }

    /**
     * Appends gold colored text.
     */
    @Nonnull
    public ChatMessageBuilder gold(@Nonnull String text) {
        return text(text, Colors.GOLD);
    }

    /**
     * Appends green colored text.
     */
    @Nonnull
    public ChatMessageBuilder green(@Nonnull String text) {
        return text(text, Colors.GREEN);
    }

    /**
     * Appends red colored text.
     */
    @Nonnull
    public ChatMessageBuilder red(@Nonnull String text) {
        return text(text, Colors.RED);
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
     * Builds the formatted message.
     */
    @Nonnull
    public FormattedMessage build() {
        return new FormattedMessage(
            null,
            null,
            parts.toArray(new FormattedMessage[0]),
            null, null, null,
            MaybeBool.Null,
            MaybeBool.Null,
            MaybeBool.Null,
            MaybeBool.Null,
            null, false
        );
    }

    /**
     * Builds the message wrapped in a ServerMessage packet.
     */
    @Nonnull
    public ServerMessage buildServerMessage() {
        return buildServerMessage(ChatType.Chat);
    }

    /**
     * Builds the message wrapped in a ServerMessage packet with specified type.
     */
    @Nonnull
    public ServerMessage buildServerMessage(@Nonnull ChatType chatType) {
        Objects.requireNonNull(chatType, "chatType");
        return new ServerMessage(chatType, build());
    }

    private FormattedMessage createPart(String text, String color, boolean bold) {
        return new FormattedMessage(
            text,
            null, null, null, null,
            color,
            bold ? MaybeBool.True : MaybeBool.Null,
            MaybeBool.Null,
            MaybeBool.Null,
            MaybeBool.Null,
            null, false
        );
    }

    /**
     * Common color constants.
     */
    public static final class Colors {
        public static final String RED = "#FF5555";
        public static final String GREEN = "#55FF55";
        public static final String GOLD = "#FFAA00";
        public static final String YELLOW = "#FFFF55";
        public static final String AQUA = "#55FFFF";
        public static final String WHITE = "#FFFFFF";
        public static final String GRAY = "#AAAAAA";

        private Colors() {
        }
    }
}

