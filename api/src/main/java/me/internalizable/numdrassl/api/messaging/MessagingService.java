package me.internalizable.numdrassl.api.messaging;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * Service for cross-proxy messaging via pub/sub.
 *
 * <p>Provides publish/subscribe functionality for communication between
 * proxy instances in a distributed deployment. The underlying implementation
 * typically uses Redis, but the API is backend-agnostic.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>All methods are thread-safe. Message handlers are invoked on a dedicated
 * thread pool and should not block.</p>
 *
 * <h2>Connection State</h2>
 * <p>The service handles connection failures gracefully. When disconnected,
 * publish operations fail fast and subscriptions are restored on reconnection.</p>
 *
 * <h2>Programmatic API</h2>
 * <pre>{@code
 * MessagingService messaging = proxy.getMessagingService();
 *
 * // Subscribe to system channel
 * messaging.subscribe(Channels.HEARTBEAT, HeartbeatMessage.class,
 *     (channel, msg) -> logger.info("Proxy {} is alive", msg.sourceProxyId()));
 *
 * // Subscribe to plugin messages
 * messaging.subscribePlugin("my-plugin", "scores", ScoreData.class,
 *     (sourceProxyId, data) -> logger.info("Score: {}", data));
 *
 * // Publish
 * messaging.publishPlugin("my-plugin", "scores", new ScoreData("Steve", 100));
 * }</pre>
 *
 * <h2>Annotation-Based API</h2>
 * <pre>{@code
 * @Plugin(id = "my-plugin", name = "My Plugin", version = "1.0.0")
 * public class MyPlugin {
 *     @Subscribe(channel = "scores")
 *     public void onScore(ScoreData data) {
 *         logger.info("Score: {}", data);
 *     }
 *
 *     @Subscribe(SystemChannel.HEARTBEAT)
 *     public void onHeartbeat(HeartbeatMessage msg) {
 *         logger.info("Proxy {} alive", msg.sourceProxyId());
 *     }
 * }
 *
 * // Register the listener
 * messaging.registerListener(myPlugin);
 * }</pre>
 *
 * @see Subscription
 * @see PluginMessageHandler
 * @see Subscribe
 */
public interface MessagingService {

    /**
     * Check if the messaging service is connected and operational.
     *
     * @return true if connected to the messaging backend
     */
    boolean isConnected();

    // ==================== Publishing ====================

    /**
     * Publish a message to a channel.
     *
     * <p>The message is serialized to JSON and published to all subscribers
     * across all proxy instances.</p>
     *
     * @param channel the channel to publish to
     * @param message the message to publish
     * @return a future that completes when the message is sent
     */
    @Nonnull
    CompletableFuture<Void> publish(@Nonnull MessageChannel channel, @Nonnull ChannelMessage message);

    /**
     * Publish a plugin-specific message to other proxies.
     *
     * <p>This is a convenience method that wraps your custom data in a
     * {@link me.internalizable.numdrassl.api.messaging.message.PluginMessage}
     * and publishes it to the plugin channel.</p>
     *
     * @param pluginId your plugin's unique identifier
     * @param channel the sub-channel within your plugin
     * @param data the data object to send (will be serialized to JSON)
     * @return a future that completes when the message is sent
     */
    @Nonnull
    CompletableFuture<Void> publishPlugin(
            @Nonnull String pluginId,
            @Nonnull String channel,
            @Nonnull Object data);

    // ==================== Subscribing ====================

    /**
     * Subscribe to messages on a channel.
     *
     * <p>The handler is invoked for each message received on the channel.
     * Messages from the local proxy are filtered out by default.</p>
     *
     * @param channel the channel to subscribe to
     * @param handler the handler to invoke for each message
     * @return a subscription that can be used to unsubscribe
     */
    @Nonnull
    Subscription subscribe(@Nonnull MessageChannel channel, @Nonnull MessageHandler<ChannelMessage> handler);

