package me.internalizable.numdrassl.auth;

import me.internalizable.numdrassl.auth.credential.CredentialStore;
import me.internalizable.numdrassl.auth.http.HttpClientFactory;
import me.internalizable.numdrassl.auth.oauth.OAuthDeviceFlow;
import me.internalizable.numdrassl.auth.oauth.OAuthTokenRefresher;
import me.internalizable.numdrassl.auth.session.BackendAuthHandler;
import me.internalizable.numdrassl.auth.session.ClientAuthHandler;
import me.internalizable.numdrassl.auth.session.GameSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.http.HttpClient;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Facade for proxy authentication with Hytale's session service.
 *
 * <p>Composes specialized components for:</p>
 * <ul>
 *   <li>OAuth device flow authentication</li>
 *   <li>Token management and refresh</li>
 *   <li>Game session management</li>
 *   <li>Client and backend authentication handling</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * var authenticator = new ProxyAuthenticator(certPath, keyPath, credentialsPath);
 * authenticator.initialize();
 *
 * // If not authenticated, start device flow
 * if (!authenticator.isAuthenticated()) {
 *     var deviceCode = authenticator.startDeviceCodeFlow().join();
 *     // Display deviceCode.userCode() to user
 *     authenticator.pollDeviceCode(deviceCode.deviceCode(), deviceCode.interval()).join();
 * }
 * }</pre>
 */
