package me.internalizable.numdrassl.auth.credential;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Manages persistent storage of authentication credentials.
 */
public final class CredentialStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialStore.class);
    private static final Gson GSON = new Gson();

    private final Path storagePath;

    private volatile String oauthAccessToken;
    private volatile String oauthRefreshToken;
    private volatile Instant oauthTokenExpiry;
    private volatile UUID profileUuid;
    private volatile String profileUsername;

    public CredentialStore(@Nonnull String storagePath) {
        Objects.requireNonNull(storagePath, "storagePath");
        this.storagePath = Path.of(storagePath);
    }

    public boolean load() {
        try {
            if (!Files.exists(storagePath)) return false;
            String json = Files.readString(storagePath);
            JsonObject stored = JsonParser.parseString(json).getAsJsonObject();
            if (stored.has("oauth_access_token")) this.oauthAccessToken = stored.get("oauth_access_token").getAsString();
            if (stored.has("oauth_refresh_token")) this.oauthRefreshToken = stored.get("oauth_refresh_token").getAsString();
            if (stored.has("oauth_expiry")) this.oauthTokenExpiry = Instant.ofEpochSecond(stored.get("oauth_expiry").getAsLong());
            if (stored.has("profile_uuid")) this.profileUuid = UUID.fromString(stored.get("profile_uuid").getAsString());
            if (stored.has("profile_username")) this.profileUsername = stored.get("profile_username").getAsString();
            return oauthRefreshToken != null;
        } catch (Exception e) {
            LOGGER.warn("Failed to load credentials: {}", e.getMessage());
            return false;
        }
    }

    public void save() {
        try {
            JsonObject stored = new JsonObject();
            if (oauthAccessToken != null) stored.addProperty("oauth_access_token", oauthAccessToken);
            if (oauthRefreshToken != null) stored.addProperty("oauth_refresh_token", oauthRefreshToken);
            if (oauthTokenExpiry != null) stored.addProperty("oauth_expiry", oauthTokenExpiry.getEpochSecond());
            if (profileUuid != null) stored.addProperty("profile_uuid", profileUuid.toString());
            if (profileUsername != null) stored.addProperty("profile_username", profileUsername);
            Files.createDirectories(storagePath.getParent());
            Files.writeString(storagePath, GSON.toJson(stored));
        } catch (Exception e) {
            LOGGER.error("Failed to save credentials", e);
        }
    }

    @Nullable public String getOauthAccessToken() { return oauthAccessToken; }
    public void setOauthAccessToken(@Nullable String token) { this.oauthAccessToken = token; }
    @Nullable public String getOauthRefreshToken() { return oauthRefreshToken; }
    public void setOauthRefreshToken(@Nullable String token) { this.oauthRefreshToken = token; }
    @Nullable public Instant getOauthTokenExpiry() { return oauthTokenExpiry; }
    public void setOauthTokenExpiry(@Nullable Instant expiry) { this.oauthTokenExpiry = expiry; }
    @Nullable public UUID getProfileUuid() { return profileUuid; }
    public void setProfileUuid(@Nullable UUID uuid) { this.profileUuid = uuid; }
    @Nullable public String getProfileUsername() { return profileUsername; }
    public void setProfileUsername(@Nullable String username) { this.profileUsername = username; }
    public boolean hasRefreshToken() { return oauthRefreshToken != null; }
}

