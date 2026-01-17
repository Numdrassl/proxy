/**
 * Authentication state management for sessions.
 *
 * <p>This package handles transient authentication state during the session
 * handshake phase. Once a session is fully connected, sensitive authentication
 * data can be cleared.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.session.auth.SessionAuthState} - Holds
 *       authentication state including TLS certificates, OAuth tokens, and the
 *       original Connect packet needed for server transfers.</li>
 * </ul>
 *
 * <h2>Authentication Flow</h2>
 * <pre>
 * 1. Client connects with mTLS certificate
 * 2. SessionAuthState stores certificate fingerprint
 * 3. Client sends Connect packet with identity token
 * 4. Proxy exchanges tokens with Hytale session service
 * 5. OAuth tokens stored temporarily in SessionAuthState
 * 6. After connection established, sensitive data cleared
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>All fields in {@code SessionAuthState} are volatile for safe visibility
 * across the multiple Netty event loop threads that may access session state.</p>
 *
 * @see me.internalizable.numdrassl.session.auth.SessionAuthState
 */
package me.internalizable.numdrassl.session.auth;

