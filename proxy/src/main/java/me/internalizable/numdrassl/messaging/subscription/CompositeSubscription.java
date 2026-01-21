package me.internalizable.numdrassl.messaging.subscription;

import me.internalizable.numdrassl.api.messaging.Channels;
import me.internalizable.numdrassl.api.messaging.MessageChannel;
import me.internalizable.numdrassl.api.messaging.Subscription;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Subscription that groups multiple subscriptions together.
 *
 * <p>Used when registering a listener class with multiple {@code @Subscribe}
 * methods - all subscriptions can be unsubscribed at once.</p>
 */
public final class CompositeSubscription implements Subscription {

    private final List<Subscription> subscriptions;
    private volatile boolean active = true;

    public CompositeSubscription(List<Subscription> subscriptions) {
        this.subscriptions = List.copyOf(subscriptions);
    }

    @Override
    @Nonnull
    public MessageChannel getChannel() {
        // Return PLUGIN as default for composite subscriptions
        return Channels.PLUGIN;
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

