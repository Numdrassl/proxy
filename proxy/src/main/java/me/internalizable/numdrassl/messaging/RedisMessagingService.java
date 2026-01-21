package me.internalizable.numdrassl.messaging;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import me.internalizable.numdrassl.api.messaging.*;
import me.internalizable.numdrassl.api.messaging.message.PluginMessage;
import me.internalizable.numdrassl.config.ProxyConfig;
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
    private final Map<Object, List<Subscription>> listenerSubscriptions = new ConcurrentHashMap<>();
    private final AtomicLong subscriptionIdCounter = new AtomicLong(0);
    private final AtomicBoolean connected = new AtomicBoolean(false);

    public RedisMessagingService(@Nonnull String localProxyId, @Nonnull ProxyConfig config) {
        this.localProxyId = localProxyId;
        this.codec = new MessageCodec();
        this.methodProcessor = new SubscribeMethodProcessor(localProxyId, codec, createSubscriptionFactory());

        RedisURI redisUri = buildRedisUri(config);
        LOGGER.info("Connecting to Redis at {}:{}", config.getRedisHost(), config.getRedisPort());

        this.redisClient = RedisClient.create(redisUri);
        this.pubSubConnection = redisClient.connectPubSub();
        this.publishConnection = redisClient.connect();
        this.pubSubCommands = pubSubConnection.async();

        pubSubConnection.addListener(new RedisMessageListener(this::handleMessage));

        connected.set(true);
        LOGGER.info("Redis messaging service connected");
    }

    private RedisURI buildRedisUri(ProxyConfig config) {
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

    public void shutdown() {
        LOGGER.info("Shutting down Redis messaging service");
        connected.set(false);

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
            Subscribe annotation = method.getAnnotation(Subscribe.class);
            if (annotation == null) {
                continue;
            }

            try {
                Subscription sub = methodProcessor.process(listener, method, annotation, explicitPluginId);
                subs.add(sub);
            } catch (Exception e) {
                LOGGER.error("Failed to register @Subscribe method {}.{}: {}",
                        listener.getClass().getSimpleName(), method.getName(), e.getMessage());
            }
        }

        if (subs.isEmpty()) {
            LOGGER.warn("No @Subscribe methods found in {}", listener.getClass().getSimpleName());
        } else {
            listenerSubscriptions.put(listener, subs);
            LOGGER.debug("Registered {} @Subscribe methods from {}",
                    subs.size(), listener.getClass().getSimpleName());
        }

        return new CompositeSubscription(subs);
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

    /**
     * Raw subscription method used by SubscriptionFactory to avoid generic issues.
     */
    private Subscription addSubscriptionRaw(
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

    @SuppressWarnings("unchecked")
    private <T extends ChannelMessage> Subscription addSubscription(
            MessageChannel channel,
            MessageHandler<T> handler,
            Class<T> messageType,
            boolean includeSelf) {

        long id = subscriptionIdCounter.incrementAndGet();
        SubscriptionEntry entry = new SubscriptionEntry(
                id, channel,
                (MessageHandler<ChannelMessage>) handler,
                messageType, includeSelf
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

            try {
                entry.getHandler().handle(channel, message);
            } catch (Exception e) {
                LOGGER.error("Error in message handler for channel {}", channel, e);
            }
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
}

