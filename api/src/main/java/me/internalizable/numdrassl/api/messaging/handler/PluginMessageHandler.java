package me.internalizable.numdrassl.api.messaging.handler;

import javax.annotation.Nonnull;

/**
 * Handler for typed plugin messages received from other proxy instances.
 *
 * <p>This functional interface is used with
 * {@link me.internalizable.numdrassl.api.messaging.MessagingService#subscribePlugin}
 * to receive custom plugin data with automatic deserialization.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Define your custom data type
 * record ScoreData(String playerName, int score) {}
 *
 * // Create a handler
 * PluginMessageHandler<ScoreData> handler = (sourceProxyId, data) -> {
 *     logger.info("Player {} scored {} (from proxy {})",
 *         data.playerName(), data.score(), sourceProxyId);
 * };
 *
 * // Subscribe
 * messaging.subscribePlugin("my-plugin", "scores", ScoreData.class, handler);
 * }</pre>
 *
 * @param <T> the custom data type
 * @see me.internalizable.numdrassl.api.messaging.MessagingService#subscribePlugin(String, String, Class, PluginMessageHandler)
 */
@FunctionalInterface
public interface PluginMessageHandler<T> {

    /**
     * Handle a received plugin message.
     *
     * <p>This method is invoked on a message handling thread. Implementations
     * should not block for extended periods.</p>
     *
     * @param sourceProxyId the ID of the proxy that sent the message
     * @param data the deserialized custom data
     */
    void handle(@Nonnull String sourceProxyId, @Nonnull T data);
}

