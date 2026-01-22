/**
 * Plugin messaging API for communication between the proxy and backend servers.
 *
 * <p>This package provides the infrastructure for sending and receiving custom
 * plugin messages through registered channels. This is commonly used by plugins
 * like LuckPerms to synchronize data between the proxy and backend servers.</p>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.api.plugin.messaging.ChannelIdentifier} - Identifies a message channel</li>
 *   <li>{@link me.internalizable.numdrassl.api.plugin.messaging.ChannelRegistrar} - Registers/unregisters channels</li>
 * </ul>
 *
 * <h2>Related Events</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.api.event.connection.PluginMessageEvent} - Fired when a message is received</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Create and register a channel
 * ChannelIdentifier channel = ChannelIdentifier.create("myplugin", "sync");
 * proxy.getChannelRegistrar().register(channel);
 *
 * // Listen for messages
 * @Subscribe
 * public void onPluginMessage(PluginMessageEvent event) {
 *     if (event.getIdentifier().equals(channel)) {
 *         byte[] data = event.getData();
 *         // Process the message
 *     }
 * }
 *
 * // Send a message to a server
 * server.sendPluginMessage(channel, data);
 * }</pre>
 *
 * @see me.internalizable.numdrassl.api.server.RegisteredServer#sendPluginMessage
 */
package me.internalizable.numdrassl.api.plugin.messaging;

