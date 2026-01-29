package me.internalizable.numdrassl.pipeline.handler;

import com.hypixel.hytale.protocol.packets.connection.Connect;
import me.internalizable.numdrassl.api.event.player.PlayerChooseInitialServerEvent;
import me.internalizable.numdrassl.config.BackendServer;
import me.internalizable.numdrassl.plugin.NumdrasslProxy;
import me.internalizable.numdrassl.server.ProxyCore;
import me.internalizable.numdrassl.session.ProxySession;
import me.internalizable.numdrassl.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;

/**
 * Handles connecting a client session to a backend server.
 *
 * <p>Determines the appropriate backend based on:</p>
 * <ul>
 *   <li>Pending referrals (server transfers)</li>
 *   <li>Default backend server</li>
 * </ul>
 */
public final class BackendConnectionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackendConnectionHandler.class);

    private final ProxyCore proxyCore;
    private final ProxySession session;

    public BackendConnectionHandler(@Nonnull ProxyCore proxyCore, @Nonnull ProxySession session) {
        this.proxyCore = Objects.requireNonNull(proxyCore, "proxyCore");
        this.session = Objects.requireNonNull(session, "session");
    }

    /**
     * Connects the session to the appropriate backend server.
     *
     * <p>Uses HMAC-signed referral data for backend authentication.</p>
     */
    public void connectToBackend() {
        LOGGER.info("Session {}: Client authenticated, connecting to backend", session.getSessionId());

        Connect originalConnect = session.getOriginalConnect();
        if (originalConnect == null) {
            LOGGER.error("Session {}: No original connect packet stored", session.getSessionId());
            session.disconnect("Internal error");
            return;
        }

        BackendServer backend = resolveBackend(originalConnect);
        if (backend == null) {
            LOGGER.error("Session {}: No backend server available", session.getSessionId());
            session.disconnect("No backend server available");
            return;
        }

        initiateConnection(backend, originalConnect);
    }

    // ==================== Internal Methods ====================

    private BackendServer resolveBackend(Connect connect) {
        // Check for pending referral (server transfer)
        if (connect.uuid != null) {
            Optional<BackendServer> referral = proxyCore.getReferralManager()
                .consumeReferral(connect.uuid, connect.referralData);

            if (referral.isPresent()) {
                BackendServer backend = referral.get();
                LOGGER.info("Session {}: Player {} transferred to backend {}",
                    session.getSessionId(), connect.username, backend.getName());
                return backend;
            }
        }

        PlayerChooseInitialServerEvent.InitialServerResult initialServerResult = firePlayerChooseInitialServerEvent();
        if (initialServerResult.getMode().equals(PlayerChooseInitialServerEvent.InitialServerResult.InitialServerMode.CUSTOM)) {
            if (initialServerResult.getInitialServer() != null) {
                return proxyCore.getConfig().getBackendByName(initialServerResult.getInitialServer().getName());
            }
        }

        // Fall back to default backend
        return proxyCore.getConfig().getDefaultBackend();
    }

    private void initiateConnection(BackendServer backend, Connect connect) {
        session.setCurrentBackend(backend);
        session.setState(SessionState.CONNECTING);

        LOGGER.debug("Session {}: Initiating connection to backend {}",
            session.getSessionId(), backend.getName());

        proxyCore.getBackendConnector().connect(session, backend, connect);
    }

    private PlayerChooseInitialServerEvent.InitialServerResult firePlayerChooseInitialServerEvent() {
        NumdrasslProxy apiProxy = proxyCore.getApiProxy();
        if (apiProxy == null) {
            return PlayerChooseInitialServerEvent.InitialServerResult.useDefault();
        }

        return apiProxy.getEventBridge().firePlayerChooseInitialServerEvent(session);
    }
}

