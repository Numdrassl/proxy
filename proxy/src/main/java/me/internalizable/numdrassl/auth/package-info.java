/**
 * Authentication system for the Numdrassl proxy.
 *
 * <p>Handles OAuth authentication with Hytale accounts and session management
 * with Hytale's session service.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.auth.ProxyAuthenticator} -
 *       Main facade for authentication operations</li>
 *   <li>{@link me.internalizable.numdrassl.auth.CertificateExtractor} -
 *       Utilities for X.509 certificate handling</li>
 * </ul>
 *
 * <h2>Subpackages</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.auth.oauth} - OAuth device flow implementation</li>
 *   <li>{@link me.internalizable.numdrassl.auth.session} - Game session management</li>
 *   <li>{@link me.internalizable.numdrassl.auth.credential} - Credential storage</li>
 *   <li>{@link me.internalizable.numdrassl.auth.http} - HTTP client utilities</li>
 * </ul>
 *
 * <h2>Authentication Flow</h2>
 * <ol>
 *   <li>User initiates device code flow via {@code auth login}</li>
 *   <li>OAuth tokens are obtained and stored</li>
 *   <li>Game session is created with Hytale session service</li>
 *   <li>Proxy can now authenticate clients and connect to backends</li>
 * </ol>
 */
package me.internalizable.numdrassl.auth;

