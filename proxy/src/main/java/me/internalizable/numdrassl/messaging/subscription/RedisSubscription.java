package me.internalizable.numdrassl.messaging.subscription;

import me.internalizable.numdrassl.api.messaging.channel.MessageChannel;
import me.internalizable.numdrassl.api.messaging.Subscription;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Subscription implementation for Redis-backed messaging.
 *
 * <p>Wraps a {@link SubscriptionEntry} and provides lifecycle management.</p>
 */
public final class RedisSubscription implements Subscription {

    private final SubscriptionEntry entry;
    private final BooleanSupplier connectionChecker;
    private final Consumer<SubscriptionEntry> removeCallback;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * Create a new Redis subscription.
     *
     * @param entry the subscription entry
     * @param connectionChecker supplies connection status
     * @param removeCallback called when unsubscribing
     */
    public RedisSubscription(
            SubscriptionEntry entry,
            BooleanSupplier connectionChecker,
            Consumer<SubscriptionEntry> removeCallback) {
        this.entry = entry;
        this.connectionChecker = connectionChecker;
        this.removeCallback = removeCallback;
    }

    @Override
    @Nonnull
    public MessageChannel getChannel() {
        return entry.getChannel();
    }

    @Override
    public boolean isActive() {
        return !cancelled.get() && entry.isActive() && connectionChecker.getAsBoolean();
    }

    @Override
    public void unsubscribe() {
        // Ensure unsubscribe is idempotent - only invoke callback once
        if (cancelled.compareAndSet(false, true)) {
            entry.setActive(false);
            removeCallback.accept(entry);
        }
    }
}

