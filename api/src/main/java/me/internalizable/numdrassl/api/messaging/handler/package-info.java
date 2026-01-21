/**
 * Handler interfaces for message processing.
 *
 * <p>Contains callback interfaces used when subscribing to messages:</p>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.api.messaging.handler.MessageHandler} - Generic message handler</li>
 *   <li>{@link me.internalizable.numdrassl.api.messaging.handler.PluginMessageHandler} - Plugin-specific message handler</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Generic handler
 * MessageHandler<HeartbeatMessage> handler = (channel, msg) -> {
 *     logger.info("Heartbeat from {}", msg.sourceProxyId());
 * };
 *
 * // Plugin handler
 * PluginMessageHandler<MyData> pluginHandler = (sourceProxy, data) -> {
 *     logger.info("Received from {}: {}", sourceProxy, data);
 * };
 * }</pre>
 *
 * @see me.internalizable.numdrassl.api.messaging.MessagingService
 */
package me.internalizable.numdrassl.api.messaging.handler;

