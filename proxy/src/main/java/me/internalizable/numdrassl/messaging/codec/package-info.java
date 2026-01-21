/**
 * Message serialization and deserialization.
 *
 * <p>Contains the {@link MessageCodec} for JSON encoding/decoding of
 * {@link me.internalizable.numdrassl.api.messaging.ChannelMessage} instances.</p>
 *
 * <h2>Key Class</h2>
 * <ul>
 *   <li>{@link MessageCodec} - Gson-based JSON codec with polymorphic support</li>
 * </ul>
 *
 * @see me.internalizable.numdrassl.api.messaging.TypeAdapter
 */
@javax.annotation.ParametersAreNonnullByDefault
package me.internalizable.numdrassl.messaging.codec;

