package me.internalizable.numdrassl.server;

import com.hypixel.hytale.protocol.HostAddress;
import com.hypixel.hytale.protocol.packets.auth.ClientReferral;
import me.internalizable.numdrassl.config.BackendServer;
import me.internalizable.numdrassl.session.ProxySession;
import me.internalizable.numdrassl.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * API for transferring players between backend servers.
 *
 * <p>Uses ClientReferral to tell the client to disconnect and reconnect to the proxy.
 * When they reconnect with the referral data, the ReferralManager routes them to
 * the target backend server.</p>
 */
public class PlayerTransfer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerTransfer.class);

    private final ProxyServer proxyServer;

    public PlayerTransfer(@Nonnull ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    /**
     * Transfer a player to a different backend server.
     * Sends a ClientReferral packet which causes the client to disconnect and reconnect.
     * When they reconnect, the ReferralManager routes them to the new backend.
     *
     * @param session The player's session
     * @param targetBackend The backend to transfer to
     * @return A future that completes when the referral is sent
     */
    public CompletableFuture<TransferResult> transfer(@Nonnull ProxySession session, @Nonnull BackendServer targetBackend) {
        CompletableFuture<TransferResult> future = new CompletableFuture<>();

        if (session.getState() != SessionState.CONNECTED) {
            future.complete(new TransferResult(false, "Player not connected"));
            return future;
        }

        if (session.getPlayerUuid() == null) {
            future.complete(new TransferResult(false, "Player UUID not known"));
            return future;
        }

        BackendServer currentBackend = session.getCurrentBackend();
        if (currentBackend != null && currentBackend.getName().equalsIgnoreCase(targetBackend.getName())) {
            future.complete(new TransferResult(false, "Already connected to this server"));
            return future;
        }

        LOGGER.info("Session {}: Initiating transfer for {} from {} to {}",
                session.getSessionId(),
                session.getPlayerName(),
                currentBackend != null ? currentBackend.getName() : "unknown",
                targetBackend.getName());

        // Create referral data that will tell us where to route the player on reconnect
        byte[] referralData = proxyServer.getReferralManager().createReferral(
                session.getPlayerUuid(),
                targetBackend
        );

        // Get the proxy's public address - this is where the client should reconnect
        String proxyHost = proxyServer.getConfig().getPublicAddress();
        int proxyPort = proxyServer.getConfig().getPublicPort();

        if (proxyPort <= 0) {
            proxyPort = proxyServer.getConfig().getBindPort();
        }

        if (proxyHost == null || proxyHost.isEmpty() || "0.0.0.0".equals(proxyHost)) {
            String bindAddr = proxyServer.getConfig().getBindAddress();
            if (bindAddr != null && !bindAddr.isEmpty() && !"0.0.0.0".equals(bindAddr)) {
                proxyHost = bindAddr;
            } else {
                proxyHost = "127.0.0.1";
                LOGGER.warn("Session {}: No publicAddress configured - using localhost.", session.getSessionId());
            }
        }

        if (proxyPort > 32767) {
            LOGGER.error("Session {}: Port {} exceeds maximum value for ClientReferral (32767).",
                session.getSessionId(), proxyPort);
            future.complete(new TransferResult(false, "Port exceeds maximum value for player transfers"));
            return future;
        }

        // Create and send the ClientReferral packet
        HostAddress hostTo = new HostAddress(proxyHost, (short) proxyPort);
        ClientReferral referral = new ClientReferral(hostTo, referralData);

        LOGGER.info("Session {}: Sending ClientReferral to {} -> {}:{}",
                session.getSessionId(), session.getPlayerName(), proxyHost, proxyPort);

        session.sendToClient(referral);

        future.complete(new TransferResult(true, "Transfer initiated"));
        return future;
    }

    /**
     * Transfer a player to a backend server by name.
     *
     * @param session The player's session
     * @param backendName The name of the backend to transfer to
     * @return A future that completes when the referral is sent
     */
    public CompletableFuture<TransferResult> transfer(@Nonnull ProxySession session, @Nonnull String backendName) {
        BackendServer backend = proxyServer.getConfig().getBackendByName(backendName);
        if (backend == null) {
            CompletableFuture<TransferResult> future = new CompletableFuture<>();
            future.complete(new TransferResult(false, "Unknown backend server: " + backendName));
            return future;
        }
        return transfer(session, backend);
    }

    /**
     * Result of a player transfer attempt
     */
    public static class TransferResult {
        private final boolean success;
        private final String message;

        public TransferResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}
