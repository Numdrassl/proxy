/**
 * Subscription management for the messaging service.
 *
 * <p>Contains classes that track active subscriptions and handle their lifecycle.</p>
 *
 * <h2>Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.messaging.subscription.SubscriptionEntry} - Holds subscription metadata and handler</li>
 *   <li>{@link me.internalizable.numdrassl.messaging.subscription.RedisSubscription} - Redis-backed subscription</li>
 *   <li>{@link me.internalizable.numdrassl.messaging.subscription.LocalSubscription} - Local-only subscription</li>
 *   <li>{@link me.internalizable.numdrassl.messaging.subscription.CompositeSubscription} - Groups multiple subscriptions</li>
 * </ul>
 */
package me.internalizable.numdrassl.messaging.subscription;

