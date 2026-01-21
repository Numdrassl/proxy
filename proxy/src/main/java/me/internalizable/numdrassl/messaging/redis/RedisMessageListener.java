package me.internalizable.numdrassl.messaging.redis;

import io.lettuce.core.pubsub.RedisPubSubAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

/**
 * Redis pub/sub listener that delegates message handling.
 *
 * <p>Receives raw messages from Redis and forwards them to the
 * configured handler for processing.</p>
 */
public final class RedisMessageListener extends RedisPubSubAdapter<String, String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisMessageListener.class);

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
        try {
            messageHandler.accept(channel, message);
        } catch (Exception e) {
            LOGGER.error("Failed to handle message on channel '{}': {}", channel, e.getMessage(), e);
        }
    }
}