    /**
     * Subscribe to messages of a specific type on a channel.
     *
     * <p>Only messages matching the specified type are passed to the handler.</p>
     *
     * @param channel the channel to subscribe to
     * @param messageType the message type to filter for
     * @param handler the handler to invoke for matching messages
     * @param <T> the message type
     * @return a subscription that can be used to unsubscribe
     */
    @Nonnull
    <T extends ChannelMessage> Subscription subscribe(
            @Nonnull MessageChannel channel,
            @Nonnull Class<T> messageType,
            @Nonnull MessageHandler<T> handler);

    /**
     * Subscribe to messages on a channel, including messages from the local proxy.
     *
     * @param channel the channel to subscribe to
     * @param handler the handler to invoke for each message
     * @return a subscription that can be used to unsubscribe
     */
    @Nonnull
    Subscription subscribeIncludingSelf(@Nonnull MessageChannel channel, @Nonnull MessageHandler<ChannelMessage> handler);

    /**
     * Subscribe to plugin-specific messages with automatic deserialization.
     *
     * <p>This provides a type-safe way to receive custom plugin messages.</p>
     *
     * @param pluginId the plugin identifier to listen for
     * @param channel the sub-channel within the plugin
     * @param dataType the class of your custom data type
     * @param handler the handler to invoke with deserialized data
     * @param <T> your custom data type
     * @return a subscription that can be used to unsubscribe
     */
    @Nonnull
    <T> Subscription subscribePlugin(
            @Nonnull String pluginId,
            @Nonnull String channel,
            @Nonnull Class<T> dataType,
            @Nonnull PluginMessageHandler<T> handler);

    /**
     * Unsubscribe all handlers from a channel.
     *
     * @param channel the channel to unsubscribe from
     */
    void unsubscribeAll(@Nonnull MessageChannel channel);

    // ==================== Annotation-Based API ====================

    /**
     * Register a listener object containing {@link Subscribe}-annotated methods.
     *
     * <p>The plugin ID for plugin message subscriptions is inferred from:</p>
     * <ol>
     *   <li>The {@code @Plugin} annotation on the listener class itself</li>
     *   <li>The {@code @Plugin} annotation on any enclosing class (for inner classes)</li>
     * </ol>
     *
     * <p>If the listener is a separate class without {@code @Plugin}, use
     * {@link #registerListener(Object, Object)} instead.</p>
     *
     * @param listener the listener object
     * @return a composite subscription that can unsubscribe all methods at once
     * @throws IllegalArgumentException if plugin ID cannot be inferred for plugin subscriptions
     */
    @Nonnull
    Subscription registerListener(@Nonnull Object listener);

    /**
     * Register a listener object with explicit plugin context.
     *
     * <p>Use this method when your listener is a separate class that doesn't have
     * the {@code @Plugin} annotation.</p>
     *
     * @param listener the listener object
     * @param plugin the plugin instance (must have @Plugin annotation)
     * @return a composite subscription that can unsubscribe all methods at once
     * @throws IllegalArgumentException if plugin doesn't have @Plugin annotation
     */
    @Nonnull
    Subscription registerListener(@Nonnull Object listener, @Nonnull Object plugin);

    /**
     * Unregister a listener previously registered with {@link #registerListener(Object)}.
     *
     * @param listener the listener to unregister
     */
    void unregisterListener(@Nonnull Object listener);

    // ==================== Type Adapters ====================

    /**
     * Register a custom type adapter for message serialization.
     *
     * <p>Type adapters provide custom serialization/deserialization logic
     * for specific types.</p>
     *
     * @param adapter the type adapter to register
     * @param <T> the type handled by the adapter
     */
    <T> void registerTypeAdapter(@Nonnull TypeAdapter<T> adapter);

    /**
     * Unregister a type adapter.
     *
     * @param type the type to unregister the adapter for
     */
    void unregisterTypeAdapter(@Nonnull Class<?> type);
}

