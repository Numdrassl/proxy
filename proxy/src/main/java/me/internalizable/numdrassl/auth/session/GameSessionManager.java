package me.internalizable.numdrassl.auth.session;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.internalizable.numdrassl.auth.credential.CredentialStore;
import me.internalizable.numdrassl.auth.http.HytaleEndpoints;
import me.internalizable.numdrassl.auth.oauth.OAuthTokenRefresher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages game sessions with Hytale's session service.
 */
public final class GameSessionManager implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameSessionManager.class);

    private final HttpClient httpClient;
    private final CredentialStore credentialStore;
    private final OAuthTokenRefresher tokenRefresher;
    private final ScheduledExecutorService refreshScheduler;

    private volatile String sessionToken;
    private volatile String identityToken;
    private volatile Instant sessionExpiry;

    public GameSessionManager(@Nonnull HttpClient httpClient, @Nonnull CredentialStore credentialStore,
                              @Nonnull OAuthTokenRefresher tokenRefresher) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.credentialStore = Objects.requireNonNull(credentialStore);
        this.tokenRefresher = Objects.requireNonNull(tokenRefresher);
        this.refreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GameSession-Refresh"); t.setDaemon(true); return t;
        });
    }

    public boolean createSession() {
        if (!tokenRefresher.refreshIfNeeded()) return false;
        try {
            JsonArray profiles = fetchProfiles();
            if (profiles == null || profiles.isEmpty()) return false;
            JsonObject profile = selectProfile(profiles);
            updateProfile(profile);
            return createGameSession();
        } catch (Exception e) { LOGGER.error("Error creating session", e); return false; }
    }

    private JsonArray fetchProfiles() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(HytaleEndpoints.accountDataEndpoint("/my-account/get-profiles")))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + credentialStore.getOauthAccessToken())
            .header("User-Agent", HytaleEndpoints.USER_AGENT)
            .GET().timeout(HytaleEndpoints.REQUEST_TIMEOUT).build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) { LOGGER.error("Failed to get profiles: {}", res.body()); return null; }
        JsonObject data = JsonParser.parseString(res.body()).getAsJsonObject();
        return data.has("profiles") ? data.getAsJsonArray("profiles") : null;
    }

    private JsonObject selectProfile(JsonArray profiles) {
        UUID stored = credentialStore.getProfileUuid();
        if (stored != null) {
            for (int i = 0; i < profiles.size(); i++) {
                JsonObject p = profiles.get(i).getAsJsonObject();
                var u = p.get("uuid"); if (u != null && stored.equals(UUID.fromString(u.getAsString()))) return p;
            }
        }
        return profiles.get(0).getAsJsonObject();
    }

    private void updateProfile(JsonObject profile) {
        var u = profile.get("uuid"); if (u != null) credentialStore.setProfileUuid(UUID.fromString(u.getAsString()));
        var n = profile.get("username"); if (n != null) credentialStore.setProfileUsername(n.getAsString());
        LOGGER.info("Using profile: {} ({})", credentialStore.getProfileUsername(), credentialStore.getProfileUuid());
    }

    private boolean createGameSession() throws Exception {
        String body = String.format("{\"uuid\":\"%s\"}", credentialStore.getProfileUuid());
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(HytaleEndpoints.sessionServiceEndpoint("/game-session/new")))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + credentialStore.getOauthAccessToken())
            .header("User-Agent", HytaleEndpoints.USER_AGENT)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(HytaleEndpoints.REQUEST_TIMEOUT).build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200 || res.statusCode() == 201) {
            JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
            var st = json.get("sessionToken"); if (st != null) this.sessionToken = st.getAsString();
            var it = json.get("identityToken"); if (it != null) this.identityToken = it.getAsString();
            var exp = json.get("expiresAt"); if (exp != null) {
                try { this.sessionExpiry = Instant.parse(exp.getAsString()); scheduleRefresh(); } catch (Exception ignored) {}
            }
            credentialStore.save();
            LOGGER.info("Game session created successfully!");
            return true;
        }
        LOGGER.error("Failed to create session: {}", res.body()); return false;
    }

    private void scheduleRefresh() {
        if (sessionExpiry == null) return;
        long delay = sessionExpiry.getEpochSecond() - Instant.now().getEpochSecond() - HytaleEndpoints.REFRESH_BUFFER_SECONDS;
        if (delay < 60) delay = 60;
        refreshScheduler.schedule(() -> { if (!createSession()) LOGGER.error("Failed to refresh session!"); }, delay, TimeUnit.SECONDS);
    }

    @Nullable public String getSessionToken() { return sessionToken; }
    @Nullable public String getIdentityToken() { return identityToken; }
    public boolean isAuthenticated() { return sessionToken != null && identityToken != null; }

    @Override public void close() {
        refreshScheduler.shutdown();
        try { if (!refreshScheduler.awaitTermination(5, TimeUnit.SECONDS)) refreshScheduler.shutdownNow(); }
        catch (InterruptedException e) { refreshScheduler.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}
