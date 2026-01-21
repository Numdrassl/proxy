package me.internalizable.numdrassl.cluster.handler;

import com.hypixel.hytale.protocol.HostAddress;
import com.hypixel.hytale.protocol.packets.auth.ClientReferral;
import me.internalizable.numdrassl.api.cluster.ProxyInfo;
import me.internalizable.numdrassl.api.messaging.channel.Channels;
import me.internalizable.numdrassl.api.messaging.MessagingService;
import me.internalizable.numdrassl.api.messaging.Subscription;
import me.internalizable.numdrassl.api.messaging.message.TransferMessage;
import me.internalizable.numdrassl.api.player.TransferResult;
import me.internalizable.numdrassl.cluster.ProxyRegistry;
import me.internalizable.numdrassl.session.ProxySession;
import me.internalizable.numdrassl.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Handles player transfer coordination between proxies.
 *
 * <p>For cross-proxy transfers, this handler:</p>
 * <ol>
 *   <li>Looks up the target proxy's address from {@link ProxyRegistry}</li>
 *   <li>Publishes a transfer message to notify the target proxy</li>
 *   <li>Sends a {@code ClientReferral} packet to the player</li>
 * </ol>
 *
 * <p>When a player arrives at this proxy with a pending transfer, they
 * are automatically routed to the specified backend server.</p>
 */
