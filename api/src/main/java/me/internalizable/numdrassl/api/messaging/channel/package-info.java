/**
 * Channel definitions and registry for cross-proxy messaging.
 *
 * <p>Contains:</p>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.api.messaging.channel.MessageChannel} - Channel interface</li>
 *   <li>{@link me.internalizable.numdrassl.api.messaging.channel.Channels} - Channel registry with predefined channels</li>
 *   <li>{@link me.internalizable.numdrassl.api.messaging.channel.SystemChannel} - Enum of system channel types</li>
 *   <li>{@link me.internalizable.numdrassl.api.messaging.channel.BroadcastType} - Types of broadcast messages</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Use predefined system channels
 * messaging.subscribe(Channels.CHAT, handler);
 * messaging.subscribe(Channels.HEARTBEAT, handler);
 *
 * // Register custom channels
 * MessageChannel myChannel = Channels.register("myplugin", "events");
 * }</pre>
 *
 * @see me.internalizable.numdrassl.api.messaging.MessagingService
 */
package me.internalizable.numdrassl.api.messaging.channel;

