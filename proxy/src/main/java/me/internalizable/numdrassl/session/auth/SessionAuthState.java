package me.internalizable.numdrassl.session.auth;

import com.hypixel.hytale.protocol.packets.connection.Connect;

import javax.annotation.Nullable;
import java.security.cert.X509Certificate;

/**
 * Holds authentication state during the client-proxy handshake.
 *
 * <p>This state is transient - it's populated during authentication and
 * can be cleared after the session is fully established.</p>
 *
 * <p>Thread-safety: All fields are volatile for visibility across threads.</p>
 */
public final class SessionAuthState {

    // Client's TLS certificate from mTLS handshake
    private volatile X509Certificate clientCertificate;
    private volatile String certificateFingerprint;

    // Original Connect packet (needed for server transfers)
    private volatile Connect originalConnect;

    // OAuth tokens during auth flow
    private volatile String authorizationGrant;
    private volatile String accessToken;

    // ==================== Certificate ====================

    @Nullable
    public X509Certificate clientCertificate() {
        return clientCertificate;
    }

    public void setClientCertificate(@Nullable X509Certificate cert, @Nullable String fingerprint) {
        this.clientCertificate = cert;
        this.certificateFingerprint = fingerprint;
    }

    @Nullable
    public String certificateFingerprint() {
        return certificateFingerprint;
    }

    // ==================== Connect Packet ====================

    @Nullable
    public Connect originalConnect() {
        return originalConnect;
    }

    public void setOriginalConnect(@Nullable Connect connect) {
        this.originalConnect = connect;
    }

    // ==================== OAuth Tokens ====================

    @Nullable
    public String authorizationGrant() {
        return authorizationGrant;
    }

    public void setAuthorizationGrant(@Nullable String grant) {
        this.authorizationGrant = grant;
    }

    @Nullable
    public String accessToken() {
        return accessToken;
    }

    public void setAccessToken(@Nullable String token) {
        this.accessToken = token;
    }

    /**
     * Clears sensitive auth data after connection is established.
     * The original connect packet is preserved for server transfers.
     */
    public void clearSensitiveData() {
        this.authorizationGrant = null;
        this.accessToken = null;
    }

    /**
     * Checks if the client has presented a valid certificate.
     */
    public boolean hasCertificate() {
        return clientCertificate != null;
    }
}

