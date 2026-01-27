package me.internalizable.numdrassl.pipeline.handler;

import com.hypixel.hytale.protocol.packets.auth.AuthGrant;
import com.hypixel.hytale.protocol.packets.auth.AuthToken;
import com.hypixel.hytale.protocol.packets.auth.ServerAuthToken;
import com.hypixel.hytale.protocol.packets.connection.Connect;
import me.internalizable.numdrassl.api.event.connection.AsyncLoginEvent;
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

        // Fire PermissionSetupEvent early - this allows permission plugins like LuckPerms
        // to start loading user data asynchronously while authentication proceeds.
        // By the time LoginEvent fires (after auth completes), the data should be ready.
        firePermissionSetupEvent();

        // Store the connect packet - LoginEvent will be fired after authentication completes
        // in completeAuthentication() to give async permission loading time to complete.
        session.setOriginalConnect(connect);
        requestAuthGrant(connect);
    }

    /**
     * Fires the PermissionSetupEvent to allow plugins to set up permissions.
     * This is called early in the connection flow to give async plugins time to load data.
     * The returned future completes when permission setup is done.
     */
    private void firePermissionSetupEvent() {
        var apiProxy = proxyCore.getApiProxy();
        if (apiProxy == null) {
            return;
        }

        // Create and cache the player, start async permission loading
        // The returned future will complete when all async tasks are done
        apiProxy.getEventBridge().getLifecycleHandler().setupPlayerPermissions(session)
            .whenComplete((player, ex) -> {
                if (ex != null) {
                    LOGGER.warn("Session {}: Error during permission setup", session.getSessionId(), ex);
                } else if (player == null) {
                    LOGGER.debug("Session {}: Could not create player for permission setup", session.getSessionId());
                } else {
                    LOGGER.debug("Session {}: Permission setup completed for {}", session.getSessionId(), player.getUsername());
                }
            });
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

    /**
     * Completes the client authentication flow and initiates the asynchronous login barrier.
     *
     * <p>This method sends the success token to the client immediately to satisfy the handshake protocol,
     * but internally holds the connection state until all plugins verify the session via
     * {@link AsyncLoginEvent}.</p>
     *
     * @param serverAccessToken The access token verified by the central auth server.
     */
    private void completeAuthentication(String serverAccessToken) {
        // 1. Send success packet to client immediately to prevent protocol timeouts
        ServerAuthToken serverAuthToken = new ServerAuthToken(serverAccessToken, null);
        session.sendToClient(serverAuthToken);

        // 2. Get lifecycle handler
        var apiProxy = proxyCore.getApiProxy();
        if (apiProxy == null) {
            LOGGER.error("Session {}: API Proxy not initialized during login sequence",
                    session.getSessionId());
            session.disconnect("Internal Proxy Error: API Bridge unavailable");
            return;
        }

        var lifecycleHandler = apiProxy.getEventBridge().getLifecycleHandler();

        // 3. Execute async login phase (delegates all async barrier logic)
        lifecycleHandler.onAsyncLogin(session)
                .thenAccept(result -> {

                    if (!result.isAllowed()) {
                        String denyReason = result.getDenyReason();
                        session.disconnect(denyReason != null ? denyReason : "Connection denied by proxy");
                        return;
                    }

                    // Fire LoginEvent now that authentication is complete
                    // This gives permission plugins time to load data between PermissionSetupEvent and LoginEvent
                    fireLoginEvent();

                    onAuthenticationComplete.run();
                })
                .exceptionally(ex -> {
                    LOGGER.error("Session {}: Async login phase failed", session.getSessionId(), ex);

                    // Determine appropriate disconnect message based on exception type
                    String disconnectMessage = ex instanceof IllegalStateException
                            ? ex.getMessage()
                            : "Internal Server Error: Data loading failed";

                    session.disconnect(disconnectMessage);
                    return null;
                });
    }

    /**
     * Fires the LoginEvent after authentication completes.
     * If cancelled, disconnects the player.
     */
    private void fireLoginEvent() {
        Connect connect = session.getOriginalConnect();
        if (connect == null) {
            return;
        }

        Connect processed = dispatchConnectEvent(connect);
        if (processed == null) {
            session.disconnect("Login denied");
        }
    }
}

