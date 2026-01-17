package me.internalizable.numdrassl.pipeline;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.auth.AuthGrant;
import com.hypixel.hytale.protocol.packets.auth.AuthToken;
import com.hypixel.hytale.protocol.packets.auth.ServerAuthToken;
import com.hypixel.hytale.protocol.packets.connection.Connect;
import com.hypixel.hytale.protocol.packets.connection.Disconnect;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import me.internalizable.numdrassl.config.BackendServer;
import me.internalizable.numdrassl.server.ProxyServer;
import me.internalizable.numdrassl.session.ProxySession;
import me.internalizable.numdrassl.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * Handles packets from the downstream Hytale client.
 *
 * <p>Authentication flow (Client ↔ Proxy):</p>
 * <ol>
 *   <li>Client sends Connect (identity_token, uuid, username)</li>
 *   <li>Proxy validates identity_token and requests auth grant from sessions.hytale.com</li>
 *   <li>Proxy sends AuthGrant to client (authorization_grant, server_identity_token)</li>
 *   <li>Client sends AuthToken (access_token, server_authorization_grant)</li>
 *   <li>Proxy validates access_token and exchanges server_authorization_grant</li>
 *   <li>Proxy sends ServerAuthToken to client (server_access_token)</li>
 * </ol>
 *
 * <p>The Proxy ↔ Backend connection uses HMAC-signed referral data instead.</p>
 */
