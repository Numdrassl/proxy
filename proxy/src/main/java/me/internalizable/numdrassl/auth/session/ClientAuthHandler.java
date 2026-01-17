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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles client authentication when the proxy acts as a server.
 */
public final class ClientAuthHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientAuthHandler.class);
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient;
    private final GameSessionManager sessionManager;
    private final String proxyFingerprint;

    public ClientAuthHandler(@Nonnull HttpClient httpClient, @Nonnull GameSessionManager sessionManager,
                             @Nullable String proxyFingerprint) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.sessionManager = Objects.requireNonNull(sessionManager);
        this.proxyFingerprint = proxyFingerprint;
    }

    @Nonnull
    public CompletableFuture<AuthGrantResult> requestAuthGrant(@Nonnull UUID clientUuid, @Nonnull String clientUsername,
                                                               @Nullable String clientIdentityToken) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!sessionManager.isAuthenticated()) { LOGGER.error("Proxy not authenticated!"); return null; }
                LOGGER.info("Requesting auth grant for {} ({})", clientUsername, clientUuid);

                JsonObject body = new JsonObject();
                if (clientIdentityToken != null && !clientIdentityToken.isEmpty()) body.addProperty("identityToken", clientIdentityToken);
                else body.addProperty("uuid", clientUuid.toString());
                if (proxyFingerprint != null) body.addProperty("x509Fingerprint", proxyFingerprint);

                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(HytaleEndpoints.sessionServiceEndpoint("/server-join/auth-grant")))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .header("Authorization", "Bearer " + sessionManager.getSessionToken())
                    .header("User-Agent", HytaleEndpoints.USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .timeout(HytaleEndpoints.REQUEST_TIMEOUT).build();

                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
                    var ag = json.get("authorizationGrant");
                    if (ag != null) {
                        LOGGER.info("Got auth grant for {}", clientUsername);
                        return new AuthGrantResult(ag.getAsString(), sessionManager.getIdentityToken());
                    }
                }
                LOGGER.error("Failed to get auth grant: {}", res.body()); return null;
            } catch (Exception e) { LOGGER.error("Error requesting auth grant", e); return null; }
        });
    }

    @Nonnull
    public CompletableFuture<String> exchangeServerAuthGrant(@Nonnull String serverAuthGrant) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!sessionManager.isAuthenticated() || proxyFingerprint == null) return null;
                JsonObject body = new JsonObject();
                body.addProperty("authorizationGrant", serverAuthGrant);
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
                    return at != null ? at.getAsString() : null;
                }
                return null;
            } catch (Exception e) { LOGGER.error("Error exchanging server auth grant", e); return null; }
        });
    }

    public record AuthGrantResult(@Nonnull String authorizationGrant, @Nullable String serverIdentityToken) {
        public AuthGrantResult { Objects.requireNonNull(authorizationGrant); }
    }
}
