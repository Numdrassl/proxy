package me.internalizable.numdrassl.auth.session;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.internalizable.numdrassl.auth.http.HytaleEndpoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Handles authentication when the proxy connects to backend servers.
 */
public final class BackendAuthHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackendAuthHandler.class);
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient;
    private final GameSessionManager sessionManager;
    private final String proxyFingerprint;

    public BackendAuthHandler(@Nonnull HttpClient httpClient, @Nonnull GameSessionManager sessionManager,
                              @Nullable String proxyFingerprint) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.sessionManager = Objects.requireNonNull(sessionManager);
        this.proxyFingerprint = proxyFingerprint;
    }

    @Nonnull
    public CompletableFuture<String> exchangeAuthGrant(@Nonnull String authorizationGrant) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!sessionManager.isAuthenticated() || proxyFingerprint == null) return null;
                LOGGER.info("Exchanging auth grant for access token...");

                JsonObject body = new JsonObject();
                body.addProperty("authorizationGrant", authorizationGrant);
                body.addProperty("x509Fingerprint", proxyFingerprint);

                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(HytaleEndpoints.sessionServiceEndpoint("/server-join/auth-token")))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .header("Authorization", "Bearer " + sessionManager.getSessionToken())
                    .header("User-Agent", HytaleEndpoints.USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .timeout(HytaleEndpoints.REQUEST_TIMEOUT).build();

                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200) {
                    var at = JsonParser.parseString(res.body()).getAsJsonObject().get("accessToken");
                    if (at != null) { LOGGER.info("Got access token!"); return at.getAsString(); }
                }
                LOGGER.error("Failed to exchange auth grant: {}", res.body()); return null;
            } catch (Exception e) { LOGGER.error("Error exchanging auth grant", e); return null; }
        });
    }

    @Nonnull
    public CompletableFuture<String> requestServerAuthGrant(@Nonnull String serverIdentityToken, @Nonnull String serverAudience) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!sessionManager.isAuthenticated()) return null;
                JsonObject body = new JsonObject();
                body.addProperty("identityToken", serverIdentityToken);
                body.addProperty("aud", serverAudience);

                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(HytaleEndpoints.sessionServiceEndpoint("/server-join/auth-grant")))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .header("Authorization", "Bearer " + sessionManager.getSessionToken())
                    .header("User-Agent", HytaleEndpoints.USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .timeout(HytaleEndpoints.REQUEST_TIMEOUT).build();

                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200) {
                    var ag = JsonParser.parseString(res.body()).getAsJsonObject().get("authorizationGrant");
                    return ag != null ? ag.getAsString() : null;
                }
                return null;
            } catch (Exception e) { LOGGER.error("Error requesting server auth grant", e); return null; }
        });
    }

    @Nonnull
    public CompletableFuture<String> requestClientAuthGrant(@Nonnull String clientIdentityToken, @Nonnull String serverAudience) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!sessionManager.isAuthenticated()) return null;
                JsonObject body = new JsonObject();
                body.addProperty("identityToken", clientIdentityToken);
                body.addProperty("aud", serverAudience);

                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(HytaleEndpoints.sessionServiceEndpoint("/server-join/auth-grant")))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .header("Authorization", "Bearer " + sessionManager.getSessionToken())
                    .header("User-Agent", HytaleEndpoints.USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .timeout(HytaleEndpoints.REQUEST_TIMEOUT).build();

                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200) {
                    var ag = JsonParser.parseString(res.body()).getAsJsonObject().get("authorizationGrant");
                    return ag != null ? ag.getAsString() : null;
                }
                return null;
            } catch (Exception e) { LOGGER.error("Error requesting client auth grant", e); return null; }
        });
    }
}
