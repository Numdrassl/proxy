package me.internalizable.numdrassl.auth;

import io.netty.incubator.codec.quic.QuicChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * Utilities for extracting and computing fingerprints from X.509 certificates.
 *
 * <p>Provides methods for:</p>
 * <ul>
 *   <li>Extracting client certificates from QUIC channels</li>
 *   <li>Loading certificates from files</li>
 *   <li>Computing SHA-256 fingerprints in Hytale-compatible format</li>
 * </ul>
 */
public final class CertificateExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CertificateExtractor.class);
    private static final String FINGERPRINT_ALGORITHM = "SHA-256";

    private CertificateExtractor() {
        // Utility class
    }

    /**
     * Extracts the client's X.509 certificate from a QUIC channel.
     *
     * @param channel the QUIC channel with completed TLS handshake
     * @return the client certificate, or empty if not available
     */
    @Nonnull
    public static Optional<X509Certificate> extractFromChannel(@Nonnull QuicChannel channel) {
        Objects.requireNonNull(channel, "channel");

        try {
            SSLEngine sslEngine = channel.sslEngine();
            if (sslEngine == null) {
                LOGGER.debug("No SSL engine available on channel");
                return Optional.empty();
            }

            Certificate[] peerCerts = sslEngine.getSession().getPeerCertificates();
            if (peerCerts == null || peerCerts.length == 0) {
                LOGGER.debug("No peer certificates available");
                return Optional.empty();
            }

            if (peerCerts[0] instanceof X509Certificate cert) {
                LOGGER.debug("Extracted client certificate: {}", cert.getSubjectX500Principal().getName());
                return Optional.of(cert);
            }

            LOGGER.debug("Peer certificate is not X.509");
            return Optional.empty();

        } catch (SSLPeerUnverifiedException e) {
            LOGGER.debug("Failed to extract client certificate: peer not verified");
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.debug("Failed to extract client certificate: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Loads an X.509 certificate from a file.
     *
     * @param certPath path to the certificate file
     * @return the certificate, or empty if loading failed
     */
    @Nonnull
    public static Optional<X509Certificate> loadFromFile(@Nonnull String certPath) {
        Objects.requireNonNull(certPath, "certPath");

        File certFile = new File(certPath);
        if (!certFile.exists()) {
            LOGGER.warn("Certificate file not found: {}", certPath);
            return Optional.empty();
        }

        try (FileInputStream fis = new FileInputStream(certFile)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate cert = cf.generateCertificate(fis);

            if (cert instanceof X509Certificate x509) {
                return Optional.of(x509);
            }

            LOGGER.warn("Certificate is not X.509: {}", certPath);
            return Optional.empty();

        } catch (Exception e) {
            LOGGER.error("Failed to load certificate from {}: {}", certPath, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Computes the SHA-256 fingerprint of a certificate in URL-safe Base64 format.
     *
     * <p>This matches the format Hytale uses for certificate binding.</p>
     *
     * @param certificate the certificate to fingerprint
     * @return the fingerprint, or empty on failure
     */
    @Nonnull
    public static Optional<String> computeFingerprint(@Nonnull X509Certificate certificate) {
        Objects.requireNonNull(certificate, "certificate");

        try {
            MessageDigest digest = MessageDigest.getInstance(FINGERPRINT_ALGORITHM);
            byte[] hash = digest.digest(certificate.getEncoded());
            String fingerprint = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return Optional.of(fingerprint);

        } catch (Exception e) {
            LOGGER.error("Failed to compute certificate fingerprint", e);
            return Optional.empty();
        }
    }

    /**
     * Extracts client certificate and computes its fingerprint in one step.
     *
     * @param channel the QUIC channel
     * @return the fingerprint, or empty if extraction or computation failed
     */
    @Nonnull
    public static Optional<String> extractFingerprint(@Nonnull QuicChannel channel) {
        return extractFromChannel(channel).flatMap(CertificateExtractor::computeFingerprint);
    }

    /**
     * Loads certificate from file and computes its fingerprint.
     *
     * @param certPath path to the certificate file
     * @return the fingerprint, or empty if loading or computation failed
     */
    @Nonnull
    public static Optional<String> loadAndComputeFingerprint(@Nonnull String certPath) {
        return loadFromFile(certPath).flatMap(CertificateExtractor::computeFingerprint);
    }

    // ==================== Legacy Methods (for backward compatibility) ====================

    /**
     * @deprecated Use {@link #extractFromChannel(QuicChannel)} instead
     */
    @Deprecated
    @Nullable
    public static X509Certificate extractClientCertificate(@Nonnull QuicChannel channel) {
        return extractFromChannel(channel).orElse(null);
    }

    /**
     * @deprecated Use {@link #computeFingerprint(X509Certificate)} instead
     */
    @Deprecated
    @Nullable
    public static String computeCertificateFingerprint(@Nonnull X509Certificate certificate) {
        return computeFingerprint(certificate).orElse(null);
    }

    /**
     * @deprecated Use {@link #extractFingerprint(QuicChannel)} instead
     */
    @Deprecated
    @Nullable
    public static String extractClientCertificateFingerprint(@Nonnull QuicChannel channel) {
        return extractFingerprint(channel).orElse(null);
    }
}

