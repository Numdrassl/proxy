package me.internalizable.numdrassl.auth.oauth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.internalizable.numdrassl.auth.credential.CredentialStore;
import me.internalizable.numdrassl.auth.http.HytaleEndpoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Handles OAuth device code authentication flow.
 */
public final class OAuthDeviceFlow {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthDeviceFlow.class);

    private final HttpClient httpClient;
    private final CredentialStore credentialStore;

    public OAuthDeviceFlow(@Nonnull HttpClient httpClient, @Nonnull CredentialStore credentialStore) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
    }

    @Nonnull
    public CompletableFuture<DeviceCodeResponse> startFlow() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info("Starting OAuth device code flow...");
                String scope = String.join(" ", HytaleEndpoints.SCOPES);
                String body = "client_id=" + URLEncoder.encode(HytaleEndpoints.CLIENT_ID, StandardCharsets.UTF_8) +
                              "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(HytaleEndpoints.DEVICE_AUTH_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("User-Agent", HytaleEndpoints.USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(HytaleEndpoints.REQUEST_TIMEOUT)
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    DeviceCodeResponse dcr = new DeviceCodeResponse(
                        getStr(json, "device_code"), getStr(json, "user_code"),
                        getStr(json, "verification_uri"), getStr(json, "verification_uri_complete"),
                        getInt(json, "expires_in", 600), getInt(json, "interval", 5));
                    LOGGER.info("Device code obtained. User code: {}", dcr.userCode());
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

    @Nonnull
    public CompletableFuture<Boolean> pollForCompletion(@Nonnull String deviceCode, int interval) {
        Objects.requireNonNull(deviceCode, "deviceCode");
        final int[] pollInterval = {Math.max(interval, 5)};
        return CompletableFuture.supplyAsync(() -> {
            try {
                while (true) {
                    Thread.sleep(pollInterval[0] * 1000L);
                    String body = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:device_code", StandardCharsets.UTF_8) +
                                  "&client_id=" + URLEncoder.encode(HytaleEndpoints.CLIENT_ID, StandardCharsets.UTF_8) +
                                  "&device_code=" + URLEncoder.encode(deviceCode, StandardCharsets.UTF_8);

                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(HytaleEndpoints.OAUTH_TOKEN_URL))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("User-Agent", HytaleEndpoints.USER_AGENT)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(HytaleEndpoints.REQUEST_TIMEOUT)
                        .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                    if (response.statusCode() == 200) {
                        credentialStore.setOauthAccessToken(getStr(json, "access_token"));
                        credentialStore.setOauthRefreshToken(getStr(json, "refresh_token"));
                        credentialStore.setOauthTokenExpiry(Instant.now().plusSeconds(getInt(json, "expires_in", 3600)));
                        credentialStore.save();
                        LOGGER.info("OAuth authentication successful!");
                        return true;
                    }

                    String error = getStr(json, "error");
                    if ("authorization_pending".equals(error)) continue;
                    if ("slow_down".equals(error)) { pollInterval[0] += 5; continue; }
                    if ("expired_token".equals(error) || "access_denied".equals(error)) return false;
                    LOGGER.error("OAuth error: {}", error);
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

    @Nullable private static String getStr(JsonObject obj, String key) {
        var e = obj.get(key); return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }
    private static int getInt(JsonObject obj, String key, int def) {
        var e = obj.get(key); return e != null && e.isJsonPrimitive() ? e.getAsInt() : def;
    }

    public record DeviceCodeResponse(String deviceCode, String userCode, String verificationUri,
                                     String verificationUriComplete, int expiresIn, int interval) {}
}
