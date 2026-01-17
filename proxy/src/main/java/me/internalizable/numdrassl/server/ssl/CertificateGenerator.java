package me.internalizable.numdrassl.server.ssl;

import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.cert.CertificateException;
import java.util.Objects;

/**
 * Generates self-signed SSL certificates for the QUIC proxy server.
 *
 * <p>Uses Netty's {@link SelfSignedCertificate} utility for certificate generation.
 * The generated certificates are suitable for development and testing.</p>
 */
public final class CertificateGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(CertificateGenerator.class);
    private static final String DEFAULT_CN = "Hytale Proxy";

    private CertificateGenerator() {
        // Utility class
    }

    /**
     * Generates a self-signed certificate and private key.
     *
     * <p>If parent directories don't exist, they will be created.</p>
     *
     * @param certPath path where the certificate will be saved
     * @param keyPath path where the private key will be saved
     * @throws CertificateException if certificate generation fails
     * @throws IOException if file operations fail
     */
    public static void generateSelfSigned(@Nonnull String certPath, @Nonnull String keyPath)
            throws CertificateException, IOException {
        generateSelfSigned(certPath, keyPath, DEFAULT_CN);
    }

    /**
     * Generates a self-signed certificate and private key with a custom CN.
     *
     * @param certPath path where the certificate will be saved
     * @param keyPath path where the private key will be saved
     * @param commonName the certificate common name (CN)
     * @throws CertificateException if certificate generation fails
     * @throws IOException if file operations fail
     */
    public static void generateSelfSigned(
            @Nonnull String certPath,
            @Nonnull String keyPath,
            @Nonnull String commonName) throws CertificateException, IOException {

        Objects.requireNonNull(certPath, "certPath");
        Objects.requireNonNull(keyPath, "keyPath");
        Objects.requireNonNull(commonName, "commonName");

        LOGGER.info("Generating self-signed SSL certificate (CN={})...", commonName);

        createParentDirectories(certPath);
        createParentDirectories(keyPath);

        SelfSignedCertificate ssc = new SelfSignedCertificate(commonName);

        try {
            copyFile(ssc.certificate().toPath(), Paths.get(certPath));
            copyFile(ssc.privateKey().toPath(), Paths.get(keyPath));

            LOGGER.info("Self-signed certificate generated: {}", certPath);
            LOGGER.info("Private key generated: {}", keyPath);
        } finally {
            ssc.delete();
        }
    }

    private static void createParentDirectories(String path) throws IOException {
        Path parent = Paths.get(path).getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static void copyFile(Path source, Path target) throws IOException {
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Checks if both certificate and key files exist.
     *
     * @param certPath the certificate path
     * @param keyPath the key path
     * @return true if both files exist
     */
    public static boolean certificatesExist(@Nonnull String certPath, @Nonnull String keyPath) {
        Objects.requireNonNull(certPath, "certPath");
        Objects.requireNonNull(keyPath, "keyPath");

        return Files.exists(Paths.get(certPath)) && Files.exists(Paths.get(keyPath));
    }
}

