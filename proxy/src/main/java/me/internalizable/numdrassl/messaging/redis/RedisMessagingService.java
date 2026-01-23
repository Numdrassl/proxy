package me.internalizable.numdrassl.messaging.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import me.internalizable.numdrassl.api.messaging.*;
import me.internalizable.numdrassl.api.messaging.annotation.MessageSubscribe;
import me.internalizable.numdrassl.api.messaging.annotation.TypeAdapter;
import me.internalizable.numdrassl.api.messaging.channel.Channels;
import me.internalizable.numdrassl.api.messaging.channel.MessageChannel;
import me.internalizable.numdrassl.api.messaging.handler.MessageHandler;
import me.internalizable.numdrassl.api.messaging.handler.PluginMessageHandler;
import me.internalizable.numdrassl.api.messaging.message.PluginMessage;
import me.internalizable.numdrassl.config.ProxyConfig;
import me.internalizable.numdrassl.messaging.codec.MessageCodec;
import me.internalizable.numdrassl.messaging.processing.PluginIdExtractor;
import me.internalizable.numdrassl.messaging.processing.SubscribeMethodProcessor;
import me.internalizable.numdrassl.messaging.subscription.CompositeSubscription;
import me.internalizable.numdrassl.messaging.subscription.RedisSubscription;
import me.internalizable.numdrassl.messaging.subscription.SubscriptionEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis-based messaging service implementation using Lettuce.
 *
 * <p>Provides pub/sub messaging across proxy instances via Redis.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Non-blocking async operations via Lettuce's async API</li>
 *   <li>Automatic reconnection on connection loss</li>
 *   <li>JSON message serialization via {@link MessageCodec}</li>
 *   <li>Message filtering by type and source proxy</li>
 *   <li>Annotation-based subscription support</li>
 * </ul>
 */
