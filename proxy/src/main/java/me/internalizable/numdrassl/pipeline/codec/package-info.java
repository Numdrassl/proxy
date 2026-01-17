/**
 * Packet codecs for encoding and decoding Hytale protocol packets.
 *
 * <p>This package provides Netty codecs for the Hytale binary protocol.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.pipeline.codec.ProxyPacketDecoder} - Decodes
 *       incoming bytes into {@link com.hypixel.hytale.protocol.Packet} objects.
 *       Unknown packets are forwarded as raw {@link io.netty.buffer.ByteBuf}.</li>
 *   <li>{@link me.internalizable.numdrassl.pipeline.codec.ProxyPacketEncoder} - Encodes
 *       {@link com.hypixel.hytale.protocol.Packet} objects into bytes. Raw buffers
 *       are forwarded as-is.</li>
 * </ul>
 *
 * <h2>Packet Format</h2>
 * <pre>
 * ┌────────────────┬────────────────┬──────────────────┐
 * │ Payload Length │   Packet ID    │     Payload      │
 * │   (4 bytes)    │   (4 bytes)    │   (variable)     │
 * │  Little-Endian │  Little-Endian │                  │
 * └────────────────┴────────────────┴──────────────────┘
 * </pre>
 *
 * <h2>Unknown Packet Handling</h2>
 * <p>When a packet ID is not found in {@link com.hypixel.hytale.protocol.PacketRegistry},
 * the decoder forwards the raw bytes as a {@link io.netty.buffer.ByteBuf} to allow
 * transparent proxying of new or proprietary packet types.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>{@code ProxyPacketEncoder} is marked {@code @Sharable} and can be reused
 * across multiple channels. {@code ProxyPacketDecoder} is not sharable and must
 * be created per-channel.</p>
 *
 * @see me.internalizable.numdrassl.pipeline
 */
package me.internalizable.numdrassl.pipeline.codec;

