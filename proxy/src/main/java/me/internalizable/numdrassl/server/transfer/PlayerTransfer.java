package me.internalizable.numdrassl.server.transfer;

import com.hypixel.hytale.protocol.HostAddress;
import com.hypixel.hytale.protocol.packets.auth.ClientReferral;
import me.internalizable.numdrassl.api.player.TransferResult;
import me.internalizable.numdrassl.config.BackendServer;
import me.internalizable.numdrassl.server.ProxyCore;
import me.internalizable.numdrassl.session.ProxySession;
import me.internalizable.numdrassl.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Handles player transfers between backend servers.
 *
 * <p>Uses {@link ClientReferral} packets to instruct the client to disconnect
 * and reconnect to the proxy. When they reconnect with referral data,
 * the {@link ReferralManager} routes them to the target backend.</p>
 */
public final class PlayerTransfer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerTransfer.class);
    private static final int MAX_PORT = 32767;

    private final ProxyCore proxyCore;

    public PlayerTransfer(@Nonnull ProxyCore proxyCore) {
        this.proxyCore = Objects.requireNonNull(proxyCore, "proxyCore");
    }

    // ==================== Transfer Operations ====================

    /**
     * Transfers a player to a different backend server.
     *
     * @param session the player's session
     * @param targetBackend the target backend server
     * @return a future completing with the transfer result
     */
    @Nonnull
    public CompletableFuture<TransferResult> transfer(
            @Nonnull ProxySession session,
            @Nonnull BackendServer targetBackend) {

        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(targetBackend, "targetBackend");

        return CompletableFuture.completedFuture(executeTransfer(session, targetBackend));
    }

    /**
     * Transfers a player to a backend server by name.
     *
     * @param session the player's session
     * @param backendName the name of the target backend
     * @return a future completing with the transfer result
     */
    @Nonnull
    public CompletableFuture<TransferResult> transfer(
            @Nonnull ProxySession session,
            @Nonnull String backendName) {

        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(backendName, "backendName");

        BackendServer backend = proxyCore.getConfig().getBackendByName(backendName);
        if (backend == null) {
            return CompletableFuture.completedFuture(
                TransferResult.failure("Unknown backend server: " + backendName)
            );
        }

        return transfer(session, backend);
    }

    // ==================== Internal ====================

    private TransferResult executeTransfer(ProxySession session, BackendServer targetBackend) {
        // Validate session state
        Optional<TransferResult> validationError = validateTransfer(session, targetBackend);
        if (validationError.isPresent()) {
            return validationError.get();
        }

        // Resolve proxy address
        HostAddress proxyAddress = resolveProxyAddress(session);
        if (proxyAddress == null) {
            return TransferResult.failure("Port exceeds maximum value for player transfers");
        }

        // Create and send referral
        logTransferStart(session, targetBackend);
        byte[] referralData = createReferralData(session, targetBackend);
        sendClientReferral(session, proxyAddress, referralData);

        return TransferResult.success("Transfer initiated");
    }

    private Optional<TransferResult> validateTransfer(ProxySession session, BackendServer targetBackend) {
        if (session.getState() != SessionState.CONNECTED) {
            return Optional.of(TransferResult.failure("Player not connected"));
        }

        if (session.getPlayerUuid() == null) {
            return Optional.of(TransferResult.failure("Player UUID not known"));
        }

        BackendServer current = session.getCurrentBackend();
        if (current != null && current.getName().equalsIgnoreCase(targetBackend.getName())) {
            return Optional.of(TransferResult.failure("Already connected to this server"));
        }

        return Optional.empty();
    }

    private HostAddress resolveProxyAddress(ProxySession session) {
        String host = resolveProxyHost();
        int port = resolveProxyPort();

        if (port > MAX_PORT) {
            LOGGER.error("Session {}: Port {} exceeds maximum ({}) for ClientReferral",
                session.getSessionId(), port, MAX_PORT);
            return null;
        }

        return new HostAddress(host, (short) port);
    }

    private String resolveProxyHost() {
        String publicAddr = proxyCore.getConfig().getPublicAddress();
        if (isValidHost(publicAddr)) {
            return publicAddr;
        }

        String bindAddr = proxyCore.getConfig().getBindAddress();
        if (isValidHost(bindAddr)) {
            return bindAddr;
        }

        LOGGER.warn("No publicAddress configured - using localhost");
        return "127.0.0.1";
    }

    private boolean isValidHost(String host) {
        return host != null && !host.isEmpty() && !"0.0.0.0".equals(host);
    }

    private int resolveProxyPort() {
        int publicPort = proxyCore.getConfig().getPublicPort();
        return publicPort > 0 ? publicPort : proxyCore.getConfig().getBindPort();
    }

    private void logTransferStart(ProxySession session, BackendServer targetBackend) {
        BackendServer current = session.getCurrentBackend();
        LOGGER.info("Session {}: Initiating transfer for {} from {} to {}",
            session.getSessionId(),
            session.getPlayerName(),
            current != null ? current.getName() : "unknown",
            targetBackend.getName());
    }

    private byte[] createReferralData(ProxySession session, BackendServer targetBackend) {
        return proxyCore.getReferralManager().createReferral(
            session.getPlayerUuid(),
            targetBackend
        );
    }

    private void sendClientReferral(ProxySession session, HostAddress address, byte[] referralData) {
        ClientReferral referral = new ClientReferral(address, referralData);

        LOGGER.info("Session {}: Sending ClientReferral to {} -> {}:{}",
            session.getSessionId(),
            session.getPlayerName(),
            address.host,
            address.port);

        session.sendToClient(referral);
    }
}

