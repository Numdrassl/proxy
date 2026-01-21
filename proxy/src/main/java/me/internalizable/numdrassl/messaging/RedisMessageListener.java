package me.internalizable.numdrassl.messaging;

import io.lettuce.core.pubsub.RedisPubSubAdapter;

import java.util.function.BiConsumer;

/**
 * Redis pub/sub listener that delegates message handling.
 *
 * <p>Receives raw messages from Redis and forwards them to the
 * configured handler for processing.</p>
 */
public final class RedisMessageListener extends RedisPubSubAdapter<String, String> {

    private final BiConsumer<String, String> messageHandler;

    /**
     * Create a new Redis message listener.
     *
     * @param messageHandler receives (channel, json) for each message
     */
    public RedisMessageListener(BiConsumer<String, String> messageHandler) {
        this.messageHandler = messageHandler;
    }

    @Override
    public void message(String channel, String message) {
        messageHandler.accept(channel, message);
    }
}

