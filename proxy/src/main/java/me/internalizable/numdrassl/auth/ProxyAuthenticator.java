package me.internalizable.numdrassl.auth;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.*;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Handles proxy authentication with Hytale's session service.
 *
 * The proxy acts as a Hytale server and authenticates using OAuth.
 * This allows the proxy to exchange authorization grants for access tokens
 * that are bound to the proxy's certificate.
 *
 * Based on ServerAuthManager and OAuthClient from Hytale server.
 */
public class ProxyAuthenticator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyAuthenticator.class);

    // Correct Hytale OAuth/API endpoints
    private static final String OAUTH_TOKEN_URL = "https://oauth.accounts.hytale.com/oauth2/token";
    private static final String DEVICE_AUTH_URL = "https://oauth.accounts.hytale.com/oauth2/device/auth";
    private static final String SESSION_SERVICE_URL = "https://sessions.hytale.com";
    private static final String ACCOUNT_DATA_URL = "https://account-data.hytale.com";

    private static final String CLIENT_ID = "hytale-server";
    private static final String[] SCOPES = {"openid", "offline", "auth:server"};
    private static final String USER_AGENT = "NumdrasslProxy/1.0";

    private static final int REFRESH_BUFFER_SECONDS = 300;
    private static final Gson GSON = new Gson();

    private final String certPath;
    private final String keyPath;
    private final String credentialStorePath;
    private final String proxyFingerprint;

    private HttpClient httpClient;
    private final ScheduledExecutorService refreshScheduler;

    // OAuth tokens (from OAuth flow)
    private volatile String oauthAccessToken;
    private volatile String oauthRefreshToken;
    private volatile Instant oauthTokenExpiry;

    // Game session tokens (from session service)
    private volatile String sessionToken;
    private volatile String identityToken;
    private volatile Instant sessionExpiry;

    // Selected profile
    private volatile UUID profileUuid;
    private volatile String profileUsername;

    public ProxyAuthenticator(@Nonnull String certPath, @Nonnull String keyPath, @Nonnull String credentialStorePath) {
        this.certPath = certPath;
        this.keyPath = keyPath;
        this.credentialStorePath = credentialStorePath;
        this.proxyFingerprint = computeCertFingerprint(certPath);
        this.refreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ProxyAuth-TokenRefresh");
            t.setDaemon(true);
            return t;
        });

        LOGGER.info("ProxyAuthenticator initialized");
        if (proxyFingerprint != null) {
            LOGGER.info("Proxy certificate fingerprint: {}", proxyFingerprint);
        }
    }

    private String computeCertFingerprint(String certPath) {
        try {
            File certFile = new File(certPath);
            if (!certFile.exists()) {
                LOGGER.warn("Certificate file not found: {}", certPath);
                return null;
            }
            java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(new FileInputStream(certFile));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(cert.getEncoded());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            LOGGER.error("Failed to compute certificate fingerprint", e);
            return null;
        }
    }

    /**
     * Initialize the HTTP client and try to restore session from stored credentials.
     */
    public void initialize() {
        try {
            // Create SSL context
            SSLContext sslContext = SSLContext.getInstance("TLS");

            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            this.httpClient = HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

            LOGGER.info("HTTP client initialized for session service communication");

            // Try to restore session from stored credentials
            if (loadStoredCredentials()) {
                LOGGER.info("Found stored credentials, attempting to restore session...");
                if (createGameSessionFromOAuth()) {
                    LOGGER.info("Session restored from stored credentials");
                } else {
                    LOGGER.warn("Failed to restore session - use 'auth login' command");
                }
            } else {
                LOGGER.info("No stored credentials found - use 'auth login' command to authenticate");
            }

        } catch (Exception e) {
            LOGGER.error("Failed to initialize HTTP client", e);
            throw new RuntimeException("Failed to initialize ProxyAuthenticator", e);
        }
    }

    // ==================== Credential Storage ====================

    private boolean loadStoredCredentials() {
        try {
            Path path = Path.of(credentialStorePath);
            if (!Files.exists(path)) {
                return false;
            }

            String json = Files.readString(path);
            JsonObject stored = JsonParser.parseString(json).getAsJsonObject();

            if (stored.has("oauth_access_token")) {
                this.oauthAccessToken = stored.get("oauth_access_token").getAsString();
            }
            if (stored.has("oauth_refresh_token")) {
                this.oauthRefreshToken = stored.get("oauth_refresh_token").getAsString();
            }
            if (stored.has("oauth_expiry")) {
                this.oauthTokenExpiry = Instant.ofEpochSecond(stored.get("oauth_expiry").getAsLong());
            }
            if (stored.has("profile_uuid")) {
                this.profileUuid = UUID.fromString(stored.get("profile_uuid").getAsString());
            }
            if (stored.has("profile_username")) {
                this.profileUsername = stored.get("profile_username").getAsString();
            }

            return oauthRefreshToken != null;

        } catch (Exception e) {
            LOGGER.warn("Failed to load stored credentials: {}", e.getMessage());
            return false;
        }
    }

    private void saveCredentials() {
        try {
            JsonObject stored = new JsonObject();
            if (oauthAccessToken != null) stored.addProperty("oauth_access_token", oauthAccessToken);
            if (oauthRefreshToken != null) stored.addProperty("oauth_refresh_token", oauthRefreshToken);
            if (oauthTokenExpiry != null) stored.addProperty("oauth_expiry", oauthTokenExpiry.getEpochSecond());
            if (profileUuid != null) stored.addProperty("profile_uuid", profileUuid.toString());
            if (profileUsername != null) stored.addProperty("profile_username", profileUsername);

            Path path = Path.of(credentialStorePath);
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(stored));

            LOGGER.debug("Credentials saved to {}", credentialStorePath);

        } catch (Exception e) {
            LOGGER.error("Failed to save credentials", e);
        }
    }

    // ==================== OAuth Device Flow ====================

    /**
     * Start device code OAuth flow (for headless servers).
     * Returns the user code and verification URL.
     */
    public CompletableFuture<DeviceCodeResponse> startDeviceCodeFlow() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info("Starting OAuth device code flow...");

                String scope = String.join(" ", SCOPES);
                String body = "client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8) +
                              "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DEVICE_AUTH_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("User-Agent", USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(30))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                    DeviceCodeResponse dcr = new DeviceCodeResponse();
                    dcr.deviceCode = getJsonString(json, "device_code");
                    dcr.userCode = getJsonString(json, "user_code");
                    dcr.verificationUri = getJsonString(json, "verification_uri");
                    dcr.verificationUriComplete = getJsonString(json, "verification_uri_complete");
                    dcr.expiresIn = getJsonInt(json, "expires_in", 600);
                    dcr.interval = getJsonInt(json, "interval", 5);

                    LOGGER.info("Device code obtained. User code: {}", dcr.userCode);
                    LOGGER.info("Please visit: {}", dcr.verificationUri);

                    return dcr;
                }

                LOGGER.error("Failed to get device code: HTTP {} - {}", response.statusCode(), response.body());
                return null;

            } catch (Exception e) {
                LOGGER.error("Error starting device code flow", e);
                return null;
            }
        });
    }

    /**
     * Poll for device code completion.
     */
    public CompletableFuture<Boolean> pollDeviceCode(String deviceCode, int interval) {
        final int[] pollInterval = {Math.max(interval, 5)};
        return CompletableFuture.supplyAsync(() -> {
            try {
                while (true) {
                    Thread.sleep(pollInterval[0] * 1000L);

                    String body = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:device_code", StandardCharsets.UTF_8) +
                                  "&client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8) +
                                  "&device_code=" + URLEncoder.encode(deviceCode, StandardCharsets.UTF_8);

                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(OAUTH_TOKEN_URL))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("User-Agent", USER_AGENT)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(Duration.ofSeconds(30))
                        .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                    if (response.statusCode() == 200) {
                        // Success!
                        this.oauthAccessToken = getJsonString(json, "access_token");
                        this.oauthRefreshToken = getJsonString(json, "refresh_token");
                        int expiresIn = getJsonInt(json, "expires_in", 3600);
                        this.oauthTokenExpiry = Instant.now().plusSeconds(expiresIn);

                        saveCredentials();
                        LOGGER.info("OAuth authentication successful!");

                        // Now create game session
                        return createGameSessionFromOAuth();
                    }

                    // Check for error
                    String error = getJsonString(json, "error");
                    if (error != null) {
                        if ("authorization_pending".equals(error)) {
                            LOGGER.debug("Waiting for user authorization...");
                            continue;
                        } else if ("slow_down".equals(error)) {
                            pollInterval[0] += 5;
                            LOGGER.debug("Slowing down polling to {} seconds", pollInterval[0]);
                            continue;
                        } else if ("expired_token".equals(error)) {
                            LOGGER.error("Device code expired");
                            return false;
                        } else if ("access_denied".equals(error)) {
                            LOGGER.error("User denied authorization");
                            return false;
                        } else {
                            LOGGER.error("OAuth error: {}", error);
                            return false;
                        }
                    }

                    LOGGER.error("OAuth token request failed: HTTP {} - {}", response.statusCode(), response.body());
                    return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                LOGGER.error("Error polling device code", e);
                return false;
            }
        });
    }

    /**
     * Refresh OAuth tokens if needed.
     */
    private boolean refreshOAuthTokens() {
        if (oauthRefreshToken == null) {
            LOGGER.warn("No refresh token available");
            return false;
        }

        // Check if refresh is needed
        if (oauthTokenExpiry != null && oauthTokenExpiry.isAfter(Instant.now().plusSeconds(REFRESH_BUFFER_SECONDS))) {
            return true; // Token still valid
        }

        try {
            LOGGER.info("Refreshing OAuth tokens...");

            String body = "grant_type=refresh_token" +
                          "&client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8) +
                          "&refresh_token=" + URLEncoder.encode(oauthRefreshToken, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OAUTH_TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                this.oauthAccessToken = getJsonString(json, "access_token");
                String newRefreshToken = getJsonString(json, "refresh_token");
                if (newRefreshToken != null) {
                    this.oauthRefreshToken = newRefreshToken;
                }
                int expiresIn = getJsonInt(json, "expires_in", 3600);
                this.oauthTokenExpiry = Instant.now().plusSeconds(expiresIn);

                saveCredentials();
                LOGGER.info("OAuth tokens refreshed");
                return true;
            }

            LOGGER.error("Failed to refresh OAuth tokens: HTTP {} - {}", response.statusCode(), response.body());
            return false;

        } catch (Exception e) {
            LOGGER.error("Error refreshing OAuth tokens", e);
            return false;
        }
    }

    // ==================== Game Session ====================

    /**
     * Create a game session using OAuth tokens.
     * This gets us the sessionToken and identityToken needed to act as a server.
     */
    private boolean createGameSessionFromOAuth() {
        if (!refreshOAuthTokens()) {
            return false;
        }

        try {
            // First, get available game profiles
            HttpRequest profilesRequest = HttpRequest.newBuilder()
                .uri(URI.create(ACCOUNT_DATA_URL + "/my-account/get-profiles"))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + oauthAccessToken)
                .header("User-Agent", USER_AGENT)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> profilesResponse = httpClient.send(profilesRequest, HttpResponse.BodyHandlers.ofString());

            if (profilesResponse.statusCode() != 200) {
                LOGGER.error("Failed to get game profiles: HTTP {} - {}", profilesResponse.statusCode(), profilesResponse.body());
                return false;
            }

            JsonObject profilesData = JsonParser.parseString(profilesResponse.body()).getAsJsonObject();
            JsonArray profiles = profilesData.has("profiles") ? profilesData.getAsJsonArray("profiles") : null;

            if (profiles == null || profiles.isEmpty()) {
                LOGGER.error("No game profiles found for this account");
                return false;
            }

            // Select first profile or previously selected
            JsonObject selectedProfile = null;
            for (int i = 0; i < profiles.size(); i++) {
                JsonObject profile = profiles.get(i).getAsJsonObject();
                String uuidStr = getJsonString(profile, "uuid");
                if (uuidStr != null) {
                    UUID uuid = UUID.fromString(uuidStr);
                    if (profileUuid != null && profileUuid.equals(uuid)) {
                        selectedProfile = profile;
                        break;
                    }
                }
            }
            if (selectedProfile == null) {
                selectedProfile = profiles.get(0).getAsJsonObject();
            }

            String uuidStr = getJsonString(selectedProfile, "uuid");
            this.profileUuid = uuidStr != null ? UUID.fromString(uuidStr) : null;
            this.profileUsername = getJsonString(selectedProfile, "username");

            LOGGER.info("Using profile: {} ({})", profileUsername, profileUuid);

            // Create game session
            String sessionBody = String.format("{\"uuid\":\"%s\"}", profileUuid.toString());

            HttpRequest sessionRequest = HttpRequest.newBuilder()
                .uri(URI.create(SESSION_SERVICE_URL + "/game-session/new"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + oauthAccessToken)
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(sessionBody))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> sessionResponse = httpClient.send(sessionRequest, HttpResponse.BodyHandlers.ofString());

            if (sessionResponse.statusCode() == 200 || sessionResponse.statusCode() == 201) {
                JsonObject json = JsonParser.parseString(sessionResponse.body()).getAsJsonObject();
                this.sessionToken = getJsonString(json, "sessionToken");
                this.identityToken = getJsonString(json, "identityToken");

                String expiresAt = getJsonString(json, "expiresAt");
                if (expiresAt != null) {
                    try {
                        this.sessionExpiry = Instant.parse(expiresAt);
                        scheduleSessionRefresh();
                    } catch (Exception e) {
                        LOGGER.warn("Failed to parse session expiry: {}", expiresAt);
                    }
                }

                saveCredentials();
                LOGGER.info("Game session created successfully!");
                LOGGER.info("Proxy is now authenticated as a Hytale server");
                return true;
            }

            LOGGER.error("Failed to create game session: HTTP {} - {}", sessionResponse.statusCode(), sessionResponse.body());
            return false;

        } catch (Exception e) {
            LOGGER.error("Error creating game session", e);
            return false;
        }
    }

    private void scheduleSessionRefresh() {
        if (sessionExpiry == null) return;

        long delaySeconds = sessionExpiry.getEpochSecond() - Instant.now().getEpochSecond() - REFRESH_BUFFER_SECONDS;
        if (delaySeconds < 60) delaySeconds = 60;

        LOGGER.info("Session refresh scheduled in {} seconds", delaySeconds);
        refreshScheduler.schedule(this::refreshGameSession, delaySeconds, TimeUnit.SECONDS);
    }

    private void refreshGameSession() {
        LOGGER.info("Refreshing game session...");
        if (createGameSessionFromOAuth()) {
            LOGGER.info("Game session refreshed successfully");
        } else {
            LOGGER.error("Failed to refresh game session!");
        }
    }

    // ==================== Client-Facing Auth (Proxy acts as server) ====================

    /**
     * Result of requesting an auth grant for a client.
     */
    public static class AuthGrantResult {
        public final String authorizationGrant;
        public final String serverIdentityToken;

        public AuthGrantResult(String authorizationGrant, String serverIdentityToken) {
            this.authorizationGrant = authorizationGrant;
            this.serverIdentityToken = serverIdentityToken;
        }
    }

    /**
     * Request an authorization grant for a client connecting to this proxy.
     * This is called when the proxy receives a Connect packet from a client.
     *
     * The proxy acts as a Hytale server and needs to:
     * 1. Validate the client's identity token
     * 2. Request an auth grant from the session service
     * 3. Return the grant along with the proxy's identity token
     *
     * @param clientUuid The client's UUID from the Connect packet
     * @param clientUsername The client's username
     * @param clientIdentityToken The client's identity token
     * @return AuthGrantResult containing the authorization grant and server identity token
     */
    public CompletableFuture<AuthGrantResult> requestAuthGrantForClient(
            @Nonnull UUID clientUuid,
            @Nonnull String clientUsername,
            @Nullable String clientIdentityToken) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                if (sessionToken == null) {
                    LOGGER.error("Cannot request auth grant for client - proxy not authenticated!");
                    return null;
                }

                LOGGER.info("Requesting authorization grant for client {} ({})", clientUsername, clientUuid);

                // Build the request to session service
                // The session service expects either identityToken OR uuid
                JsonObject requestBody = new JsonObject();

                if (clientIdentityToken != null && !clientIdentityToken.isEmpty()) {
                    // Preferred: use the client's identity token
                    requestBody.addProperty("identityToken", clientIdentityToken);
                } else {
                    // Fallback: use UUID
                    requestBody.addProperty("uuid", clientUuid.toString());
                }

                // Add server's certificate fingerprint for token binding
                if (proxyFingerprint != null) {
                    requestBody.addProperty("x509Fingerprint", proxyFingerprint);
                }

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SESSION_SERVICE_URL + "/server-join/auth-grant"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + sessionToken)
                    .header("User-Agent", USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String authGrant = getJsonString(json, "authorizationGrant");
                    String serverIdToken = identityToken; // Use proxy's identity token

                    if (authGrant != null) {
                        LOGGER.info("Successfully obtained authorization grant for client {}", clientUsername);
                        return new AuthGrantResult(authGrant, serverIdToken);
                    }
                }

                LOGGER.error("Failed to get auth grant for client: HTTP {} - {}",
                    response.statusCode(), response.body());
                return null;

            } catch (Exception e) {
                LOGGER.error("Error requesting auth grant for client", e);
                return null;
            }
        });
    }

    /**
     * Exchange a server authorization grant for a server access token.
     * This is called when the proxy receives an AuthToken from a client
     * containing a serverAuthorizationGrant that the client wants us to exchange.
     *
     * @param serverAuthGrant The server authorization grant from the client's AuthToken
     * @return The server access token to send back in ServerAuthToken
     */
    public CompletableFuture<String> exchangeServerAuthGrant(@Nonnull String serverAuthGrant) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (sessionToken == null) {
                    LOGGER.error("Cannot exchange server auth grant - proxy not authenticated!");
                    return null;
                }

                if (proxyFingerprint == null) {
                    LOGGER.error("Cannot exchange server auth grant - no certificate fingerprint!");
                    return null;
                }

                LOGGER.info("Exchanging server authorization grant for server access token...");

                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("authorizationGrant", serverAuthGrant);
                requestBody.addProperty("x509Fingerprint", proxyFingerprint);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SESSION_SERVICE_URL + "/server-join/auth-token"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + sessionToken)
                    .header("User-Agent", USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String accessToken = getJsonString(json, "accessToken");
                    if (accessToken != null) {
                        LOGGER.info("Successfully exchanged server auth grant for access token!");
                        return accessToken;
                    }
                }

                LOGGER.error("Failed to exchange server auth grant: HTTP {} - {}",
                    response.statusCode(), response.body());
                return null;

            } catch (Exception e) {
                LOGGER.error("Error exchanging server authorization grant", e);
                return null;
            }
        });
    }

    // ==================== Auth Grant Exchange (Proxy -> Backend) ====================

    /**
     * Exchange an authorization grant for an access token.
     * The token will be bound to the proxy's certificate.
     *
     * This is the key method - when we receive AuthGrant from backend,
     * WE exchange it (not the client), so the resulting token is bound to OUR cert.
     */
    public CompletableFuture<String> exchangeAuthGrantForToken(@Nonnull String authorizationGrant) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (sessionToken == null) {
                    LOGGER.error("Cannot exchange auth grant - proxy not authenticated!");
                    return null;
                }

                if (proxyFingerprint == null) {
                    LOGGER.error("Cannot exchange auth grant - no certificate fingerprint!");
                    return null;
                }

                LOGGER.info("Exchanging authorization grant for access token (bound to proxy cert)...");

                // Use the session service endpoint for exchanging auth grants
                String jsonBody = String.format(
                    "{\"authorizationGrant\":\"%s\",\"x509Fingerprint\":\"%s\"}",
                    escapeJsonString(authorizationGrant),
                    escapeJsonString(proxyFingerprint)
                );

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SESSION_SERVICE_URL + "/server-join/auth-token"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + sessionToken)
                    .header("User-Agent", USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String accessToken = getJsonString(json, "accessToken");
                    if (accessToken != null) {
                        LOGGER.info("Successfully obtained access token bound to proxy certificate!");
                        return accessToken;
                    }
                }

                LOGGER.error("Failed to exchange auth grant: HTTP {} - {}", response.statusCode(), response.body());
                return null;

            } catch (Exception e) {
                LOGGER.error("Error exchanging authorization grant", e);
                return null;
            }
        });
    }

    /**
     * Request a server authorization grant for mutual authentication.
     *
     * The backend sends its serverIdentityToken in the AuthGrant packet.
     * We need to call the session service to get a serverAuthorizationGrant
     * that we'll include in our AuthToken response.
     *
     * @param serverIdentityToken The server's identity token from AuthGrant
     * @param serverAudience The server's audience (usually the server's session ID)
     * @return The server authorization grant, or null if failed
     */
    public CompletableFuture<String> requestServerAuthGrant(@Nonnull String serverIdentityToken, @Nonnull String serverAudience) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (sessionToken == null) {
                    LOGGER.error("Cannot request server auth grant - proxy not authenticated!");
                    return null;
                }

                LOGGER.info("Requesting server authorization grant for mutual authentication...");

                String jsonBody = String.format(
                    "{\"identityToken\":\"%s\",\"aud\":\"%s\"}",
                    escapeJsonString(serverIdentityToken),
                    escapeJsonString(serverAudience)
                );

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SESSION_SERVICE_URL + "/server-join/auth-grant"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + sessionToken)
                    .header("User-Agent", USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String authGrant = getJsonString(json, "authorizationGrant");
                    if (authGrant != null) {
                        LOGGER.info("Successfully obtained server authorization grant!");
                        return authGrant;
                    }
                }

                LOGGER.error("Failed to get server auth grant: HTTP {} - {}", response.statusCode(), response.body());
                return null;

            } catch (Exception e) {
                LOGGER.error("Error requesting server authorization grant", e);
                return null;
            }
        });
    }

    /**
     * Request a NEW authorization grant for a client.
     *
     * This allows the proxy to get a fresh grant that the client can exchange.
     * The original grant from the backend was consumed by the proxy.
     *
     * @param clientIdentityToken The client's identity token (from Connect packet)
     * @param serverAudience The backend server's audience
     * @return A new authorization grant for the client to exchange
     */
    public CompletableFuture<String> requestNewAuthGrantForClient(@Nonnull String clientIdentityToken, @Nonnull String serverAudience) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (sessionToken == null) {
                    LOGGER.error("Cannot request auth grant for client - proxy not authenticated!");
                    return null;
                }

                LOGGER.info("Requesting new authorization grant for client...");

                String jsonBody = String.format(
                    "{\"identityToken\":\"%s\",\"aud\":\"%s\"}",
                    escapeJsonString(clientIdentityToken),
                    escapeJsonString(serverAudience)
                );

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SESSION_SERVICE_URL + "/server-join/auth-grant"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + sessionToken)
                    .header("User-Agent", USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String authGrant = getJsonString(json, "authorizationGrant");
                    if (authGrant != null) {
                        LOGGER.info("Successfully obtained new authorization grant for client!");
                        return authGrant;
                    }
                }

                LOGGER.error("Failed to get auth grant for client: HTTP {} - {}", response.statusCode(), response.body());
                return null;

            } catch (Exception e) {
                LOGGER.error("Error requesting authorization grant for client", e);
                return null;
            }
        });
    }

    // ==================== Utility Methods ====================

    @Nullable
    private static String getJsonString(JsonObject obj, String key) {
        JsonElement elem = obj.get(key);
        return elem != null && elem.isJsonPrimitive() ? elem.getAsString() : null;
    }

    private static int getJsonInt(JsonObject obj, String key, int defaultValue) {
        JsonElement elem = obj.get(key);
        return elem != null && elem.isJsonPrimitive() ? elem.getAsInt() : defaultValue;
    }

    private static String escapeJsonString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ==================== Getters ====================

    public String getProxyFingerprint() {
        return proxyFingerprint;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public String getIdentityToken() {
        return identityToken;
    }

    public boolean isAuthenticated() {
        return sessionToken != null && identityToken != null;
    }

    public UUID getProfileUuid() {
        return profileUuid;
    }

    public String getProfileUsername() {
        return profileUsername;
    }

    public void shutdown() {
        refreshScheduler.shutdown();
    }

    // ==================== Helper Classes ====================

    public static class DeviceCodeResponse {
        public String deviceCode;
        public String userCode;
        public String verificationUri;
        public String verificationUriComplete;
        public int expiresIn;
        public int interval;
    }
}

