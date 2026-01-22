package me.internalizable.numdrassl.messaging.local;

import me.internalizable.numdrassl.api.messaging.*;
import me.internalizable.numdrassl.api.messaging.annotation.TypeAdapter;
import me.internalizable.numdrassl.api.messaging.channel.Channels;
import me.internalizable.numdrassl.api.messaging.channel.MessageChannel;
import me.internalizable.numdrassl.api.messaging.handler.MessageHandler;
import me.internalizable.numdrassl.api.messaging.handler.PluginMessageHandler;
import me.internalizable.numdrassl.messaging.subscription.LocalSubscription;
import me.internalizable.numdrassl.messaging.subscription.SubscriptionEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Local-only messaging service for non-clustered deployments.
 *
 * <p>This implementation provides a no-op messaging service that always
 * reports as disconnected from the cluster. Used when Redis is disabled.</p>
 *
 * <p>Local subscriptions still work for intra-proxy communication.</p>
 */
public final class LocalMessagingService implements MessagingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalMessagingService.class);

    private final String localProxyId;
    private final Map<MessageChannel, List<SubscriptionEntry>> subscriptions = new ConcurrentHashMap<>();
    private final AtomicLong subscriptionIdCounter = new AtomicLong(0);

    public LocalMessagingService(@Nonnull String localProxyId) {
        this.localProxyId = localProxyId;
        LOGGER.info("Local messaging service initialized (cluster mode disabled)");
    }

    @Override
    public boolean isConnected() {
        return false; // Always disconnected from cluster
    }

    @Override
    @Nonnull
    public CompletableFuture<Void> publish(@Nonnull MessageChannel channel, @Nonnull ChannelMessage message) {
        List<SubscriptionEntry> handlers = subscriptions.get(channel);
        if (handlers != null) {
            for (SubscriptionEntry entry : handlers) {
                // In local mode, always deliver messages since there's only one proxy
                // The includeSelf flag is more relevant for cluster mode
                if (entry.isIncludeSelf() || !message.sourceProxyId().equals(localProxyId) || isLocalMode()) {
                    try {
                        deliverMessage(entry, channel, message);
                    } catch (Exception e) {
                        LOGGER.error("Error in local message handler", e);
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Returns true since this is a local-only messaging service.
     */
    private boolean isLocalMode() {
        return true;
    }

    @Override
    @Nonnull
    public CompletableFuture<Void> publishPlugin(
            @Nonnull String pluginId,
            @Nonnull String channel,
            @Nonnull Object data) {
        LOGGER.debug("Plugin message not published (local mode): {}:{}", pluginId, channel);
        return CompletableFuture.completedFuture(null);
    }

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
        LOGGER.debug("Plugin subscription ignored (local mode): {}:{}", pluginId, channel);
        return createNoOpSubscription();
    }

    @Override
    public void unsubscribeAll(@Nonnull MessageChannel channel) {
        List<SubscriptionEntry> handlers = subscriptions.remove(channel);
        if (handlers != null) {
            handlers.forEach(e -> e.setActive(false));
        }
    }

    @Override
    @Nonnull
    public Subscription registerListener(@Nonnull Object listener) {
        LOGGER.debug("Listener registration ignored (local mode): {}", listener.getClass().getSimpleName());
        return createNoOpSubscription();
    }

    @Override
    @Nonnull
    public Subscription registerListener(@Nonnull Object listener, @Nonnull Object plugin) {
        LOGGER.debug("Listener registration ignored (local mode): {}", listener.getClass().getSimpleName());
        return createNoOpSubscription();
    }

    @Override
    public void unregisterListener(@Nonnull Object listener) {
        // No-op in local mode
    }

    @Override
    public <T> void registerTypeAdapter(@Nonnull TypeAdapter<T> adapter) {
        LOGGER.debug("Type adapter registration ignored (local mode): {}", adapter.getType().getSimpleName());
    }

    @Override
    public void unregisterTypeAdapter(@Nonnull Class<?> type) {
        // No-op in local mode
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

        subscriptions.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>()).add(entry);

        return new LocalSubscription(entry, this::removeSubscription);
    }

    private void deliverMessage(SubscriptionEntry entry, MessageChannel channel, ChannelMessage message) {
        if (entry.getMessageType() == null || entry.getMessageType().isInstance(message)) {
            entry.getHandler().handle(channel, message);
        }
    }

    private void removeSubscription(SubscriptionEntry entry) {
        List<SubscriptionEntry> handlers = subscriptions.get(entry.getChannel());
        if (handlers != null) {
            handlers.removeIf(e -> e.getId() == entry.getId());
        }
    }

    private Subscription createNoOpSubscription() {
        SubscriptionEntry entry = new SubscriptionEntry(
                subscriptionIdCounter.incrementAndGet(),
                Channels.PLUGIN,
                (ch, msg) -> {},
                null, false
        );
        return new LocalSubscription(entry, null);
    }
}

