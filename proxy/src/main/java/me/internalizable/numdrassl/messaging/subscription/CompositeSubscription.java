package me.internalizable.numdrassl.messaging.subscription;

import me.internalizable.numdrassl.api.messaging.channel.MessageChannel;
import me.internalizable.numdrassl.api.messaging.Subscription;

import javax.annotation.Nonnull;
import java.util.List;

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
    private volatile boolean active = true;

    public CompositeSubscription(List<Subscription> subscriptions) {
        this.subscriptions = List.copyOf(subscriptions);
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
        return active && subscriptions.stream().anyMatch(Subscription::isActive);
    }

    @Override
    public void unsubscribe() {
        active = false;
        subscriptions.forEach(Subscription::unsubscribe);
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

