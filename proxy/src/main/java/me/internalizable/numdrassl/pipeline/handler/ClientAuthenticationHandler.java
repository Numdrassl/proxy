package me.internalizable.numdrassl.pipeline.handler;

import com.hypixel.hytale.protocol.packets.auth.AuthGrant;
import com.hypixel.hytale.protocol.packets.auth.AuthToken;
import com.hypixel.hytale.protocol.packets.auth.ServerAuthToken;
import com.hypixel.hytale.protocol.packets.connection.Connect;
import me.internalizable.numdrassl.auth.ProxyAuthenticator;
import me.internalizable.numdrassl.server.ProxyCore;
import me.internalizable.numdrassl.session.ProxySession;
import me.internalizable.numdrassl.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Handles the client authentication flow with the proxy.
 *
 * <p>Authentication steps:</p>
 * <ol>
 *   <li>Client sends Connect (identity_token, uuid, username)</li>
 *   <li>Proxy validates and requests auth grant from sessions.hytale.com</li>
 *   <li>Proxy sends AuthGrant to client</li>
 *   <li>Client sends AuthToken (access_token, server_authorization_grant)</li>
 *   <li>Proxy exchanges server_authorization_grant</li>
 *   <li>Proxy sends ServerAuthToken to client</li>
 * </ol>
 */
public final class ClientAuthenticationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientAuthenticationHandler.class);

    private final ProxyCore proxyCore;
    private final ProxySession session;
    private final Runnable onAuthenticationComplete;

    public ClientAuthenticationHandler(
            @Nonnull ProxyCore proxyCore,
            @Nonnull ProxySession session,
            @Nonnull Runnable onAuthenticationComplete) {
        this.proxyCore = Objects.requireNonNull(proxyCore, "proxyCore");
        this.session = Objects.requireNonNull(session, "session");
        this.onAuthenticationComplete = Objects.requireNonNull(onAuthenticationComplete, "onAuthenticationComplete");
    }

    /**
     * Handles the initial Connect packet from client.
     *
     * @param connect the Connect packet
     */
    public void handleConnect(@Nonnull Connect connect) {
        Objects.requireNonNull(connect, "connect");

        LOGGER.info("Session {}: Received Connect from {} ({})",
            session.getSessionId(), connect.username, connect.uuid);

        session.handleConnectPacket(connect);
        session.setState(SessionState.AUTHENTICATING);
        proxyCore.getSessionManager().registerPlayerUuid(session);

        Connect processedConnect = dispatchConnectEvent(connect);
        if (processedConnect == null) {
            session.disconnect("Connection cancelled");
            return;
        }

        session.setOriginalConnect(processedConnect);
        requestAuthGrant(processedConnect);
    }

    /**
     * Handles AuthToken from client to complete authentication.
     *
     * @param authToken the AuthToken packet
     */
    public void handleAuthToken(@Nonnull AuthToken authToken) {
        Objects.requireNonNull(authToken, "authToken");

        LOGGER.info("Session {}: Received AuthToken from client", session.getSessionId());

        ProxyAuthenticator authenticator = proxyCore.getAuthenticator();
        if (authenticator == null) {
            LOGGER.error("Session {}: No authenticator available", session.getSessionId());
            session.disconnect("Authentication unavailable");
            return;
        }

        if (!validateAccessToken(authToken.accessToken)) {
            return;
        }

        session.setClientAccessToken(authToken.accessToken);
        exchangeServerAuthGrant(authenticator, authToken.serverAuthorizationGrant);
    }

    // ==================== Internal Methods ====================

    private Connect dispatchConnectEvent(Connect connect) {
        return proxyCore.getEventManager().dispatchClientPacket(session, connect);
    }

    private void requestAuthGrant(Connect connect) {
        ProxyAuthenticator authenticator = proxyCore.getAuthenticator();
        if (authenticator == null || !authenticator.isAuthenticated()) {
            LOGGER.error("Session {}: Proxy not authenticated!", session.getSessionId());
            session.disconnect("Server authentication unavailable");
            return;
        }

        String identityToken = connect.identityToken;
        if (identityToken == null || identityToken.isEmpty()) {
            LOGGER.warn("Session {}: Client has no identity token", session.getSessionId());
        }

        LOGGER.info("Session {}: Requesting authorization grant for client", session.getSessionId());

        authenticator.requestAuthGrantForClient(connect.uuid, connect.username, identityToken)
            .thenAccept(this::handleAuthGrantResult)
            .exceptionally(this::handleAuthGrantError);
    }

    private void handleAuthGrantResult(me.internalizable.numdrassl.auth.session.ClientAuthHandler.AuthGrantResult result) {
        if (result == null) {
            LOGGER.error("Session {}: Failed to get auth grant for client", session.getSessionId());
            session.disconnect("Authentication failed");
            return;
        }

        LOGGER.info("Session {}: Got auth grant, sending AuthGrant to client", session.getSessionId());

        session.setClientAuthGrant(result.authorizationGrant());
        AuthGrant authGrant = new AuthGrant(result.authorizationGrant(), result.serverIdentityToken());
        session.sendToClient(authGrant);
    }

    private Void handleAuthGrantError(Throwable ex) {
        LOGGER.error("Session {}: Error requesting auth grant", session.getSessionId(), ex);
        session.disconnect("Authentication failed");
        return null;
    }

    private boolean validateAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) {
            LOGGER.error("Session {}: Client sent empty access token", session.getSessionId());
            session.disconnect("Invalid access token");
            return false;
        }
        return true;
    }

    private void exchangeServerAuthGrant(ProxyAuthenticator authenticator, String serverAuthGrant) {
        if (serverAuthGrant != null && !serverAuthGrant.isEmpty()) {
            LOGGER.info("Session {}: Exchanging server authorization grant", session.getSessionId());

            authenticator.exchangeServerAuthGrant(serverAuthGrant)
                .thenAccept(this::handleServerAccessToken)
                .exceptionally(this::handleExchangeError);
        } else {
            LOGGER.warn("Session {}: No server auth grant, proceeding without mutual auth",
                session.getSessionId());
            completeAuthentication(null);
        }
    }

    private void handleServerAccessToken(String serverAccessToken) {
        if (serverAccessToken == null) {
            LOGGER.error("Session {}: Failed to exchange server auth grant", session.getSessionId());
            session.disconnect("Server authentication failed");
            return;
        }

        LOGGER.info("Session {}: Got server access token, sending ServerAuthToken", session.getSessionId());
        completeAuthentication(serverAccessToken);
    }

    private Void handleExchangeError(Throwable ex) {
        LOGGER.error("Session {}: Error exchanging server auth grant", session.getSessionId(), ex);
        session.disconnect("Server authentication failed");
        return null;
    }

    private void completeAuthentication(String serverAccessToken) {
        ServerAuthToken serverAuthToken = new ServerAuthToken(serverAccessToken, null);
        session.sendToClient(serverAuthToken);
        onAuthenticationComplete.run();
    }
}

