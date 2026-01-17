package me.internalizable.numdrassl.auth.http;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.Objects;

/**
 * Constants for Hytale API endpoints.
 */
public final class HytaleEndpoints {

    private HytaleEndpoints() {}

    public static final String OAUTH_TOKEN_URL = "https://oauth.accounts.hytale.com/oauth2/token";
    public static final String DEVICE_AUTH_URL = "https://oauth.accounts.hytale.com/oauth2/device/auth";
    public static final String SESSION_SERVICE_URL = "https://sessions.hytale.com";
    public static final String ACCOUNT_DATA_URL = "https://account-data.hytale.com";

    public static final String CLIENT_ID = "hytale-server";
    public static final String[] SCOPES = {"openid", "offline", "auth:server"};
    public static final String USER_AGENT = "NumdrasslProxy/1.0";

    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    public static final int REFRESH_BUFFER_SECONDS = 300;

    @Nonnull
    public static String sessionServiceEndpoint(@Nonnull String path) {
        Objects.requireNonNull(path, "path");
        return SESSION_SERVICE_URL + path;
    }

    @Nonnull
    public static String accountDataEndpoint(@Nonnull String path) {
        Objects.requireNonNull(path, "path");
        return ACCOUNT_DATA_URL + path;
    }
}