public final class RedisMessagingService implements MessagingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisMessagingService.class);
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10);

    private final String localProxyId;
    private final MessageCodec codec;
    private final RedisClient redisClient;
    private final StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private final StatefulRedisConnection<String, String> publishConnection;
    private final RedisPubSubAsyncCommands<String, String> pubSubCommands;
    private final SubscribeMethodProcessor methodProcessor;

    private final Map<String, List<SubscriptionEntry>> subscriptions = new ConcurrentHashMap<>();

    // Executor for running message handlers off the Lettuce I/O thread
    private final ExecutorService handlerExecutor;
    private final Map<Object, List<Subscription>> listenerSubscriptions = new ConcurrentHashMap<>();
    private final AtomicLong subscriptionIdCounter = new AtomicLong(0);
    private final AtomicBoolean connected = new AtomicBoolean(false);

    private RedisMessagingService(
            @Nonnull String localProxyId,
            @Nonnull RedisClient redisClient,
            @Nonnull StatefulRedisPubSubConnection<String, String> pubSubConnection,
            @Nonnull StatefulRedisConnection<String, String> publishConnection) {
        this.localProxyId = localProxyId;
        this.codec = new MessageCodec();
        this.redisClient = redisClient;
        this.pubSubConnection = pubSubConnection;
        this.publishConnection = publishConnection;
        this.pubSubCommands = pubSubConnection.async();
        this.methodProcessor = new SubscribeMethodProcessor(localProxyId, codec, createSubscriptionFactory());

        // Bounded executor for message handlers - prevents blocking Redis I/O thread
        // Core: 2, Max: 8, Queue: 1000, Timeout: 60s, drops tasks when full
        this.handlerExecutor = new ThreadPoolExecutor(
                2, 8, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                r -> {
                    Thread t = new Thread(r, "numdrassl-msg-handler");
                    t.setDaemon(true);
                    return t;
                },
                (r, executor) -> LOGGER.warn("Message handler queue full, dropping task")
        );

        pubSubConnection.addListener(new RedisMessageListener(this::handleMessage));
        connected.set(true);
    }

    /**
     * Factory method to create a Redis messaging service.
     *
     * @param localProxyId the local proxy identifier
     * @param config the proxy configuration containing Redis settings
     * @return a new connected Redis messaging service
     * @throws RedisConnectionException if connection to Redis fails
     */
    @Nonnull
    public static RedisMessagingService create(@Nonnull String localProxyId, @Nonnull ProxyConfig config) {
        RedisURI redisUri = buildRedisUri(config);
        LOGGER.info("Connecting to Redis at {}:{}", config.getRedisHost(), config.getRedisPort());

        RedisClient redisClient = RedisClient.create(redisUri);
        StatefulRedisPubSubConnection<String, String> pubSubConnection = null;
        StatefulRedisConnection<String, String> publishConnection = null;

        try {
            pubSubConnection = redisClient.connectPubSub();
            publishConnection = redisClient.connect();

            RedisMessagingService service = new RedisMessagingService(
                    localProxyId, redisClient, pubSubConnection, publishConnection
            );

            LOGGER.info("Redis messaging service connected");
            return service;
        } catch (Exception e) {
            closeQuietly(publishConnection);
            closeQuietly(pubSubConnection);
            shutdownQuietly(redisClient);

            LOGGER.error("Failed to connect to Redis: {}", e.getMessage());
            throw new RedisConnectionException("Failed to connect to Redis", e);
        }
    }

    /**
     * Factory method for async initialization.
     *
     * @param localProxyId the local proxy identifier
     * @param config the proxy configuration
     * @return a future that completes with the messaging service
     */
    @Nonnull
    public static CompletableFuture<RedisMessagingService> createAsync(
            @Nonnull String localProxyId,
            @Nonnull ProxyConfig config) {
        return CompletableFuture.supplyAsync(() -> create(localProxyId, config));
    }

    private static RedisURI buildRedisUri(ProxyConfig config) {
        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(config.getRedisHost())
                .withPort(config.getRedisPort())
                .withDatabase(config.getRedisDatabase())
                .withTimeout(CONNECTION_TIMEOUT);

        if (config.getRedisPassword() != null && !config.getRedisPassword().isBlank()) {
            uriBuilder.withPassword(config.getRedisPassword().toCharArray());
        }

        if (config.isRedisSsl()) {
            uriBuilder.withSsl(true);
        }

        return uriBuilder.build();
    }

    private SubscribeMethodProcessor.SubscriptionFactory createSubscriptionFactory() {
        return new SubscribeMethodProcessor.SubscriptionFactory() {
            @Override
            @SuppressWarnings("unchecked")
            public Subscription subscribe(MessageChannel channel, MessageHandler<ChannelMessage> handler,
                                          Class<? extends ChannelMessage> messageType, boolean includeSelf) {
                return addSubscriptionRaw(channel, handler, messageType, includeSelf);
            }

            @Override
            public <T> Subscription subscribePlugin(String pluginId, String channel, Class<T> dataType,
                                                    PluginMessageHandler<T> handler, boolean includeSelf) {
                return subscribePluginInternal(pluginId, channel, dataType, handler, includeSelf);
            }
        };
    }

    // ==================== Connection ====================

    @Override
    public boolean isConnected() {
        return connected.get() && pubSubConnection.isOpen();
    }

    /**
     * Gets the Redis connection for direct key-value operations.
     *
     * <p>Used by services that need direct Redis access, such as
     * {@link me.internalizable.numdrassl.cluster.PlayerLocationService}.</p>
     *
     * @return the stateful Redis connection
     */
    @Nonnull
    public StatefulRedisConnection<String, String> getConnection() {
        return publishConnection;
    }

    public void shutdown() {
        LOGGER.info("Shutting down Redis messaging service");
        connected.set(false);

        // Shutdown handler executor gracefully
        handlerExecutor.shutdown();
        try {
            if (!handlerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                handlerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            handlerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        try {
            pubSubConnection.close();
            publishConnection.close();
            redisClient.shutdown();
        } catch (Exception e) {
            LOGGER.error("Error during Redis shutdown", e);
        }
    }

    // ==================== Publishing ====================

    @Override
    @Nonnull
    public CompletableFuture<Void> publish(@Nonnull MessageChannel channel, @Nonnull ChannelMessage message) {
        if (!isConnected()) {
            LOGGER.warn("Cannot publish to {}: not connected to Redis", channel);
            return CompletableFuture.failedFuture(new IllegalStateException("Not connected to Redis"));
        }

        String json = codec.encode(message);
        return publishConnection.async()
                .publish(channel.getId(), json)
                .thenAccept(count -> LOGGER.debug("Published {} to {} ({} subscribers)",
                        message.messageType(), channel, count))
                .toCompletableFuture();
    }

    @Override
    @Nonnull
    public CompletableFuture<Void> publishPlugin(
            @Nonnull String pluginId,
            @Nonnull String channel,
            @Nonnull Object data) {

        String payload = codec.encodePayload(data);
        PluginMessage message = new PluginMessage(
                localProxyId,
                Instant.now(),
                pluginId,
                channel,
                payload
        );
        return publish(Channels.PLUGIN, message);
    }

    // ==================== Subscribing ====================

    @Override
    @Nonnull
    public Subscription subscribe(@Nonnull MessageChannel channel, @Nonnull MessageHandler<ChannelMessage> handler) {
        return addSubscription(channel, handler, null, false);
    }

    @Override
    @Nonnull
    public <T extends ChannelMessage> Subscription subscribe(
            @Nonnull MessageChannel channel,
            @Nonnull Class<T> messageType,
            @Nonnull MessageHandler<T> handler) {
        return addSubscription(channel, handler, messageType, false);
    }

    @Override
    @Nonnull
    public Subscription subscribeIncludingSelf(
            @Nonnull MessageChannel channel,
            @Nonnull MessageHandler<ChannelMessage> handler) {
        return addSubscription(channel, handler, null, true);
    }

    @Override
    @Nonnull
    public <T> Subscription subscribePlugin(
            @Nonnull String pluginId,
            @Nonnull String channel,
            @Nonnull Class<T> dataType,
            @Nonnull PluginMessageHandler<T> handler) {
        return subscribePluginInternal(pluginId, channel, dataType, handler, false);
    }

    private <T> Subscription subscribePluginInternal(
            String pluginId, String channel, Class<T> dataType,
            PluginMessageHandler<T> handler, boolean includeSelf) {

        MessageHandler<ChannelMessage> wrapperHandler = (msgChannel, message) -> {
            if (message instanceof PluginMessage pm) {
                if (pm.pluginId().equals(pluginId) && pm.channel().equals(channel)) {
                    if (!includeSelf && pm.sourceProxyId().equals(localProxyId)) {
                        return;
                    }
                    T data = codec.decodePayload(pm.payload(), dataType);
                    if (data != null) {
                        handler.handle(pm.sourceProxyId(), data);
                    }
                }
            }
        };

        return addSubscription(Channels.PLUGIN, wrapperHandler, null, true);
    }

    @Override
    public void unsubscribeAll(@Nonnull MessageChannel channel) {
        synchronized (subscriptions) {
            List<SubscriptionEntry> handlers = subscriptions.remove(channel.getId());
            if (handlers != null) {
                handlers.forEach(e -> e.setActive(false));
                pubSubCommands.unsubscribe(channel.getId());
                LOGGER.debug("Unsubscribed from channel: {}", channel);
            }
        }
    }

    // ==================== Annotation-Based API ====================

    @Override
    @Nonnull
    public Subscription registerListener(@Nonnull Object listener) {
        return registerListenerInternal(listener, null);
    }

    @Override
    @Nonnull
    public Subscription registerListener(@Nonnull Object listener, @Nonnull Object plugin) {
        String pluginId = PluginIdExtractor.fromClass(plugin.getClass());
        if (pluginId == null) {
            throw new IllegalArgumentException("Plugin object must have @Plugin annotation");
        }
        return registerListenerInternal(listener, pluginId);
    }

    private Subscription registerListenerInternal(Object listener, String explicitPluginId) {
        List<Subscription> subs = new ArrayList<>();

        for (Method method : listener.getClass().getDeclaredMethods()) {
            MessageSubscribe annotation = method.getAnnotation(MessageSubscribe.class);
            if (annotation == null) {
                continue;
            }

            try {
                Subscription sub = methodProcessor.process(listener, method, annotation, explicitPluginId);
                subs.add(sub);
            } catch (Exception e) {
                LOGGER.error("Failed to register @MessageSubscribe method {}.{}: {}",
                        listener.getClass().getSimpleName(), method.getName(), e.getMessage());
            }
        }

        if (subs.isEmpty()) {
            LOGGER.warn("No @MessageSubscribe methods found in {}", listener.getClass().getSimpleName());
        } else {
            listenerSubscriptions.put(listener, subs);
            LOGGER.debug("Registered {} @Subscribe methods from {}",
                    subs.size(), listener.getClass().getSimpleName());
        }

        // Create composite with cleanup callback to remove listener from registry
        return new CompositeSubscription(subs, () -> listenerSubscriptions.remove(listener));
    }

    @Override
    public void unregisterListener(@Nonnull Object listener) {
        List<Subscription> subs = listenerSubscriptions.remove(listener);
        if (subs != null) {
            subs.forEach(Subscription::unsubscribe);
            LOGGER.debug("Unregistered {} subscriptions from {}",
                    subs.size(), listener.getClass().getSimpleName());
        }
    }

    // ==================== Type Adapters ====================

    @Override
    public <T> void registerTypeAdapter(@Nonnull TypeAdapter<T> adapter) {
        codec.registerTypeAdapter(adapter);
    }

    @Override
    public void unregisterTypeAdapter(@Nonnull Class<?> type) {
        codec.unregisterTypeAdapter(type);
    }

    // ==================== Internal ====================

    private Subscription addSubscriptionRaw(
            MessageChannel channel,
            MessageHandler<ChannelMessage> handler,
            Class<? extends ChannelMessage> messageType,
            boolean includeSelf) {

        return doAddSubscription(channel, handler, messageType, includeSelf);
    }

    @SuppressWarnings("unchecked")
    private <T extends ChannelMessage> Subscription addSubscription(
            MessageChannel channel,
            MessageHandler<T> handler,
            Class<T> messageType,
            boolean includeSelf) {

        return doAddSubscription(
                channel,
                (MessageHandler<ChannelMessage>) handler,
                messageType,
                includeSelf
        );
    }

    private Subscription doAddSubscription(
            MessageChannel channel,
            MessageHandler<ChannelMessage> handler,
            Class<? extends ChannelMessage> messageType,
            boolean includeSelf) {

        long id = subscriptionIdCounter.incrementAndGet();
        SubscriptionEntry entry = new SubscriptionEntry(
                id, channel, handler, messageType, includeSelf
        );

        boolean needsSubscribe;
        synchronized (subscriptions) {
            List<SubscriptionEntry> handlers = subscriptions.computeIfAbsent(
                    channel.getId(), k -> new CopyOnWriteArrayList<>());
            needsSubscribe = handlers.isEmpty();
            handlers.add(entry);
        }

        if (needsSubscribe) {
            pubSubCommands.subscribe(channel.getId())
                    .thenAccept(v -> LOGGER.debug("Subscribed to channel: {}", channel));
        }

        return new RedisSubscription(entry, this::isConnected, this::removeSubscription);
    }

    private void handleMessage(String channelName, String json) {
        MessageChannel channel = Channels.get(channelName);
        if (channel == null) {
            LOGGER.warn("Received message on unknown channel: {}", channelName);
            return;
        }

        ChannelMessage message = codec.decode(json);
        if (message == null) {
            return;
        }

        List<SubscriptionEntry> handlers;
        synchronized (subscriptions) {
            handlers = subscriptions.get(channelName);
        }

        if (handlers == null || handlers.isEmpty()) {
            return;
        }

        // Process each handler off the Lettuce I/O thread
        for (SubscriptionEntry entry : handlers) {
            if (!entry.isActive()) {
                continue;
            }

            if (!entry.isIncludeSelf() && message.sourceProxyId().equals(localProxyId)) {
                continue;
            }

            if (entry.getMessageType() != null && !entry.getMessageType().isInstance(message)) {
                continue;
            }

            // Submit handler execution to bounded executor
            handlerExecutor.execute(() -> {
                try {
                    entry.getHandler().handle(channel, message);
                } catch (Exception e) {
                    LOGGER.error("Error in message handler for channel {}", channel, e);
                }
            });
        }
    }

    private void removeSubscription(SubscriptionEntry entry) {
        synchronized (subscriptions) {
            List<SubscriptionEntry> handlers = subscriptions.get(entry.getChannel().getId());
            if (handlers != null) {
                handlers.removeIf(e -> e.getId() == entry.getId());
                if (handlers.isEmpty()) {
                    pubSubCommands.unsubscribe(entry.getChannel().getId());
                    LOGGER.debug("Unsubscribed from channel: {} (no more handlers)", entry.getChannel());
                }
            }
        }
    }

    // ==================== Resource Cleanup Helpers ====================

    private static void closeQuietly(StatefulRedisConnection<?, ?> connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                LOGGER.debug("Error closing Redis connection", e);
            }
        }
    }

    private static void closeQuietly(StatefulRedisPubSubConnection<?, ?> connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                LOGGER.debug("Error closing Redis pub/sub connection", e);
            }
        }
    }

    private static void shutdownQuietly(RedisClient client) {
        if (client != null) {
            try {
                client.shutdown();
            } catch (Exception e) {
                LOGGER.debug("Error shutting down Redis client", e);
            }
        }
    }
}

