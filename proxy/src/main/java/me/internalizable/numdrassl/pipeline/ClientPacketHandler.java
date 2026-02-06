package me.internalizable.numdrassl.pipeline;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.auth.AuthToken;
import com.hypixel.hytale.protocol.packets.connection.Connect;
import com.hypixel.hytale.protocol.packets.connection.Disconnect;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import me.internalizable.numdrassl.pipeline.handler.BackendConnectionHandler;
import me.internalizable.numdrassl.pipeline.handler.ClientAuthenticationHandler;
import me.internalizable.numdrassl.profiling.ProxyMetrics;
import me.internalizable.numdrassl.server.ProxyCore;
import me.internalizable.numdrassl.session.ProxySession;
import me.internalizable.numdrassl.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Handles packets from the downstream Hytale client.
 *
 * <p>Delegates to specialized handlers:</p>
 * <ul>
 *   <li>{@link ClientAuthenticationHandler} - Authentication flow</li>
 *   <li>{@link BackendConnectionHandler} - Backend connection</li>
 * </ul>
 */
public final class ClientPacketHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientPacketHandler.class);

    private final ProxyCore proxyCore;
    private final ProxySession session;
    private final ClientAuthenticationHandler authHandler;
    private final BackendConnectionHandler connectionHandler;

    /**
     * Buffer for raw packets received within a single Netty read batch.
     * All buffered packets are submitted as a single cross-event-loop task
     * in {@link #channelReadComplete(ChannelHandlerContext)}, reducing
     * scheduling overhead from O(N) to O(1) per batch.
     */
    private List<ByteBuf> pendingRawToBackend = new ArrayList<>();

    public ClientPacketHandler(@Nonnull ProxyCore proxyCore, @Nonnull ProxySession session) {
        this.proxyCore = Objects.requireNonNull(proxyCore, "proxyCore");
        this.session = Objects.requireNonNull(session, "session");
        this.connectionHandler = new BackendConnectionHandler(proxyCore, session);
        this.authHandler = new ClientAuthenticationHandler(proxyCore, session, connectionHandler::connectToBackend);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf raw) {
            handleRawPacket(raw);
            return;
        }

        if (!(msg instanceof Packet packet)) {
            LOGGER.warn("Session {}: Unknown message type from client: {}",
                session.getSessionId(), msg.getClass().getName());
            return;
        }
        ProxyMetrics.getInstance().recordPacketFromClient(packet.getClass().getSimpleName(), 0);
        dispatchPacket(packet);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        // Submit all buffered raw packets as a single cross-event-loop task.
        // This reduces scheduling overhead from O(N) tasks to O(1) per read batch.
        if (!pendingRawToBackend.isEmpty()) {
            List<ByteBuf> batch = pendingRawToBackend;
            pendingRawToBackend = new ArrayList<>();
            session.sendRawBatchToBackend(batch);
        }
        super.channelReadComplete(ctx);
    }

    // ==================== Packet Routing ====================

    private void handleRawPacket(ByteBuf raw) {
        // Fast path: buffer raw packets for batch submission.
        // Instead of submitting each packet as a separate cross-event-loop task,
        // we buffer them and submit the entire batch in channelReadComplete().
        if (session.getState() == SessionState.CONNECTED) {
            int bytes = raw.readableBytes();
            pendingRawToBackend.add(raw.retain());
            ProxyMetrics.getInstance().recordRawBytesFromClient(bytes);
            ProxyMetrics.getInstance().recordRawBytesToBackend(bytes);
        } else {
            LOGGER.debug("Session {}: Dropping raw packet - not connected (state={})",
                session.getSessionId(), session.getState());
        }
    }

    private void dispatchPacket(Packet packet) {
        if (packet instanceof Connect connect) {
            authHandler.handleConnect(connect);
        } else if (packet instanceof AuthToken authToken) {
            authHandler.handleAuthToken(authToken);
        } else if (packet instanceof Disconnect disconnect) {
            handleDisconnect(disconnect);
        } else {
            forwardToBackend(packet);
        }
    }

    private void handleDisconnect(Disconnect disconnect) {
        LOGGER.info("Session {}: Client disconnecting", session.getSessionId());

        if (session.getState() == SessionState.CONNECTED) {
            Packet toForward = proxyCore.getEventManager().dispatchClientPacket(session, disconnect);
            if (toForward != null) {
                session.sendToBackend(toForward);
            }
        }

        session.disconnect("Client disconnected");
    }

    private void forwardToBackend(Packet packet) {
        if (session.getState() == SessionState.CONNECTED) {
            Packet toForward = proxyCore.getEventManager().dispatchClientPacket(session, packet);
            if (toForward != null) {
                session.sendToBackend(toForward);
            }
        } else {
            LOGGER.debug("Session {}: Dropping packet {} - not connected (state={})",
                session.getSessionId(), packet.getClass().getSimpleName(), session.getState());
        }
    }

    // ==================== Channel Lifecycle ====================

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("Session {}: Client stream closed", session.getSessionId());
        releasePendingBuffers();
        cleanupSession();
        super.channelInactive(ctx);
    }

    private void releasePendingBuffers() {
        for (ByteBuf buf : pendingRawToBackend) {
            ReferenceCountUtil.safeRelease(buf);
        }
        pendingRawToBackend.clear();
    }

    private void cleanupSession() {
        session.close();
        proxyCore.getSessionManager().removeSession(session);
        proxyCore.getEventManager().dispatchSessionClosed(session);
        ProxyMetrics.getInstance().recordConnectionClosed();
        ProxyMetrics.getInstance().decrementActiveSession();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("Session {}: Exception in client handler", session.getSessionId(), cause);
        releasePendingBuffers();
        session.disconnect("Internal error: " + cause.getMessage());
    }
}
