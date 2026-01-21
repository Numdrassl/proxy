package me.internalizable.numdrassl.messaging.subscription;

import me.internalizable.numdrassl.api.messaging.ChannelMessage;
import me.internalizable.numdrassl.api.messaging.channel.MessageChannel;
import me.internalizable.numdrassl.api.messaging.handler.MessageHandler;

/**
 * Internal data structure holding subscription metadata.
 *
 * <p>Tracks the handler, message type filter, and whether to include
 * self-originated messages.</p>
 */
public final class SubscriptionEntry {

    private final long id;
    private final MessageChannel channel;
    private final MessageHandler<ChannelMessage> handler;
    private final Class<? extends ChannelMessage> messageType;
    private final boolean includeSelf;
    private volatile boolean active = true;

    public SubscriptionEntry(
            long id,
            MessageChannel channel,
            MessageHandler<ChannelMessage> handler,
            Class<? extends ChannelMessage> messageType,
            boolean includeSelf) {
        this.id = id;
        this.channel = channel;
        this.handler = handler;
        this.messageType = messageType;
        this.includeSelf = includeSelf;
    }

    public long getId() {
        return id;
    }

    public MessageChannel getChannel() {
        return channel;
    }

    public MessageHandler<ChannelMessage> getHandler() {
        return handler;
    }

    public Class<? extends ChannelMessage> getMessageType() {
        return messageType;
    }

    public boolean isIncludeSelf() {
        return includeSelf;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}

