/**
 * Messaging service implementations for cross-proxy communication.
 *
 * <p>Provides Redis-based pub/sub messaging between proxy instances.</p>
 *
 * <h2>Implementations</h2>
 * <ul>
 *   <li>{@link RedisMessagingService} - Full Redis-backed implementation</li>
 *   <li>{@link LocalMessagingService} - Local-only fallback when Redis is disabled</li>
 * </ul>
 *
 * <h2>Components</h2>
 * <ul>
 *   <li>{@link MessageCodec} - JSON serialization for messages</li>
 *   <li>{@link RedisMessageListener} - Redis pub/sub event handler</li>
 *   <li>{@link PluginIdExtractor} - Extracts plugin IDs from annotations</li>
 *   <li>{@link SubscribeMethodProcessor} - Processes @Subscribe annotations</li>
 * </ul>
 *
 * <h2>Subpackages</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.messaging.subscription} - Subscription management</li>
 * </ul>
 *
 * @see me.internalizable.numdrassl.api.messaging
 */
package me.internalizable.numdrassl.messaging;

