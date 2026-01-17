/**
 * Low-level packet interception and event dispatch.
 *
 * <p>This package handles raw packet events flowing through the proxy.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.event.packet.PacketEventManager} - Manages packet listeners
 *       and dispatches packet events</li>
 *   <li>{@link me.internalizable.numdrassl.event.packet.PacketEvent} - Wrapper for intercepted packets</li>
 *   <li>{@link me.internalizable.numdrassl.event.packet.PacketListener} - Interface for packet interception</li>
 *   <li>{@link me.internalizable.numdrassl.event.packet.PacketDirection} - Packet flow direction</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * public class MyListener implements PacketListener {
 *     @Override
 *     public <T extends Packet> T onClientPacket(PacketEvent<T> event) {
 *         if (event.getPacket() instanceof ChatMessage chat) {
 *             // Process chat message
 *         }
 *         return event.getPacket();
 *     }
 * }
 *
 * eventManager.registerListener(new MyListener());
 * }</pre>
 *
 * @see me.internalizable.numdrassl.event.api
 */
package me.internalizable.numdrassl.event.packet;