public class ClientPacketHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientPacketHandler.class);

    private final ProxyServer proxyServer;
    private final ProxySession session;

    public ClientPacketHandler(@Nonnull ProxyServer proxyServer, @Nonnull ProxySession session) {
        this.proxyServer = proxyServer;
        this.session = session;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        // Handle raw ByteBuf - unknown packets forwarded directly
        if (msg instanceof ByteBuf raw) {
            if (session.getState() == SessionState.CONNECTED) {
                session.sendToBackend(raw.retain());
            } else {
                raw.release();
            }
            return;
        }

        if (!(msg instanceof Packet packet)) {
            LOGGER.warn("Session {}: Received unknown message type from client: {}",
                session.getSessionId(), msg.getClass().getName());
            return;
        }

        // Handle Connect packet - start authentication
        if (packet instanceof Connect connect) {
            handleConnect(connect);
            return;
        }

        // Handle AuthToken from client - complete authentication
        if (packet instanceof AuthToken authToken) {
            handleAuthToken(authToken);
            return;
        }

        // Handle Disconnect packet
        if (packet instanceof Disconnect disconnect) {
            handleDisconnect(disconnect);
            return;
        }

        // For all other packets, dispatch through event system and forward to backend
        if (session.getState() == SessionState.CONNECTED) {
            Packet toForward = proxyServer.getEventManager().dispatchClientPacket(session, packet);
            if (toForward != null) {
                session.sendToBackend(toForward);
            }
        } else {
            LOGGER.debug("Session {}: Dropping client packet {} - not connected (state={})",
                session.getSessionId(), packet.getClass().getSimpleName(), session.getState());
        }
    }

    /**
     * Handle Connect packet from client.
     * Validates identity_token and initiates auth grant request.
     */
    private void handleConnect(Connect connect) {
        LOGGER.info("Session {}: Received Connect from {} ({})",
            session.getSessionId(), connect.username, connect.uuid);

        // Update session with player info
        session.handleConnectPacket(connect);
        session.setState(SessionState.AUTHENTICATING);

        // Register UUID mapping
        proxyServer.getSessionManager().registerPlayerUuid(session);

        // Dispatch through event system (allows plugins to cancel/modify)
        Connect processedConnect = proxyServer.getEventManager().dispatchClientPacket(session, connect);
        if (processedConnect == null) {
            session.disconnect("Connection cancelled");
            return;
        }

        // Store the connect packet for later use when connecting to backend
        session.setOriginalConnect(processedConnect);

        // Get the authenticator
        var authenticator = proxyServer.getAuthenticator();
        if (authenticator == null || !authenticator.isAuthenticated()) {
            LOGGER.error("Session {}: Proxy not authenticated! Cannot authenticate clients.", session.getSessionId());
            session.disconnect("Server authentication unavailable");
            return;
        }

        // Validate client's identity token (optional - session service will validate)
        String identityToken = connect.identityToken;
        if (identityToken == null || identityToken.isEmpty()) {
            LOGGER.warn("Session {}: Client has no identity token", session.getSessionId());
            // Continue anyway - let session service decide
        }

        // Request authorization grant from session service for this client
        LOGGER.info("Session {}: Requesting authorization grant for client", session.getSessionId());

        authenticator.requestAuthGrantForClient(connect.uuid, connect.username, identityToken)
            .thenAccept(result -> {
                if (result == null) {
                    LOGGER.error("Session {}: Failed to get auth grant for client", session.getSessionId());
                    session.disconnect("Authentication failed");
                    return;
                }

                LOGGER.info("Session {}: Got auth grant, sending AuthGrant to client", session.getSessionId());

                // Store for later validation
                session.setClientAuthGrant(result.authorizationGrant);

                // Send AuthGrant to client
                AuthGrant authGrant = new AuthGrant(result.authorizationGrant, result.serverIdentityToken);
                session.sendToClient(authGrant);
            })
            .exceptionally(ex -> {
                LOGGER.error("Session {}: Error requesting auth grant", session.getSessionId(), ex);
                session.disconnect("Authentication failed");
                return null;
            });
    }

    /**
     * Handle AuthToken from client.
     * Validates access_token and exchanges server_authorization_grant.
     */
    private void handleAuthToken(AuthToken authToken) {
        LOGGER.info("Session {}: Received AuthToken from client", session.getSessionId());

        var authenticator = proxyServer.getAuthenticator();
        if (authenticator == null) {
            LOGGER.error("Session {}: No authenticator available", session.getSessionId());
            session.disconnect("Authentication unavailable");
            return;
        }

        // Validate client's access token
        String accessToken = authToken.accessToken;
        if (accessToken == null || accessToken.isEmpty()) {
            LOGGER.error("Session {}: Client sent empty access token", session.getSessionId());
            session.disconnect("Invalid access token");
            return;
        }

        // Store client's access token
        session.setClientAccessToken(accessToken);

        // Exchange the server_authorization_grant for a server_access_token
        String serverAuthGrant = authToken.serverAuthorizationGrant;
        if (serverAuthGrant != null && !serverAuthGrant.isEmpty()) {
            LOGGER.info("Session {}: Exchanging server authorization grant", session.getSessionId());

            authenticator.exchangeServerAuthGrant(serverAuthGrant)
                .thenAccept(serverAccessToken -> {
                    if (serverAccessToken == null) {
                        LOGGER.error("Session {}: Failed to exchange server auth grant", session.getSessionId());
                        session.disconnect("Server authentication failed");
                        return;
                    }

                    LOGGER.info("Session {}: Got server access token, sending ServerAuthToken", session.getSessionId());

                    // Send ServerAuthToken to client - completes client authentication
                    ServerAuthToken serverAuthToken = new ServerAuthToken(serverAccessToken, null);
                    session.sendToClient(serverAuthToken);

                    // Now connect to backend with HMAC-signed referral
                    connectToBackend();
                })
                .exceptionally(ex -> {
                    LOGGER.error("Session {}: Error exchanging server auth grant", session.getSessionId(), ex);
                    session.disconnect("Server authentication failed");
                    return null;
                });
        } else {
            LOGGER.warn("Session {}: Client sent no server auth grant, proceeding without mutual auth",
                session.getSessionId());

            // Send ServerAuthToken anyway (some flows may not require mutual auth)
            ServerAuthToken serverAuthToken = new ServerAuthToken(null, null);
            session.sendToClient(serverAuthToken);

            // Connect to backend
            connectToBackend();
        }
    }

    /**
     * Connect to the backend server after client authentication is complete.
     * Uses HMAC-signed referral data instead of re-authenticating with Hytale.
     */
    private void connectToBackend() {
        LOGGER.info("Session {}: Client authenticated, connecting to backend", session.getSessionId());

        Connect originalConnect = session.getOriginalConnect();
        if (originalConnect == null) {
            LOGGER.error("Session {}: No original connect packet stored", session.getSessionId());
            session.disconnect("Internal error");
            return;
        }

        // Determine which backend to connect to
        BackendServer backend = null;
        if (originalConnect.uuid != null) {
            backend = proxyServer.getReferralManager().consumeReferral(
                originalConnect.uuid, originalConnect.referralData);
            if (backend != null) {
                LOGGER.info("Session {}: Player {} transferred to backend {}",
                    session.getSessionId(), originalConnect.username, backend.getName());
            }
        }

        if (backend == null) {
            backend = proxyServer.getConfig().getDefaultBackend();
        }

        if (backend == null) {
            LOGGER.error("Session {}: No backend server available", session.getSessionId());
            session.disconnect("No backend server available");
            return;
        }

        session.setCurrentBackend(backend);
        session.setState(SessionState.CONNECTING);

        // Connect to backend (BackendConnector adds HMAC-signed referral data)
        proxyServer.getBackendConnector().connect(session, backend, originalConnect);
    }

    private void handleDisconnect(Disconnect disconnect) {
        LOGGER.info("Session {}: Client disconnecting", session.getSessionId());

        // Forward to backend if connected
        if (session.getState() == SessionState.CONNECTED) {
            Packet toForward = proxyServer.getEventManager().dispatchClientPacket(session, disconnect);
            if (toForward != null) {
                session.sendToBackend(toForward);
            }
        }

        session.disconnect("Client disconnected");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("Session {}: Client stream active", session.getSessionId());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("Session {}: Client stream closed", session.getSessionId());
        session.close();
        proxyServer.getSessionManager().removeSession(session);
        proxyServer.getEventManager().dispatchSessionClosed(session);
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("Session {}: Exception in client handler", session.getSessionId(), cause);
        session.disconnect("Internal error: " + cause.getMessage());
    }
}

