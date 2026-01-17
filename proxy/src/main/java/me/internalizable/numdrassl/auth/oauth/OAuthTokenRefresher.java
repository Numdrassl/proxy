package me.internalizable.numdrassl.auth.oauth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.internalizable.numdrassl.auth.credential.CredentialStore;
import me.internalizable.numdrassl.auth.http.HytaleEndpoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

/**
 * Handles OAuth token refresh operations.
 */
public final class OAuthTokenRefresher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthTokenRefresher.class);

    private final HttpClient httpClient;
    private final CredentialStore credentialStore;

    public OAuthTokenRefresher(@Nonnull HttpClient httpClient, @Nonnull CredentialStore credentialStore) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
    }

    /**
     * Refreshes OAuth tokens if needed.
     *
     * @return true if tokens are valid (either still valid or successfully refreshed)
     */
    public boolean refreshIfNeeded() {
        if (!credentialStore.hasRefreshToken()) return false;
        Instant expiry = credentialStore.getOauthTokenExpiry();
        if (expiry != null && expiry.isAfter(Instant.now().plusSeconds(HytaleEndpoints.REFRESH_BUFFER_SECONDS))) {
            return true;
        }
        return performRefresh();
    }

    /**
     * Forces a token refresh regardless of expiry.
     */
    public boolean forceRefresh() {
        if (!credentialStore.hasRefreshToken()) {
            LOGGER.warn("No refresh token available for refresh");
            return false;
        }
        return performRefresh();
    }

    private boolean performRefresh() {
        try {
            LOGGER.info("Refreshing OAuth tokens...");
            String body = "grant_type=refresh_token" +
                "&client_id=" + URLEncoder.encode(HytaleEndpoints.CLIENT_ID, StandardCharsets.UTF_8) +
                "&refresh_token=" + URLEncoder.encode(credentialStore.getOauthRefreshToken(), StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HytaleEndpoints.OAUTH_TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", HytaleEndpoints.USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(HytaleEndpoints.REQUEST_TIMEOUT)
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                var at = json.get("access_token"); if (at != null) credentialStore.setOauthAccessToken(at.getAsString());
                var rt = json.get("refresh_token"); if (rt != null) credentialStore.setOauthRefreshToken(rt.getAsString());
                var exp = json.get("expires_in"); int e = exp != null ? exp.getAsInt() : 3600;
                credentialStore.setOauthTokenExpiry(Instant.now().plusSeconds(e));
                credentialStore.save();
                LOGGER.info("OAuth tokens refreshed successfully");
                return true;
            }
            LOGGER.error("Failed to refresh: HTTP {} - {}", response.statusCode(), response.body());
            return false;
        } catch (Exception e) {
            LOGGER.error("Error refreshing OAuth tokens", e);
            return false;
        }
    }
}
