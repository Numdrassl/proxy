/**
 * Session management for proxy connections.
 *
 * <p>This package manages the lifecycle and state of player sessions connected
 * through the proxy. Each connected player has a {@link ProxySession} that tracks
 * their connection state, identity, and channels.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.session.ProxySession} - Represents a connected
 *       player's session. Coordinates identity, channels, authentication, and packet sending.</li>
 *   <li>{@link me.internalizable.numdrassl.session.SessionManager} - Thread-safe registry
 *       for all active sessions with multiple lookup strategies (by ID, UUID, or channel).</li>
 *   <li>{@link me.internalizable.numdrassl.session.SessionState} - Enum representing the
 *       lifecycle states of a session (HANDSHAKING → CONNECTED → DISCONNECTED).</li>
 * </ul>
 *
 * <h2>Subpackages</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.session.auth} - Authentication state during
 *       the handshake phase.</li>
 *   <li>{@link me.internalizable.numdrassl.session.channel} - QUIC channel management
 *       and thread-safe packet sending.</li>
 *   <li>{@link me.internalizable.numdrassl.session.identity} - Immutable player identity
 *       information.</li>
 * </ul>
 *
 * <h2>Session Lifecycle</h2>
 * <pre>
 * HANDSHAKING ──► AUTHENTICATING ──► CONNECTING ──► CONNECTED
 *                                                       │
 *                                                       ▼
 *                                                 TRANSFERRING ──► CONNECTED
 *                                                       │
 *                                                       ▼
 *                                                 DISCONNECTED
 * </pre>
 *
 * @see me.internalizable.numdrassl.session.ProxySession
 * @see me.internalizable.numdrassl.session.SessionManager
 */
package me.internalizable.numdrassl.session;

