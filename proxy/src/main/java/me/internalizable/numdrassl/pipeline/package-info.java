/**
 * Netty pipeline components for Hytale protocol handling.
 *
 * <p>This package provides the network pipeline infrastructure for the proxy,
 * handling packet encoding/decoding and routing between clients and backend servers.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.pipeline.ClientPacketHandler} - Handles packets
 *       from downstream clients. Delegates to specialized handlers for authentication
 *       and backend connection.</li>
 *   <li>{@link me.internalizable.numdrassl.pipeline.BackendPacketHandler} - Handles packets
 *       from upstream backend servers. Forwards to clients and handles connection lifecycle.</li>
 *   <li>{@link me.internalizable.numdrassl.pipeline.RawPacket} - Wrapper for unknown packets
 *       that are forwarded without decoding.</li>
 * </ul>
 *
 * <h2>Subpackages</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.pipeline.codec} - Packet codecs for
 *       encoding/decoding Hytale protocol.</li>
 *   <li>{@link me.internalizable.numdrassl.pipeline.handler} - Specialized handlers for
 *       authentication and connection management.</li>
 * </ul>
 *
 * <h2>Pipeline Architecture</h2>
 * <pre>
 *                      Client Pipeline
 *   ┌─────────────────────────────────────────────┐
 *   │ QUIC Channel                                │
 *   │    ▼                                        │
 *   │ ProxyPacketDecoder                          │
 *   │    ▼                                        │
 *   │ ClientPacketHandler                         │
 *   │    │                                        │
 *   │    ├─► ClientAuthenticationHandler          │
 *   │    └─► BackendConnectionHandler             │
 *   │    ▼                                        │
 *   │ ProxyPacketEncoder                          │
 *   └─────────────────────────────────────────────┘
 *
 *                     Backend Pipeline
 *   ┌─────────────────────────────────────────────┐
 *   │ QUIC Channel                                │
 *   │    ▼                                        │
 *   │ ProxyPacketDecoder                          │
 *   │    ▼                                        │
 *   │ BackendPacketHandler                        │
 *   │    ▼                                        │
 *   │ ProxyPacketEncoder                          │
 *   └─────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Memory Management</h2>
 * <p>This package handles Netty {@link io.netty.buffer.ByteBuf} objects carefully:</p>
 * <ul>
 *   <li>Buffers are retained when forwarding to another channel</li>
 *   <li>Buffers are released when dropping packets</li>
 *   <li>{@link me.internalizable.numdrassl.pipeline.RawPacket} implements
 *       {@link io.netty.util.ReferenceCounted} for proper tracking</li>
 * </ul>
 *
 * @see me.internalizable.numdrassl.pipeline.codec
 * @see me.internalizable.numdrassl.pipeline.handler
 */
package me.internalizable.numdrassl.pipeline;

