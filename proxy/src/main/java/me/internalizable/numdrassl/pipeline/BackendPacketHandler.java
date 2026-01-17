package me.internalizable.numdrassl.pipeline;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.auth.ConnectAccept;
import com.hypixel.hytale.protocol.packets.connection.Disconnect;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import me.internalizable.numdrassl.server.ProxyServer;
import me.internalizable.numdrassl.session.ProxySession;
import me.internalizable.numdrassl.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * Handles packets from the upstream backend server.
 *
 * <p>With secret-based authentication, the backend validates the player using
 * HMAC-signed referral data in the Connect packet. This handler simply forwards
 * packets between backend and client without intercepting authentication.</p>
 */
public class BackendPacketHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackendPacketHandler.class);

    private final ProxyServer proxyServer;
    private final ProxySession session;

    public BackendPacketHandler(@Nonnull ProxyServer proxyServer, @Nonnull ProxySession session) {
        this.proxyServer = proxyServer;
        this.session = session;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        // Handle raw ByteBuf - unknown packets forwarded directly
        if (msg instanceof ByteBuf raw) {
            if (proxyServer.getConfig().isDebugMode()) {
                int packetId = raw.readableBytes() >= 8 ? raw.getIntLE(4) : -1;
                LOGGER.debug("Session {}: Forwarding raw backend packet id={} to client",
                    session.getSessionId(), packetId);
            }
            session.sendToClient(raw.retain());
            return;
        }

        if (!(msg instanceof Packet packet)) {
            LOGGER.warn("Session {}: Received unknown message type from backend: {}",
                session.getSessionId(), msg.getClass().getName());
            return;
        }

        // Handle ConnectAccept - backend accepted the connection
        if (packet instanceof ConnectAccept accept) {
            handleConnectAccept(accept);
            return;
        }

        // Handle Disconnect from backend
        if (packet instanceof Disconnect disconnect) {
            handleDisconnect(disconnect);
            return;
        }

        // Dispatch through event system and forward to client
        Packet toForward = proxyServer.getEventManager().dispatchServerPacket(session, packet);
        if (toForward != null) {
            session.sendToClient(toForward);
        }
    }

    private void handleConnectAccept(ConnectAccept accept) {
        LOGGER.info("Session {}: Backend accepted connection (secret-based auth)", session.getSessionId());

        session.setState(SessionState.CONNECTED);

        // Register player UUID and kick any existing session with same UUID
        proxyServer.getSessionManager().registerPlayerUuid(session, true);

        // Fire API events for plugin developers
        var apiServer = proxyServer.getApiServer();
        if (apiServer != null) {
            var eventBridge = apiServer.getEventBridge();
            if (eventBridge != null) {
                // Fire PostLoginEvent (player fully authenticated)
                eventBridge.firePostLoginEvent(session);
                // Fire ServerConnectedEvent (connected to backend)
                eventBridge.fireServerConnectedEvent(session, null);
            }
        }

        // Do NOT forward ConnectAccept to client!
        // The client already completed authentication with the proxy (received ServerAuthToken).
        // Sending ConnectAccept would confuse the client as it's not expecting it.
        // The client is now ready to receive game packets directly.
        LOGGER.debug("Session {}: Not forwarding ConnectAccept to client (proxy handled auth)", session.getSessionId());
    }

    private void handleDisconnect(Disconnect disconnect) {
        LOGGER.info("Session {}: Backend disconnecting: {}", session.getSessionId(), disconnect.reason);

        // If we're in the process of transferring to another server, don't disconnect the client
        if (session.getState() == SessionState.TRANSFERRING || session.isServerTransfer()) {
            LOGGER.info("Session {}: Ignoring disconnect during server transfer", session.getSessionId());
            return;
        }

        // Forward to client
        Packet toForward = proxyServer.getEventManager().dispatchServerPacket(session, disconnect);
        if (toForward != null) {
            session.sendToClient(toForward);
        }

        session.disconnect("Backend disconnected: " + disconnect.reason);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("Session {}: Backend stream active", session.getSessionId());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("Session {}: Backend stream closed", session.getSessionId());

        // If we're transferring or already disconnected, don't try to disconnect again
        SessionState currentState = session.getState();
        if (currentState != SessionState.DISCONNECTED &&
            currentState != SessionState.TRANSFERRING &&
            !session.isServerTransfer()) {
            session.disconnect("Backend connection lost");
        }

        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("Session {}: Exception in backend handler", session.getSessionId(), cause);
        session.disconnect("Backend error: " + cause.getMessage());
    }
}

