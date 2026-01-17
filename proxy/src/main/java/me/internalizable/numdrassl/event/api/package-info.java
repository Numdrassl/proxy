/**
 * Plugin API event system with priority ordering and async support.
 *
 * <p>This package provides the high-level event system for plugins.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.event.api.NumdrasslEventManager} - Main event manager
 *       implementing the API {@link me.internalizable.numdrassl.api.event.EventManager}</li>
 * </ul>
 *
 * <h2>Subpackages</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.event.api.handler} - Handler registration internals</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * public class MyListener {
 *     @Subscribe(priority = EventPriority.NORMAL)
 *     public void onChat(PlayerChatEvent event) {
 *         // Handle chat event
 *     }
 * }
 *
 * eventManager.register(plugin, new MyListener());
 * }</pre>
 *
 * @see me.internalizable.numdrassl.event.packet
 */
package me.internalizable.numdrassl.event.api;

