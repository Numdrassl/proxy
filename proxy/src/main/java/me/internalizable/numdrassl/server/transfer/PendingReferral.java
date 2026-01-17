package me.internalizable.numdrassl.server.transfer;

import me.internalizable.numdrassl.config.BackendServer;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a pending server transfer referral for a player.
 *
 * <p>When a player is transferred between servers, we create a referral that tracks
 * where they should be routed when they reconnect. Referrals expire after a timeout.</p>
 *
 * @param playerUuid the player's UUID
 * @param targetBackend the backend server to transfer to
 * @param createdAt timestamp when the referral was created (millis)
 */
public record PendingReferral(
    @Nonnull UUID playerUuid,
    @Nonnull BackendServer targetBackend,
    long createdAt
) {
    public PendingReferral {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(targetBackend, "targetBackend");
    }

    /**
     * Creates a new referral with the current timestamp.
     */
    @Nonnull
    public static PendingReferral create(@Nonnull UUID playerUuid, @Nonnull BackendServer targetBackend) {
        return new PendingReferral(playerUuid, targetBackend, System.currentTimeMillis());
    }

    /**
     * Checks if this referral has expired.
     *
     * @param expiryMillis the expiry duration in milliseconds
     * @return true if expired
     */
    public boolean isExpired(long expiryMillis) {
        return System.currentTimeMillis() - createdAt > expiryMillis;
    }

    /**
     * Gets the age of this referral in milliseconds.
     */
    public long getAgeMillis() {
        return System.currentTimeMillis() - createdAt;
    }
}

