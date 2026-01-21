package me.internalizable.numdrassl.api.messaging;

import me.internalizable.numdrassl.api.messaging.channel.MessageChannel;
import me.internalizable.numdrassl.api.messaging.handler.MessageHandler;

import javax.annotation.Nonnull;

/**
 * Represents an active subscription to a messaging channel.
 *
 * <p>Subscriptions are returned by the various subscribe methods in
 * {@link MessagingService} and can be used to check status or cancel
 * the subscription.</p>
 *
 * <h2>Lifecycle</h2>
 * <ul>
 *   <li>Subscriptions are active immediately after creation</li>
 *   <li>Calling {@link #unsubscribe()} permanently deactivates the subscription</li>
 *   <li>Subscriptions may become inactive if the messaging service disconnects</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Create a subscription
 * Subscription sub = messaging.subscribe(Channels.CHAT, handler);
 *
 * // Check if active
 * if (sub.isActive()) {
 *     logger.info("Listening on channel: {}", sub.getChannel());
 * }
 *
 * // Cancel when done
 * sub.unsubscribe();
 * }</pre>
 *
 * @see MessagingService#subscribe(MessageChannel, MessageHandler)
 */
public interface Subscription {

    /**
     * Get the channel this subscription is for.
     *
     * @return the subscribed channel
     */
    @Nonnull
    MessageChannel getChannel();

    /**
     * Check if this subscription is still active.
     *
     * <p>A subscription becomes inactive when:</p>
     * <ul>
     *   <li>{@link #unsubscribe()} is called</li>
     *   <li>The messaging service disconnects</li>
     *   <li>The subscription is removed by the service</li>
     * </ul>
     *
     * @return true if active and receiving messages
     */
    boolean isActive();

    /**
     * Cancel this subscription.
     *
     * <p>After calling this method, no more messages will be delivered
     * to the associated handler. This operation is idempotent - calling
     * it multiple times has no additional effect.</p>
     */
    void unsubscribe();
}

