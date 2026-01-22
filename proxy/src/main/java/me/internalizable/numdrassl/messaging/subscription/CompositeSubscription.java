package me.internalizable.numdrassl.messaging.subscription;

import me.internalizable.numdrassl.api.messaging.channel.MessageChannel;
import me.internalizable.numdrassl.api.messaging.Subscription;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Subscription that groups multiple subscriptions together.
 *
 * <p>Used when registering a listener class with multiple {@code @Subscribe}
 * methods - all subscriptions can be unsubscribed at once.</p>
 *
 * <p>Note: {@link #getChannel()} returns the common channel if all wrapped
 * subscriptions target the same channel, otherwise throws
 * {@link UnsupportedOperationException}.</p>
 */
public final class CompositeSubscription implements Subscription {

    private final List<Subscription> subscriptions;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final Runnable cleanupCallback;

    public CompositeSubscription(List<Subscription> subscriptions) {
        this(subscriptions, null);
    }

    /**
     * Create a composite subscription with optional cleanup callback.
     *
     * @param subscriptions the subscriptions to wrap
     * @param cleanupCallback called once when unsubscribe() is invoked, may be null
     */
    public CompositeSubscription(List<Subscription> subscriptions, @Nullable Runnable cleanupCallback) {
        this.subscriptions = List.copyOf(subscriptions);
        this.cleanupCallback = cleanupCallback;
    }

    /**
     * Returns the channel if all wrapped subscriptions share the same channel.
     *
     * @return the common channel
     * @throws UnsupportedOperationException if subscriptions target different channels
     *         or if the composite is empty
     */
    @Override
    @Nonnull
    public MessageChannel getChannel() {
        if (subscriptions.isEmpty()) {
            throw new UnsupportedOperationException(
                    "Cannot get channel from empty CompositeSubscription");
        }

        MessageChannel firstChannel = subscriptions.getFirst().getChannel();

        boolean allSame = subscriptions.stream()
                .map(Subscription::getChannel)
                .allMatch(ch -> ch.getId().equals(firstChannel.getId()));

        if (!allSame) {
            throw new UnsupportedOperationException(
                    "CompositeSubscription contains subscriptions to multiple channels; " +
                    "use getChannels() to retrieve all channels");
        }

        return firstChannel;
    }

    /**
     * Returns all unique channels that the wrapped subscriptions target.
     *
     * @return list of unique channels
     */
    @Nonnull
    public List<MessageChannel> getChannels() {
        return subscriptions.stream()
                .map(Subscription::getChannel)
                .distinct()
                .toList();
    }

    @Override
    public boolean isActive() {
        return active.get() && subscriptions.stream().anyMatch(Subscription::isActive);
    }

    @Override
    public void unsubscribe() {
        // Ensure idempotent - only run cleanup once
        if (active.compareAndSet(true, false)) {
            subscriptions.forEach(Subscription::unsubscribe);
            if (cleanupCallback != null) {
                cleanupCallback.run();
            }
        }
    }

    /**
     * Get the number of subscriptions in this composite.
     *
     * @return the subscription count
     */
    public int size() {
        return subscriptions.size();
    }
}

