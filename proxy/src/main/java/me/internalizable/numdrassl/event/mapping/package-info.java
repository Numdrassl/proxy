/**
 * Packet-to-event mappings for translating protocol packets to API events.
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.event.mapping.PacketEventRegistry} -
 *       Registry for packet-to-event mappings</li>
 *   <li>{@link me.internalizable.numdrassl.event.mapping.PacketEventMapping} -
 *       Interface for implementing custom mappings</li>
 *   <li>{@link me.internalizable.numdrassl.event.mapping.PacketContext} -
 *       Context provided to mappings during translation</li>
 * </ul>
 *
 * <h2>Subpackages</h2>
 * <ul>
 *   <li>{@code connection} - Connect/Disconnect packet mappings</li>
 *   <li>{@code interface_} - Chat and message packet mappings</li>
 * </ul>
 *
 * @see me.internalizable.numdrassl.event.api
 */
package me.internalizable.numdrassl.event.mapping;

