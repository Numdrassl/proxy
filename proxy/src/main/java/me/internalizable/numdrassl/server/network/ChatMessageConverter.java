package me.internalizable.numdrassl.server.network;

import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.MaybeBool;
import com.hypixel.hytale.protocol.packets.interface_.ChatType;
import com.hypixel.hytale.protocol.packets.interface_.ServerMessage;
import me.internalizable.numdrassl.api.chat.ChatMessageBuilder;
import me.internalizable.numdrassl.api.chat.FormattedMessagePart;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;

/**
 * Converts API chat message types to protocol types.
 *
 * <p>This class bridges the gap between the public API's {@link ChatMessageBuilder}
 * and the internal protocol's {@link ServerMessage} and {@link FormattedMessage}.</p>
 */
public final class ChatMessageConverter {

    private ChatMessageConverter() {
    }

    /**
     * Converts a ChatMessageBuilder to a ServerMessage packet.
     *
     * @param builder the chat message builder
     * @return the ServerMessage packet ready to send
     */
    @Nonnull
    public static ServerMessage toServerMessage(@Nonnull ChatMessageBuilder builder) {
        Objects.requireNonNull(builder, "builder");
        return toServerMessage(builder, ChatType.Chat);
    }

    /**
     * Converts a ChatMessageBuilder to a ServerMessage packet with specified type.
     *
     * @param builder the chat message builder
     * @param chatType the chat type (Chat, System, etc.)
     * @return the ServerMessage packet ready to send
     */
    @Nonnull
    public static ServerMessage toServerMessage(@Nonnull ChatMessageBuilder builder, @Nonnull ChatType chatType) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(chatType, "chatType");

        FormattedMessage formatted = toFormattedMessage(builder.build());
        return new ServerMessage(chatType, formatted);
    }

    /**
     * Converts a FormattedMessagePart to protocol FormattedMessage.
     *
     * @param part the API message part
     * @return the protocol FormattedMessage
     */
    @Nonnull
    public static FormattedMessage toFormattedMessage(@Nonnull FormattedMessagePart part) {
        Objects.requireNonNull(part, "part");

        List<FormattedMessagePart> children = part.getChildren();
        FormattedMessage[] childArray = null;

        if (!children.isEmpty()) {
            childArray = new FormattedMessage[children.size()];
            for (int i = 0; i < children.size(); i++) {
                childArray[i] = toFormattedMessage(children.get(i));
            }
        }

        return new FormattedMessage(
            part.getText(),
            null,  // key
            childArray,
            null,  // args
            null,  // insertion
            part.getColor(),
            toMaybeBool(part.isBold()),
            toMaybeBool(part.isItalic()),
            toMaybeBool(part.isUnderlined()),
            toMaybeBool(part.isStrikethrough()),
            null,  // font
            false  // obfuscated
        );
    }

    private static MaybeBool toMaybeBool(Boolean value) {
        if (value == null) {
            return MaybeBool.Null;
        }
        return value ? MaybeBool.True : MaybeBool.False;
    }
}