public final class ProxyAuthenticator implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyAuthenticator.class);

    // Configuration
    private final String certPath;
    private final String keyPath;
    private final String proxyFingerprint;

    // Components
    private final CredentialStore credentialStore;
    private final HttpClient httpClient;
    private final OAuthDeviceFlow deviceFlow;
    private final OAuthTokenRefresher tokenRefresher;
    private final GameSessionManager sessionManager;
    private final ClientAuthHandler clientAuthHandler;
    private final BackendAuthHandler backendAuthHandler;

    /**
     * Creates a new proxy authenticator.
     *
     * @param certPath path to the proxy's TLS certificate
     * @param keyPath path to the proxy's TLS private key
     * @param credentialStorePath path to store credentials
     */
    public ProxyAuthenticator(
            @Nonnull String certPath,
            @Nonnull String keyPath,
            @Nonnull String credentialStorePath) {

        this.certPath = Objects.requireNonNull(certPath, "certPath");
        this.keyPath = Objects.requireNonNull(keyPath, "keyPath");

        // Compute certificate fingerprint
        this.proxyFingerprint = CertificateExtractor.loadAndComputeFingerprint(certPath).orElse(null);

        // Initialize components
        this.credentialStore = new CredentialStore(credentialStorePath);
        this.httpClient = HttpClientFactory.createTlsClient();
        this.tokenRefresher = new OAuthTokenRefresher(httpClient, credentialStore);
        this.deviceFlow = new OAuthDeviceFlow(httpClient, credentialStore);
        this.sessionManager = new GameSessionManager(httpClient, credentialStore, tokenRefresher);
        this.clientAuthHandler = new ClientAuthHandler(httpClient, sessionManager, proxyFingerprint);
        this.backendAuthHandler = new BackendAuthHandler(httpClient, sessionManager, proxyFingerprint);

        LOGGER.info("ProxyAuthenticator initialized");
        if (proxyFingerprint != null) {
            LOGGER.info("Proxy certificate fingerprint: {}", proxyFingerprint);
        }
    }

    /**
     * Initializes the authenticator and attempts to restore a previous session.
     */
    public void initialize() {
        // Try to restore session from stored credentials
        if (credentialStore.load()) {
            LOGGER.info("Found stored credentials, attempting to restore session...");
            if (sessionManager.createSession()) {
                LOGGER.info("Session restored from stored credentials");
            } else {
                LOGGER.warn("Failed to restore session - use 'auth login' command");
            }
        } else {
            LOGGER.info("No stored credentials found - use 'auth login' command to authenticate");
        }
    }

    // ==================== OAuth Device Flow ====================

    /**
     * Starts the OAuth device code flow.
     *
     * @return device code response for user verification
     */
    @Nonnull
    public CompletableFuture<OAuthDeviceFlow.DeviceCodeResponse> startDeviceCodeFlow() {
        return deviceFlow.startFlow();
    }

    /**
     * Polls for device code completion.
     *
     * @param deviceCode the device code to poll
     * @param interval polling interval in seconds
     * @return true if authentication succeeded
     */
    @Nonnull
    public CompletableFuture<Boolean> pollDeviceCode(@Nonnull String deviceCode, int interval) {
        return deviceFlow.pollForCompletion(deviceCode, interval)
            .thenCompose(success -> {
                if (success) {
                    return CompletableFuture.supplyAsync(sessionManager::createSession);
                }
                return CompletableFuture.completedFuture(false);
            });
    }

    // ==================== Client Authentication (Proxy as Server) ====================

    /**
     * Requests an authorization grant for a connecting client.
     *
     * @param clientUuid client's UUID
     * @param clientUsername client's username
     * @param clientIdentityToken client's identity token (optional)
     * @return auth grant result
     */
    @Nonnull
    public CompletableFuture<ClientAuthHandler.AuthGrantResult> requestAuthGrantForClient(
            @Nonnull UUID clientUuid,
            @Nonnull String clientUsername,
            @Nullable String clientIdentityToken) {

        return clientAuthHandler.requestAuthGrant(clientUuid, clientUsername, clientIdentityToken);
    }

    /**
     * Exchanges a server authorization grant for an access token.
     *
     * @param serverAuthGrant the server authorization grant
     * @return the access token
     */
    @Nonnull
    public CompletableFuture<String> exchangeServerAuthGrant(@Nonnull String serverAuthGrant) {
        return clientAuthHandler.exchangeServerAuthGrant(serverAuthGrant);
    }

    // ==================== Backend Authentication (Proxy as Client) ====================

    /**
     * Exchanges an authorization grant for an access token bound to the proxy's certificate.
     *
     * @param authorizationGrant the authorization grant
     * @return the access token
     */
    @Nonnull
    public CompletableFuture<String> exchangeAuthGrantForToken(@Nonnull String authorizationGrant) {
        return backendAuthHandler.exchangeAuthGrant(authorizationGrant);
    }

    /**
     * Requests a server authorization grant for mutual authentication.
     *
     * @param serverIdentityToken the backend server's identity token
     * @param serverAudience the backend server's audience
     * @return the server authorization grant
     */
    @Nonnull
    public CompletableFuture<String> requestServerAuthGrant(
            @Nonnull String serverIdentityToken,
            @Nonnull String serverAudience) {

        return backendAuthHandler.requestServerAuthGrant(serverIdentityToken, serverAudience);
    }

    /**
     * Requests a new authorization grant for a client to use with the backend.
     *
     * @param clientIdentityToken the client's identity token
     * @param serverAudience the backend server's audience
     * @return the authorization grant
     */
    @Nonnull
    public CompletableFuture<String> requestNewAuthGrantForClient(
            @Nonnull String clientIdentityToken,
            @Nonnull String serverAudience) {

        return backendAuthHandler.requestClientAuthGrant(clientIdentityToken, serverAudience);
    }

    // ==================== Getters ====================

    @Nullable
    public String getProxyFingerprint() {
        return proxyFingerprint;
    }

    @Nullable
    public String getSessionToken() {
        return sessionManager.getSessionToken();
    }

    @Nullable
    public String getIdentityToken() {
        return sessionManager.getIdentityToken();
    }

    public boolean isAuthenticated() {
        return sessionManager.isAuthenticated();
    }

    @Nullable
    public UUID getProfileUuid() {
        return credentialStore.getProfileUuid();
    }

    @Nullable
    public String getProfileUsername() {
        return credentialStore.getProfileUsername();
    }

    @Override
    public void close() {
        sessionManager.close();
    }

    /**
     * @deprecated Use {@link #close()} instead
     */
    @Deprecated
    public void shutdown() {
        close();
    }

    // ==================== Legacy Types (for backward compatibility) ====================

    /**
     * @deprecated Use {@link OAuthDeviceFlow.DeviceCodeResponse} instead
     */
    @Deprecated
    public static class DeviceCodeResponse {
        public String deviceCode;
        public String userCode;
        public String verificationUri;
        public String verificationUriComplete;
        public int expiresIn;
        public int interval;

        public static DeviceCodeResponse from(OAuthDeviceFlow.DeviceCodeResponse response) {
            if (response == null) return null;
            DeviceCodeResponse legacy = new DeviceCodeResponse();
            legacy.deviceCode = response.deviceCode();
            legacy.userCode = response.userCode();
            legacy.verificationUri = response.verificationUri();
            legacy.verificationUriComplete = response.verificationUriComplete();
            legacy.expiresIn = response.expiresIn();
            legacy.interval = response.interval();
            return legacy;
        }
    }

    /**
     * @deprecated Use {@link ClientAuthHandler.AuthGrantResult} instead
     */
    @Deprecated
    public static class AuthGrantResult {
        public final String authorizationGrant;
        public final String serverIdentityToken;

        public AuthGrantResult(String authorizationGrant, String serverIdentityToken) {
            this.authorizationGrant = authorizationGrant;
            this.serverIdentityToken = serverIdentityToken;
        }

        public static AuthGrantResult from(ClientAuthHandler.AuthGrantResult result) {
            if (result == null) return null;
            return new AuthGrantResult(result.authorizationGrant(), result.serverIdentityToken());
        }
    }
}
