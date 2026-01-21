package me.internalizable.numdrassl.api.messaging;

import me.internalizable.numdrassl.api.messaging.annotation.TypeAdapter;
import me.internalizable.numdrassl.api.messaging.channel.MessageChannel;
import me.internalizable.numdrassl.api.messaging.handler.MessageHandler;
import me.internalizable.numdrassl.api.messaging.handler.PluginMessageHandler;

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
 * @see Subscription
 * @see PluginMessageHandler
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
     * @param channel the channel to publish to
     * @param message the message to publish
     * @return a future that completes when the message is sent
     */
    @Nonnull
    CompletableFuture<Void> publish(@Nonnull MessageChannel channel, @Nonnull ChannelMessage message);

    /**
     * Publish a plugin-specific message to other proxies.
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
     * @param channel the channel to subscribe to
     * @param handler the handler to invoke for each message
     * @return a subscription that can be used to unsubscribe
     */
    @Nonnull
    Subscription subscribe(@Nonnull MessageChannel channel, @Nonnull MessageHandler<ChannelMessage> handler);

    /**
     * Subscribe to messages of a specific type on a channel.
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
     * Register a listener object containing @Subscribe-annotated methods.
     *
     * @param listener the listener object
     * @return a composite subscription that can unsubscribe all methods at once
     */
    @Nonnull
    Subscription registerListener(@Nonnull Object listener);

    /**
     * Register a listener object with explicit plugin context.
     *
     * @param listener the listener object
     * @param plugin the plugin instance (must have @Plugin annotation)
     * @return a composite subscription that can unsubscribe all methods at once
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

