/**
 * Cross-proxy messaging API for distributed Numdrassl deployments.
 *
 * <p>This package provides a pub/sub messaging system for communication
 * between proxy instances, typically backed by Redis.</p>
 *
 * <h2>Core Interfaces</h2>
 * <ul>
 *   <li>{@link MessagingService} - Main service for publishing and subscribing</li>
 *   <li>{@link Subscription} - Represents an active subscription</li>
 *   <li>{@link MessageHandler} - Callback for received messages</li>
 *   <li>{@link PluginMessageHandler} - Typed callback for plugin messages</li>
 * </ul>
 *
 * <h2>Channels</h2>
 * <ul>
 *   <li>{@link MessageChannel} - Represents a pub/sub channel</li>
 *   <li>{@link Channels} - Predefined system channels</li>
 *   <li>{@link SystemChannel} - Enum of system channel types</li>
 * </ul>
 *
 * <h2>Messages</h2>
 * <ul>
 *   <li>{@link ChannelMessage} - Base interface for all messages</li>
 *   <li>{@link me.internalizable.numdrassl.api.messaging.message} - Concrete message types</li>
 * </ul>
 *
 * <h2>Annotation API</h2>
 * <ul>
 *   <li>{@link Subscribe} - Marks methods as message handlers</li>
 *   <li>{@link TypeAdapter} - Custom serialization for message payloads</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Programmatic API</h3>
 * <pre>{@code
 * MessagingService messaging = proxy.getMessagingService();
 *
 * // Subscribe to system messages
 * messaging.subscribe(Channels.HEARTBEAT, HeartbeatMessage.class,
 *     (channel, msg) -> logger.info("Proxy {} alive", msg.sourceProxyId()));
 *
 * // Subscribe to plugin messages
 * messaging.subscribePlugin("my-plugin", "events", MyData.class,
 *     (sourceProxyId, data) -> handleData(data));
 *
 * // Publish plugin messages
 * messaging.publishPlugin("my-plugin", "events", new MyData(...));
 * }</pre>
 *
 * <h3>Annotation API</h3>
 * <pre>{@code
 * @Plugin(id = "my-plugin", name = "My Plugin", version = "1.0.0")
 * public class MyPlugin {
 *
 *     @Subscribe(channel = "events")
 *     public void onEvent(MyData data) {
 *         // Plugin ID inferred from @Plugin annotation
 *     }
 *
 *     @Subscribe(SystemChannel.CHAT)
 *     public void onChat(ChatMessage msg) {
 *         // System channel subscription
 *     }
 * }
 *
 * // Register
 * messaging.registerListener(myPlugin);
 * }</pre>
 *
 * @see MessagingService
 * @see Subscribe
 */
package me.internalizable.numdrassl.api.messaging;

