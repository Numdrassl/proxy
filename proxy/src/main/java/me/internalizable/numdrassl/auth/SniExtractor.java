package me.internalizable.numdrassl.auth;

import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLEngine;
import java.net.InetAddress;
import java.util.Objects;

/**
 * Utility for extracting SNI (Server Name Indication) from QUIC/TLS connections.
 *
 * <p>SNI allows clients to specify which hostname they're connecting to during
 * the TLS handshake, enabling host-based routing on the proxy.</p>
 *
 * <p><b>Note:</b> SNI extraction in QUIC/TLS requires proper SSL context configuration.
 * The proxy must be configured to capture SNI during the handshake. This implementation
 * attempts to extract SNI from various sources, but may return null if SNI is not
 * available or not properly configured.</p>
 */
public final class SniExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SniExtractor.class);

    /**
     * Attribute key for storing SNI hostname in QUIC channels.
     * This is set by Netty's SNI handler during the TLS handshake.
     */
    private static final AttributeKey<String> SNI_HOSTNAME_KEY = AttributeKey.valueOf("SNI_HOSTNAME");

    private SniExtractor() {
        // Utility class
    }

    /**
     * Extracts the SNI hostname from a QUIC channel.
     *
     * <p>This method attempts to extract the hostname that the client specified
     * during the TLS handshake. Returns null if SNI is not available or not provided.</p>
     *
     * <p><b>Note:</b> SNI extraction requires the TLS handshake to be completed or in progress.
     * If called too early, this method may return null even if SNI was provided.</p>
     *
     * <p>Extraction methods (in order):</p>
     * <ol>
     *   <li>Channel attribute (set by SNI handler during handshake)</li>
     *   <li>Netty's standard SNI attribute</li>
     *   <li>SSL session peer host (may not be reliable for server-side SNI)</li>
     * </ol>
     *
     * @param channel the QUIC channel (handshake may be in progress or completed)
     * @return the SNI hostname, or null if not available
     */
    @Nullable
    public static String extractHostname(@Nonnull QuicChannel channel) {
        Objects.requireNonNull(channel, "channel");

        try {
            // Method 1: Check channel attribute (set by SNI handler during handshake)
            String hostname = channel.attr(SNI_HOSTNAME_KEY).get();
            if (hostname != null && !hostname.isEmpty()) {
                LOGGER.debug("Extracted hostname from channel attribute: {}", hostname);
                return hostname;
            }

            // Method 2: Try Netty's standard SNI attribute
            try {
                @SuppressWarnings("unchecked")
                AttributeKey<String> nettySniKey = (AttributeKey<String>) AttributeKey.valueOf("io.netty.handler.ssl.SNI_HOSTNAME");
                String nettyHostname = channel.attr(nettySniKey).get();
                if (nettyHostname != null && !nettyHostname.isEmpty()) {
                    LOGGER.debug("Extracted hostname from Netty SNI attribute: {}", nettyHostname);
                    return nettyHostname;
                }
            } catch (IllegalStateException e) {
                // Attribute key might not exist, continue to next method
                LOGGER.trace("Netty SNI attribute not available: {}", e.getMessage());
            }

            // Method 3: Try SSL session peer host (less reliable for server-side SNI)
            // Note: This may not work reliably as peerHost is typically set by the client
            // for client-side connections, not server-side SNI extraction
            SSLEngine sslEngine = channel.sslEngine();
            if (sslEngine != null) {
                try {
                    javax.net.ssl.SSLSession session = sslEngine.getSession();
                    if (session != null) {
                        String peerHost = session.getPeerHost();
                        // Only use if it looks like a hostname (contains dots, not an IP)
                        if (peerHost != null && !peerHost.isEmpty() && 
                            !peerHost.equals("null") && peerHost.contains(".") &&
                            !looksLikeIpAddress(peerHost)) {
                            LOGGER.debug("Extracted hostname from SSL session peer host: {}", peerHost);
                            return peerHost;
                        }
                    }
                } catch (Exception e) {
                    // SSL session might not be ready yet
                    LOGGER.debug("SSL session not ready for SNI extraction: {}", e.getMessage());
                }
            }

            return null;

        } catch (Exception e) {
            LOGGER.debug("Failed to extract SNI hostname: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Sets the SNI hostname in a channel attribute.
     * This is typically called by an SNI handler during the TLS handshake.
     *
     * @param channel the QUIC channel
     * @param hostname the SNI hostname
     */
    public static void setHostname(@Nonnull QuicChannel channel, @Nonnull String hostname) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(hostname, "hostname");
        channel.attr(SNI_HOSTNAME_KEY).set(hostname);
        LOGGER.debug("Set SNI hostname in channel: {}", hostname);
    }

    /**
     * Checks if a string looks like an IP address (IPv4 or IPv6).
     *
     * @param host the host string to check
     * @return true if the string is a valid IP address, false otherwise
     */
    private static boolean looksLikeIpAddress(@Nonnull String host) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            // If the hostname resolves to the same string, it's an IP address
            return addr.getHostAddress().equals(host);
        } catch (Exception e) {
            // If parsing fails, it's not a valid IP address
            return false;
        }
    }
}
