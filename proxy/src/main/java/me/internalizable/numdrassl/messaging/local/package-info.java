/**
 * Local messaging service for single-proxy deployments.
 *
 * <p>Provides a no-op implementation of {@link me.internalizable.numdrassl.api.messaging.MessagingService}
 * for use when Redis is not configured.</p>
 *
 * <h2>Key Class</h2>
 * <ul>
 *   <li>{@link LocalMessagingService} - Local-only messaging implementation</li>
 * </ul>
 *
 * @see me.internalizable.numdrassl.messaging.redis.RedisMessagingService
 */
@javax.annotation.ParametersAreNonnullByDefault
package me.internalizable.numdrassl.messaging.local;

