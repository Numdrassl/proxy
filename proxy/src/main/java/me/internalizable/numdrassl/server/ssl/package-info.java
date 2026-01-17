/**
 * SSL/TLS certificate management for the proxy server.
 *
 * <p>This package handles SSL certificate generation and management for QUIC connections.
 * The proxy requires certificates for both server-side (accepting client connections) and
 * client-side (connecting to backends) TLS.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.server.ssl.CertificateGenerator} - Utility for
 *       generating self-signed certificates when none are configured. Uses Netty's
 *       {@code SelfSignedCertificate} for simplicity.</li>
 * </ul>
 *
 * <h2>Certificate Flow</h2>
 * <pre>
 * Startup:
 *   1. Check if certificates exist at configured paths
 *   2. If not, generate self-signed certificates
 *   3. Load certificates into QUIC SSL contexts
 *
 * Runtime:
 *   - Server SSL context: Accepts client connections with mTLS
 *   - Client SSL context: Connects to backends with same certificate
 * </pre>
 *
 * <h2>Production Note</h2>
 * <p>For production deployments, replace self-signed certificates with proper
 * certificates signed by a trusted CA or the Hytale certificate authority.</p>
 *
 * @see me.internalizable.numdrassl.server.ssl.CertificateGenerator
 */
package me.internalizable.numdrassl.server.ssl;

