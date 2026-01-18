/**
 * Chat message formatting API.
 *
 * <p>Provides a fluent builder for creating formatted chat messages
 * with colors and styling that can be sent to players.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Simple colored message
 * player.sendMessage(ChatMessageBuilder.create()
 *     .green("[Success] ")
 *     .white("Operation completed!"));
 *
 * // Multi-colored with formatting
 * player.sendMessage(ChatMessageBuilder.create()
 *     .gold("[Server] ")
 *     .bold("Important: ", ChatMessageBuilder.Colors.RED)
 *     .white("Server restarting in 5 minutes"));
 *
 * // Custom hex colors
 * player.sendMessage(ChatMessageBuilder.create()
 *     .text("Custom color!", "#FF6B35"));
 * }</pre>
 *
 * @see me.internalizable.numdrassl.api.chat.ChatMessageBuilder
 * @see me.internalizable.numdrassl.api.chat.FormattedMessagePart
 */
package me.internalizable.numdrassl.api.chat;

