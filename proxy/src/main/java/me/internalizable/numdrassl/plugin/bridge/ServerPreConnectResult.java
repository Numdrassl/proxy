package me.internalizable.numdrassl.plugin.bridge;

import me.internalizable.numdrassl.config.BackendServer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Result of firing a ServerPreConnectEvent.
 *
 * @param allowed whether the connection is allowed
 * @param targetServer the target backend server (may be redirected)
 * @param denyReason the reason for denial if not allowed
 */
public record ServerPreConnectResult(
    boolean allowed,
    @Nullable BackendServer targetServer,
    @Nullable String denyReason
) {
    /**
     * Creates an allowed result with the given target server.
     */
    @Nonnull
    public static ServerPreConnectResult allow(@Nonnull BackendServer targetServer) {
        Objects.requireNonNull(targetServer, "targetServer");
        return new ServerPreConnectResult(true, targetServer, null);
    }

    /**
     * Creates a denied result with the given reason.
     */
    @Nonnull
    public static ServerPreConnectResult deny(@Nonnull String reason) {
        Objects.requireNonNull(reason, "reason");
        return new ServerPreConnectResult(false, null, reason);
    }

    /**
     * Creates a redirected result to a different server.
     */
    @Nonnull
    public static ServerPreConnectResult redirect(@Nonnull BackendServer newTarget) {
        Objects.requireNonNull(newTarget, "newTarget");
        return new ServerPreConnectResult(true, newTarget, null);
    }

    public boolean isAllowed() {
        return allowed;
    }

    @Nullable
    public BackendServer getTargetServer() {
        return targetServer;
    }

    @Nullable
    public String getDenyReason() {
        return denyReason;
    }
}

