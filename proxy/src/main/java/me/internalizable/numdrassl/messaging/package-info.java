/**
 * Messaging service implementations for cross-proxy communication.
 *
 * <p>Provides Redis-based pub/sub messaging between proxy instances.</p>
 *
 * <h2>Subpackages</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.messaging.redis} - Redis messaging service implementation</li>
 *   <li>{@link me.internalizable.numdrassl.messaging.local} - Local-only fallback implementation</li>
 *   <li>{@link me.internalizable.numdrassl.messaging.codec} - JSON message serialization</li>
 *   <li>{@link me.internalizable.numdrassl.messaging.processing} - Annotation processing utilities</li>
 *   <li>{@link me.internalizable.numdrassl.messaging.subscription} - Subscription management</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Use factory method for proper resource management
 * RedisMessagingService service = RedisMessagingService.create(proxyId, config);
 *
 * // Or async initialization
 * RedisMessagingService.createAsync(proxyId, config)
 *     .thenAccept(service -> { ... });
 *
 * // For non-clustered deployments
 * LocalMessagingService local = new LocalMessagingService(proxyId);
 * }</pre>
 *
 * @see me.internalizable.numdrassl.api.messaging
 * @see me.internalizable.numdrassl.messaging.redis.RedisMessagingService
 * @see me.internalizable.numdrassl.messaging.local.LocalMessagingService
 */
package me.internalizable.numdrassl.messaging;

