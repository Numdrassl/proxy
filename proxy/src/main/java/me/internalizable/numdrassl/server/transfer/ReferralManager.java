package me.internalizable.numdrassl.server.transfer;

import me.internalizable.numdrassl.config.BackendServer;
import me.internalizable.numdrassl.server.ProxyCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages pending server referrals for player transfers.
 *
 * <p>When a player is transferred between servers, a {@code ClientReferral} packet
 * tells the client to reconnect. This manager tracks where players should be
 * routed when they reconnect with referral data.</p>
 *
 * <p>Referrals expire after a configurable timeout (default 30 seconds).</p>
 */
public final class ReferralManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReferralManager.class);

    private static final Duration DEFAULT_EXPIRY = Duration.ofSeconds(30);
    private static final Duration CLEANUP_INTERVAL = Duration.ofSeconds(10);
    private static final String REFERRAL_PREFIX = "NUMDRASSL:";

    private final ProxyCore proxyCore;
    private final Map<UUID, PendingReferral> pendingReferrals = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;
    private final long expiryMillis;

    // ==================== Construction ====================

    public ReferralManager(@Nonnull ProxyCore proxyCore) {
        this(proxyCore, DEFAULT_EXPIRY);
    }

    public ReferralManager(@Nonnull ProxyCore proxyCore, @Nonnull Duration expiry) {
        this.proxyCore = Objects.requireNonNull(proxyCore, "proxyCore");
        this.expiryMillis = Objects.requireNonNull(expiry, "expiry").toMillis();
        this.cleanupExecutor = createCleanupExecutor();

        scheduleCleanup();
    }

    private ScheduledExecutorService createCleanupExecutor() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "Referral-Cleanup");
            thread.setDaemon(true);
            return thread;
        });
    }

    private void scheduleCleanup() {
        long intervalSeconds = CLEANUP_INTERVAL.toSeconds();
        cleanupExecutor.scheduleAtFixedRate(
            this::cleanupExpired,
            intervalSeconds,
            intervalSeconds,
            TimeUnit.SECONDS
        );
    }

    // ==================== Referral Creation ====================

    /**
     * Creates referral data for a player transfer.
     *
     * @param playerUuid the player's UUID
     * @param targetBackend the target backend server
     * @return the referral data bytes for the ClientReferral packet
     */
    @Nonnull
    public byte[] createReferral(@Nonnull UUID playerUuid, @Nonnull BackendServer targetBackend) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(targetBackend, "targetBackend");

        PendingReferral referral = PendingReferral.create(playerUuid, targetBackend);
        pendingReferrals.put(playerUuid, referral);

        LOGGER.info("Created referral for {} to backend {}", playerUuid, targetBackend.getName());

        return encodeReferralData(targetBackend.getName());
    }

    private byte[] encodeReferralData(String backendName) {
        return (REFERRAL_PREFIX + backendName).getBytes(StandardCharsets.UTF_8);
    }

    // ==================== Referral Consumption ====================

    /**
     * Consumes a pending referral and returns the target backend.
     *
     * <p>The referral is removed after consumption. Returns empty if no valid
     * referral exists or if it has expired.</p>
     *
     * @param playerUuid the player's UUID
     * @param referralData the referral data from Connect packet (optional)
     * @return the target backend, or empty if no valid referral
     */
    @Nonnull
    public Optional<BackendServer> consumeReferral(@Nonnull UUID playerUuid, @Nullable byte[] referralData) {
        Objects.requireNonNull(playerUuid, "playerUuid");

        PendingReferral pending = pendingReferrals.remove(playerUuid);
        if (pending == null) {
            return Optional.empty();
        }

        if (pending.isExpired(expiryMillis)) {
            LOGGER.warn("Referral for {} has expired", playerUuid);
            return Optional.empty();
        }

        validateReferralData(playerUuid, referralData, pending);

        LOGGER.info("Consumed referral for {} -> backend {}", playerUuid, pending.targetBackend().getName());
        return Optional.of(pending.targetBackend());
    }

    private void validateReferralData(UUID playerUuid, byte[] referralData, PendingReferral pending) {
        if (referralData == null || referralData.length == 0) {
            return;
        }

        String data = new String(referralData, StandardCharsets.UTF_8);
        if (!data.startsWith(REFERRAL_PREFIX)) {
            return;
        }

        String backendName = data.substring(REFERRAL_PREFIX.length());
        if (!backendName.equals(pending.targetBackend().getName())) {
            LOGGER.warn("Referral data mismatch for {}: expected {}, got {}",
                playerUuid, pending.targetBackend().getName(), backendName);
        }
    }

    // ==================== Query ====================

    /**
     * Checks if a player has a valid pending referral.
     *
     * @param playerUuid the player's UUID
     * @return true if a non-expired referral exists
     */
    public boolean hasPendingReferral(@Nonnull UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");

        PendingReferral pending = pendingReferrals.get(playerUuid);
        return pending != null && !pending.isExpired(expiryMillis);
    }

    /**
     * Cancels a pending referral.
     *
     * @param playerUuid the player's UUID
     */
    public void cancelReferral(@Nonnull UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");

        if (pendingReferrals.remove(playerUuid) != null) {
            LOGGER.debug("Cancelled referral for {}", playerUuid);
        }
    }

    /**
     * Returns the number of pending referrals.
     */
    public int getPendingCount() {
        return pendingReferrals.size();
    }

    // ==================== Lifecycle ====================

    private void cleanupExpired() {
        pendingReferrals.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired(expiryMillis)) {
                LOGGER.debug("Expired referral for {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Shuts down the referral manager.
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        pendingReferrals.clear();
        LOGGER.debug("ReferralManager shut down");
    }
}

