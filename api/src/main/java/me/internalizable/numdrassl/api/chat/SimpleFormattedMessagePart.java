package me.internalizable.numdrassl.api.chat;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simple implementation of {@link FormattedMessagePart}.
 */
final class SimpleFormattedMessagePart implements FormattedMessagePart {

    private final String text;
    private final String color;
    private final Boolean bold;
    private final Boolean italic;
    private final Boolean underlined;
    private final Boolean strikethrough;
    private final List<FormattedMessagePart> children;

    SimpleFormattedMessagePart(
            @Nullable String text,
            @Nullable String color,
            @Nullable Boolean bold,
            @Nullable Boolean italic,
            @Nullable Boolean underlined,
            @Nullable Boolean strikethrough,
            @Nonnull List<FormattedMessagePart> children) {
        this.text = text;
        this.color = color;
        this.bold = bold;
        this.italic = italic;
        this.underlined = underlined;
        this.strikethrough = strikethrough;
        this.children = Collections.unmodifiableList(new ArrayList<>(children));
    }

    @Override
    @Nullable
    public String getText() {
        return text;
    }

    @Override
    @Nullable
    public String getColor() {
        return color;
    }

    @Override
    @Nullable
    public Boolean isBold() {
        return bold;
    }

    @Override
    @Nullable
    public Boolean isItalic() {
        return italic;
    }

    @Override
    @Nullable
    public Boolean isUnderlined() {
        return underlined;
    }

    @Override
    @Nullable
    public Boolean isStrikethrough() {
        return strikethrough;
    }

    @Override
    @Nonnull
    public List<FormattedMessagePart> getChildren() {
        return children;
    }
}

