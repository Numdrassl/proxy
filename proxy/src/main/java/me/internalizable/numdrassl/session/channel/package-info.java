/**
 * QUIC channel management and packet sending.
 *
 * <p>This package manages the bidirectional QUIC channels and streams that make up
 * a proxy session, and provides thread-safe packet sending utilities.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.session.channel.SessionChannels} - Manages
 *       the QUIC channels and streams for both client (downstream) and backend (upstream)
 *       connections. All operations are thread-safe via atomic references.</li>
 *   <li>{@link me.internalizable.numdrassl.session.channel.PacketSender} - Handles
 *       thread-safe packet sending by ensuring writes execute on the correct Netty
 *       event loop thread. Properly releases ByteBuf resources on failure.</li>
 * </ul>
 *
 * <h2>Channel Architecture</h2>
 * <pre>
 *                SessionChannels
 *                      │
 *         ┌────────────┴────────────┐
 *         │                         │
 *    Client Side              Backend Side
 *         │                         │
 *  ┌──────┴──────┐           ┌──────┴──────┐
 *  │clientChannel│           │backendChannel│
 *  │clientStream │           │backendStream │
 *  └─────────────┘           └──────────────┘
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>Netty channels must be accessed from their event loop thread. The
 * {@code PacketSender} handles this automatically by checking
 * {@code eventLoop().inEventLoop()} and scheduling execution if needed.</p>
 *
 * @see me.internalizable.numdrassl.session.channel.SessionChannels
 * @see me.internalizable.numdrassl.session.channel.PacketSender
 */
package me.internalizable.numdrassl.session.channel;

