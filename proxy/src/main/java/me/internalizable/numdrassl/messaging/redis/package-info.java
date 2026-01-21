/**
 * Redis-based messaging service implementation.
 *
 * <p>Provides pub/sub messaging via Redis using the Lettuce client library.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link RedisMessagingService} - Main Redis messaging implementation</li>
 *   <li>{@link RedisMessageListener} - Listener adapter for Redis pub/sub</li>
 *   <li>{@link RedisConnectionException} - Exception for connection failures</li>
 * </ul>
 *
 * @see me.internalizable.numdrassl.api.messaging.MessagingService
 */
@javax.annotation.ParametersAreNonnullByDefault
package me.internalizable.numdrassl.messaging.redis;

