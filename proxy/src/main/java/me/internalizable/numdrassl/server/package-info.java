/**
 * Core server components for the Numdrassl QUIC proxy.
 *
 * <p>This package contains the main server infrastructure that handles QUIC networking,
 * backend connections, and session coordination.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.server.ProxyCore} - The main proxy server engine.
 *       Manages QUIC server lifecycle, SSL/TLS, and coordinates all components.</li>
 *   <li>{@link me.internalizable.numdrassl.server.BackendConnector} - Handles outbound QUIC
 *       connections to backend Hytale servers with BBR congestion control.</li>
 * </ul>
 *
 * <h2>Subpackages</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.server.network} - Network utilities including
 *       chat message formatting.</li>
 *   <li>{@link me.internalizable.numdrassl.server.ssl} - SSL/TLS certificate management
 *       and generation.</li>
 *   <li>{@link me.internalizable.numdrassl.server.transfer} - Player server transfer
 *       functionality and referral management.</li>
 * </ul>
 *
 * <h2>Architecture Overview</h2>
 * <pre>
 *                    ┌─────────────────┐
 *                    │   ProxyCore     │
 *                    │  (QUIC Server)  │
 *                    └────────┬────────┘
 *                             │
 *           ┌─────────────────┼─────────────────┐
 *           │                 │                 │
 *           ▼                 ▼                 ▼
 *   ┌───────────────┐ ┌───────────────┐ ┌───────────────┐
 *   │ SessionManager│ │BackendConnector│ │ReferralManager│
 *   └───────────────┘ └───────────────┘ └───────────────┘
 * </pre>
 *
 * @see me.internalizable.numdrassl.session for session management
 * @see me.internalizable.numdrassl.plugin.NumdrasslProxy for the public API implementation
 */
package me.internalizable.numdrassl.server;

