package me.internalizable.numdrassl.api.messaging.handler;

import me.internalizable.numdrassl.api.messaging.ChannelMessage;
import me.internalizable.numdrassl.api.messaging.channel.MessageChannel;

import javax.annotation.Nonnull;

/**
 * Handler for incoming messages on a subscribed channel.
 *
 * @param <T> the expected message type
 */
@FunctionalInterface
public interface MessageHandler<T extends ChannelMessage> {

    /**
     * Handle an incoming message.
     *
     * <p>Implementations should be fast and non-blocking. Heavy processing
     * should be offloaded to a separate thread.</p>
     *
     * @param channel the channel the message was received on
     * @param message the message payload
     */
    void handle(@Nonnull MessageChannel channel, @Nonnull T message);
}

