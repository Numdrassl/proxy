/**
 * Network utilities for the proxy server.
 *
 * <p>This package provides utilities for network-related operations such as
 * building formatted chat messages for player communication.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.api.chat.ChatMessageBuilder} - Fluent builder
 *       for constructing Hytale {@code FormattedMessage} objects with colors and styling.
 *       Simplifies the verbose message construction API.</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * ServerMessage message = ChatMessageBuilder.create()
 *     .gold("Connecting to ")
 *     .bold("lobby", ChatMessageBuilder.Colors.GREEN)
 *     .gold("...")
 *     .buildServerMessage();
 *
 * session.sendToClient(message);
 * }</pre>
 *
 * @see me.internalizable.numdrassl.api.chat.ChatMessageBuilder
 */
package me.internalizable.numdrassl.server.network;

