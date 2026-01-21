/**
 * Message types for cross-proxy communication.
 *
 * <p>This package contains the concrete message record types that are transmitted
 * between proxy instances. All messages implement {@link me.internalizable.numdrassl.api.messaging.ChannelMessage}.</p>
 *
 * <h2>Message Types</h2>
 * <ul>
 *   <li>{@link HeartbeatMessage} - Proxy health monitoring and discovery</li>
 *   <li>{@link PlayerCountMessage} - Player count synchronization</li>
 *   <li>{@link ChatMessage} - Cross-proxy chat delivery</li>
 *   <li>{@link TransferMessage} - Player transfer coordination</li>
 *   <li>{@link PluginMessage} - Custom plugin data</li>
 *   <li>{@link BroadcastMessage} - Cluster-wide broadcasts</li>
 * </ul>
 *
 * <h2>Serialization</h2>
 * <p>All message types are immutable records that serialize to JSON for transport
 * over Redis pub/sub.</p>
 *
 * @see me.internalizable.numdrassl.api.messaging.ChannelMessage
 * @see me.internalizable.numdrassl.api.messaging.channel.SystemChannel
 */
package me.internalizable.numdrassl.api.messaging.message;

