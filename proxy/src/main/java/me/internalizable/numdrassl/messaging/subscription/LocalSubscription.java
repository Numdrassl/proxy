package me.internalizable.numdrassl.messaging.subscription;

import me.internalizable.numdrassl.api.messaging.channel.MessageChannel;
import me.internalizable.numdrassl.api.messaging.Subscription;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * Subscription implementation for local-only messaging.
 *
 * <p>Used when Redis clustering is disabled.</p>
 */
public final class LocalSubscription implements Subscription {

    private final SubscriptionEntry entry;
    private final Consumer<SubscriptionEntry> removeCallback;

    /**
     * Create a new local subscription.
     *
     * @param entry the subscription entry
     * @param removeCallback called when unsubscribing (can be null for no-op)
     */
    public LocalSubscription(SubscriptionEntry entry, Consumer<SubscriptionEntry> removeCallback) {
        this.entry = entry;
        this.removeCallback = removeCallback;
    }

    @Override
    @Nonnull
    public MessageChannel getChannel() {
        return entry.getChannel();
    }

    @Override
    public boolean isActive() {
        return entry.isActive();
    }

    @Override
    public void unsubscribe() {
        entry.setActive(false);
        if (removeCallback != null) {
            removeCallback.accept(entry);
        }
    }
}

