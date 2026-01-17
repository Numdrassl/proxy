/**
 * Specialized packet handlers for authentication and connection management.
 *
 * <p>This package contains focused handler classes that implement specific
 * responsibilities, extracted from the main packet handlers for SRP compliance.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.pipeline.handler.ClientAuthenticationHandler} -
 *       Handles the client authentication flow with the proxy, including
 *       Connect/AuthToken packets and OAuth token exchange.</li>
 *   <li>{@link me.internalizable.numdrassl.pipeline.handler.BackendConnectionHandler} -
 *       Handles connecting authenticated clients to backend servers,
 *       including referral resolution for server transfers.</li>
 * </ul>
 *
 * <h2>Authentication Flow</h2>
 * <pre>
 * ClientAuthenticationHandler:
 *
 *   Client                    Proxy                    Hytale Sessions
 *      │                        │                             │
 *      │─── Connect ──────────►│                             │
 *      │                        │── requestAuthGrant ───────►│
 *      │                        │◄─ AuthGrantResult ─────────│
 *      │◄── AuthGrant ─────────│                             │
 *      │                        │                             │
 *      │─── AuthToken ────────►│                             │
 *      │                        │── exchangeServerAuthGrant ►│
 *      │                        │◄─ serverAccessToken ───────│
 *      │◄── ServerAuthToken ───│                             │
 *      │                        │                             │
 *      └─────── onAuthenticationComplete() ──────────────────►
 * </pre>
 *
 * <h2>Backend Connection</h2>
 * <pre>
 * BackendConnectionHandler:
 *
 *   1. Check for pending referral (server transfer)
 *   2. Fall back to default backend if no referral
 *   3. Update session state to CONNECTING
 *   4. Delegate to BackendConnector with HMAC-signed referral
 * </pre>
 *
 * @see me.internalizable.numdrassl.pipeline.ClientPacketHandler
 */
package me.internalizable.numdrassl.pipeline.handler;