public final class TransferHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransferHandler.class);
    private static final long TRANSFER_TOKEN_EXPIRY_MS = 30_000; // 30 seconds
    private static final int MAX_PORT = 32767;

    private final MessagingService messagingService;
    private final SessionManager sessionManager;
    private final String localProxyId;

    // Set by ClusterManager after initialization
    private ProxyRegistry proxyRegistry;

    /** Pending incoming transfers (playerUUID -> TransferInfo) */
    private final Map<UUID, PendingTransfer> pendingTransfers = new ConcurrentHashMap<>();

    private Subscription subscription;

    public TransferHandler(
            @Nonnull MessagingService messagingService,
            @Nonnull SessionManager sessionManager,
            @Nonnull String localProxyId) {
        this.messagingService = Objects.requireNonNull(messagingService);
        this.sessionManager = Objects.requireNonNull(sessionManager);
        this.localProxyId = Objects.requireNonNull(localProxyId);
    }

    /**
     * Set the proxy registry (called by ClusterManager after all components are created).
     */
    public void setProxyRegistry(@Nonnull ProxyRegistry proxyRegistry) {
        this.proxyRegistry = Objects.requireNonNull(proxyRegistry);
    }

    // ==================== Lifecycle ====================

    public void start() {
        subscription = messagingService.subscribe(
                Channels.TRANSFER,
                TransferMessage.class,
                (channel, message) -> handleIncomingTransfer(message)
        );
        LOGGER.info("Transfer handler started");
    }

    public void stop() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
        pendingTransfers.clear();
        LOGGER.info("Transfer handler stopped");
    }

    // ==================== Outgoing Transfers ====================

    /**
     * Transfer a player to another proxy.
     *
     * <p>This method:</p>
     * <ol>
     *   <li>Looks up the target proxy's address</li>
     *   <li>Publishes a transfer notification</li>
     *   <li>Sends ClientReferral to the player</li>
     * </ol>
     *
     * @param session the player's current session
     * @param targetProxyId the target proxy ID
     * @param targetServer the backend server to connect to on the target proxy
     * @return the transfer result
     */
    @Nonnull
    public CompletableFuture<TransferResult> transferToProxy(
            @Nonnull ProxySession session,
            @Nonnull String targetProxyId,
            @Nonnull String targetServer) {

        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(targetProxyId, "targetProxyId");
        Objects.requireNonNull(targetServer, "targetServer");

        // Can't transfer to ourselves - use local transfer instead
        if (localProxyId.equals(targetProxyId)) {
            return CompletableFuture.completedFuture(
                    TransferResult.failure("Use local transfer for same-proxy server changes"));
        }

        // Look up target proxy
        if (proxyRegistry == null) {
            return CompletableFuture.completedFuture(
                    TransferResult.failure("Cluster not initialized"));
        }

        Optional<ProxyInfo> targetProxy = proxyRegistry.getProxy(targetProxyId);
        if (targetProxy.isEmpty()) {
            return CompletableFuture.completedFuture(
                    TransferResult.failure("Target proxy not found: " + targetProxyId));
        }

        ProxyInfo proxy = targetProxy.get();
        InetSocketAddress targetAddress = proxy.address();

        if (targetAddress == null) {
            return CompletableFuture.completedFuture(
                    TransferResult.failure("Target proxy has no public address"));
        }

        // Validate port
        if (targetAddress.getPort() > MAX_PORT) {
            return CompletableFuture.completedFuture(
                    TransferResult.failure("Target proxy port exceeds maximum: " + targetAddress.getPort()));
        }

        UUID playerUuid = session.getPlayerUuid();
        String playerName = session.getPlayerName();

        if (playerUuid == null || playerName == null) {
            return CompletableFuture.completedFuture(
                    TransferResult.failure("Player not authenticated"));
        }

        // Generate transfer token for security
        String transferToken = UUID.randomUUID().toString();

        // Publish transfer notification first
        TransferMessage message = new TransferMessage(
                localProxyId,
                Instant.now(),
                playerUuid,
                playerName,
                targetProxyId,
                targetServer,
                transferToken
        );

        return messagingService.publish(Channels.TRANSFER, message)
                .thenApply(v -> {
                    // Now send the player to the target proxy
                    sendToProxy(session, targetAddress, transferToken);
                    LOGGER.info("Session {}: Initiated cross-proxy transfer for {} to {} (server: {})",
                            session.getSessionId(), playerName, targetProxyId, targetServer);
                    return TransferResult.success("Transfer to " + targetProxyId + " initiated");
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to publish transfer message", ex);
                    return TransferResult.failure("Failed to coordinate transfer: " + ex.getMessage());
                });
    }

    private void sendToProxy(ProxySession session, InetSocketAddress targetAddress, String transferToken) {
        // Create referral with transfer token as data
        byte[] referralData = transferToken.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        HostAddress hostAddress = new HostAddress(
                targetAddress.getHostString(),
                (short) targetAddress.getPort()
        );

        ClientReferral referral = new ClientReferral(hostAddress, referralData);

        LOGGER.info("Session {}: Sending ClientReferral to {}:{}",
                session.getSessionId(), targetAddress.getHostString(), targetAddress.getPort());

        session.sendToClient(referral);
    }

    // ==================== Incoming Transfers ====================

    /**
     * Check if a player has a pending transfer to this proxy.
     */
    public boolean hasPendingTransfer(@Nonnull UUID playerUuid) {
        PendingTransfer pending = pendingTransfers.get(playerUuid);
        if (pending == null) {
            return false;
        }
        if (isExpired(pending)) {
            pendingTransfers.remove(playerUuid);
            return false;
        }
        return true;
    }

    /**
     * Get and consume a pending transfer for a player.
     *
     * @param playerUuid the player's UUID
     * @return the pending transfer info, or null if none/expired
     */
    @Nullable
    public PendingTransfer consumePendingTransfer(@Nonnull UUID playerUuid) {
        PendingTransfer pending = pendingTransfers.remove(playerUuid);
        if (pending == null || isExpired(pending)) {
            return null;
        }
        return pending;
    }

    /**
     * Validate a transfer token for a player.
     *
     * @param playerUuid the player's UUID
     * @param token the token from the ClientReferral data
     * @return the pending transfer if valid, null otherwise
     */
    @Nullable
    public PendingTransfer validateTransfer(@Nonnull UUID playerUuid, @Nullable String token) {
        PendingTransfer pending = pendingTransfers.get(playerUuid);
        if (pending == null || isExpired(pending)) {
            return null;
        }
        // Validate token if present
        if (pending.transferToken() != null && !pending.transferToken().equals(token)) {
            LOGGER.warn("Transfer token mismatch for {}", playerUuid);
            return null;
        }
        return pendingTransfers.remove(playerUuid);
    }

    private void handleIncomingTransfer(TransferMessage message) {
        // Only handle transfers destined for this proxy
        if (!localProxyId.equals(message.targetProxyId())) {
            return;
        }

        LOGGER.info("Expecting incoming transfer: {} from proxy {}, target server: {}",
                message.playerName(), message.sourceProxyId(), message.targetServer());

        // Store as pending
        pendingTransfers.put(message.playerUuid(), new PendingTransfer(
                message.playerUuid(),
                message.playerName(),
                message.sourceProxyId(),
                message.targetServer(),
                message.transferToken(),
                System.currentTimeMillis()
        ));

        // Schedule cleanup
        CompletableFuture.delayedExecutor(TRANSFER_TOKEN_EXPIRY_MS, TimeUnit.MILLISECONDS)
                .execute(() -> {
                    PendingTransfer removed = pendingTransfers.remove(message.playerUuid());
                    if (removed != null && !isExpired(removed)) {
                        LOGGER.debug("Expired pending transfer for {} (never arrived)",
                                message.playerName());
                    }
                });
    }

    private boolean isExpired(PendingTransfer transfer) {
        return System.currentTimeMillis() - transfer.timestamp() > TRANSFER_TOKEN_EXPIRY_MS;
    }

    // ==================== Types ====================

    /**
     * Information about a pending incoming transfer.
     */
    public record PendingTransfer(
            @Nonnull UUID playerUuid,
            @Nonnull String playerName,
            @Nonnull String sourceProxyId,
            @Nonnull String targetServer,
            @Nullable String transferToken,
            long timestamp
    ) {}
}
