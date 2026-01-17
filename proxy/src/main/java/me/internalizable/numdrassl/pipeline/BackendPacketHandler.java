package me.internalizable.numdrassl.pipeline;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.auth.ConnectAccept;
import com.hypixel.hytale.protocol.packets.connection.Disconnect;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import me.internalizable.numdrassl.server.ProxyCore;
import me.internalizable.numdrassl.session.ProxySession;
import me.internalizable.numdrassl.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Handles packets from the upstream backend server.
 *
 * <p>With secret-based authentication, the backend validates players using
 * HMAC-signed referral data. This handler forwards packets between backend
 * and client without intercepting authentication.</p>
 */
public final class BackendPacketHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackendPacketHandler.class);

    private final ProxyCore proxyCore;
    private final ProxySession session;

    public BackendPacketHandler(@Nonnull ProxyCore proxyCore, @Nonnull ProxySession session) {
        this.proxyCore = Objects.requireNonNull(proxyCore, "proxyCore");
        this.session = Objects.requireNonNull(session, "session");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf raw) {
            handleRawPacket(ctx, raw);
            return;
        }

        if (!(msg instanceof Packet packet)) {
            LOGGER.warn("Session {}: Unknown message type from backend: {}",
                session.getSessionId(), msg.getClass().getName());
            return;
        }

        dispatchPacket(packet);
    }

    // ==================== Packet Routing ====================

    private void handleRawPacket(ChannelHandlerContext ctx, ByteBuf raw) {
        if (proxyCore.getConfig().isDebugMode()) {
            int packetId = raw.readableBytes() >= 8 ? raw.getIntLE(4) : -1;
            LOGGER.debug("Session {}: Forwarding raw backend packet id={}", session.getSessionId(), packetId);
        }
        session.sendToClient(raw.retain());
    }

    private void dispatchPacket(Packet packet) {
        if (packet instanceof ConnectAccept accept) {
            handleConnectAccept(accept);
        } else if (packet instanceof Disconnect disconnect) {
            handleDisconnect(disconnect);
        } else {
            forwardToClient(packet);
        }
    }

    private void forwardToClient(Packet packet) {
        Packet toForward = proxyCore.getEventManager().dispatchServerPacket(session, packet);
        if (toForward != null) {
            session.sendToClient(toForward);
        }
    }

    // ==================== Specific Packet Handlers ====================

    private void handleConnectAccept(ConnectAccept accept) {
        LOGGER.info("Session {}: Backend accepted connection (secret-based auth)", session.getSessionId());

        session.setState(SessionState.CONNECTED);
        proxyCore.getSessionManager().registerPlayerUuid(session, true);

        fireApiEvents();

        // Do NOT forward ConnectAccept to client - they already completed auth with proxy
        LOGGER.debug("Session {}: Not forwarding ConnectAccept to client", session.getSessionId());
    }

    private void fireApiEvents() {
        var apiProxy = proxyCore.getApiProxy();
        if (apiProxy == null) {
            return;
        }

        var eventBridge = apiProxy.getEventBridge();
        if (eventBridge == null) {
            return;
        }

        eventBridge.firePostLoginEvent(session);
        eventBridge.fireServerConnectedEvent(session, null);
    }

    private void handleDisconnect(Disconnect disconnect) {
        LOGGER.info("Session {}: Backend disconnecting: {}", session.getSessionId(), disconnect.reason);

        if (isTransferring()) {
            LOGGER.info("Session {}: Ignoring disconnect during server transfer", session.getSessionId());
            return;
        }

        forwardToClient(disconnect);
        session.disconnect("Backend disconnected: " + disconnect.reason);
    }

    private boolean isTransferring() {
        return session.getState() == SessionState.TRANSFERRING || session.isServerTransfer();
    }

    // ==================== Channel Lifecycle ====================

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.debug("Session {}: Backend stream active", session.getSessionId());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("Session {}: Backend stream closed", session.getSessionId());

        if (shouldDisconnectClient()) {
            session.disconnect("Backend connection lost");
        }

        super.channelInactive(ctx);
    }

    private boolean shouldDisconnectClient() {
        SessionState state = session.getState();
        return state != SessionState.DISCONNECTED
            && state != SessionState.TRANSFERRING
            && !session.isServerTransfer();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("Session {}: Exception in backend handler", session.getSessionId(), cause);
        session.disconnect("Backend error: " + cause.getMessage());
    }
}
