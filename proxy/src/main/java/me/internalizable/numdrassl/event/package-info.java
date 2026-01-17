/**
 * Event system for the Numdrassl proxy.
 *
 * <p>This package is organized into focused subpackages:</p>
 *
 * <h2>Subpackages</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.event.packet} - Low-level packet interception</li>
 *   <li>{@link me.internalizable.numdrassl.event.api} - Plugin API event manager</li>
 *   <li>{@link me.internalizable.numdrassl.event.mapping} - Packet-to-event translation</li>
 * </ul>
 *
 * <h2>Event Flow</h2>
 * <pre>
 *                        Packet Received
 *                              │
 *                              ▼
 *            ┌─────────────────────────────────┐
 *            │ event.packet.PacketEventManager │  (Internal listeners)
 *            └───────────────┬─────────────────┘
 *                            │
 *                            ▼
 *            ┌─────────────────────────────────┐
 *            │ event.mapping.PacketEventRegistry│  (Packet → API Event)
 *            └───────────────┬─────────────────┘
 *                            │
 *                            ▼
 *            ┌─────────────────────────────────┐
 *            │ event.api.NumdrasslEventManager │  (Plugin @Subscribe)
 *            └─────────────────────────────────┘
 * </pre>
 *
 * @see me.internalizable.numdrassl.event.packet
 * @see me.internalizable.numdrassl.event.api
 * @see me.internalizable.numdrassl.event.mapping
 */
package me.internalizable.numdrassl.event;

