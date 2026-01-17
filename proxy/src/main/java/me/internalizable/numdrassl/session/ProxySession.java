package me.internalizable.numdrassl.session;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.connection.Connect;
import io.netty.buffer.ByteBuf;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import me.internalizable.numdrassl.config.BackendServer;
import me.internalizable.numdrassl.server.ProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import me.internalizable.numdrassl.auth.CertificateExtractor;

/**
 * Represents a proxy session for a connected Hytale client.
 * Manages both the downstream (client) and upstream (backend server) connections.
 *
 * <p>With secret-based authentication, the session is simplified - no JWT token
 * handling is needed. The backend validates players using HMAC-signed referral data.</p>
 */
public class ProxySession {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxySession.class);
    private static final AtomicLong SESSION_ID_GENERATOR = new AtomicLong(0);

    private final long sessionId;
    private final ProxyServer proxyServer;
    private final QuicChannel clientChannel;
    private final InetSocketAddress clientAddress;

    // Client's TLS certificate (extracted from mTLS handshake)
    private volatile X509Certificate clientCertificate;
    private volatile String clientCertificateFingerprint;

    // Flag to indicate this is a server transfer (player already connected)
    private volatile boolean isServerTransfer = false;

    // Authentication state (for Client ↔ Proxy auth flow)
    private volatile Connect originalConnect;
    private volatile String clientAuthGrant;
    private volatile String clientAccessToken;

    private final AtomicReference<SessionState> state = new AtomicReference<>(SessionState.HANDSHAKING);
    private final AtomicReference<QuicChannel> backendChannel = new AtomicReference<>();
    private final AtomicReference<QuicStreamChannel> clientStream = new AtomicReference<>();
    private final AtomicReference<QuicStreamChannel> backendStream = new AtomicReference<>();

    // Player info (populated after Connect packet)
    private volatile UUID playerUuid;
    private volatile String playerName;
    private volatile String protocolHash;
    private volatile String clientIdentityToken;
    private volatile BackendServer currentBackend;

    public ProxySession(@Nonnull ProxyServer proxyServer, @Nonnull QuicChannel clientChannel) {
        this.sessionId = SESSION_ID_GENERATOR.incrementAndGet();
        this.proxyServer = proxyServer;
        this.clientChannel = clientChannel;

        // Extract client address
        java.net.SocketAddress remoteAddr = clientChannel.remoteAddress();
        if (remoteAddr instanceof InetSocketAddress) {
            this.clientAddress = (InetSocketAddress) remoteAddr;
        } else {
            this.clientAddress = new InetSocketAddress("0.0.0.0", 0);
        }

        // Extract client certificate from mTLS handshake
        this.clientCertificate = CertificateExtractor.extractClientCertificate(clientChannel);
        if (this.clientCertificate != null) {
            this.clientCertificateFingerprint = CertificateExtractor.computeCertificateFingerprint(this.clientCertificate);
            LOGGER.debug("Session {}: Client certificate fingerprint: {}", sessionId, clientCertificateFingerprint);
        }
    }

    // ==========================================
    // Certificate Info
    // ==========================================

    @Nullable
    public X509Certificate getClientCertificate() {
        return clientCertificate;
    }

    @Nullable
    public String getClientCertificateFingerprint() {
        return clientCertificateFingerprint;
    }

    // ==========================================
    // Server Transfer
    // ==========================================

    public boolean isServerTransfer() {
        return isServerTransfer;
    }

    public void setServerTransfer(boolean serverTransfer) {
        this.isServerTransfer = serverTransfer;
    }

    // ==========================================
    // Authentication State (Client ↔ Proxy)
    // ==========================================

    @Nullable
    public Connect getOriginalConnect() {
        return originalConnect;
    }

    public void setOriginalConnect(@Nullable Connect connect) {
        this.originalConnect = connect;
    }

    @Nullable
    public String getClientAuthGrant() {
        return clientAuthGrant;
    }

    public void setClientAuthGrant(@Nullable String grant) {
        this.clientAuthGrant = grant;
    }

    @Nullable
    public String getClientAccessToken() {
        return clientAccessToken;
    }

    public void setClientAccessToken(@Nullable String token) {
        this.clientAccessToken = token;
    }

    // ==========================================
    // Session Info
    // ==========================================

    public long getSessionId() {
        return sessionId;
    }

    @Nonnull
    public ProxyServer getProxyServer() {
        return proxyServer;
    }

    @Nonnull
    public QuicChannel getClientChannel() {
        return clientChannel;
    }

    @Nonnull
    public InetSocketAddress getClientAddress() {
        return clientAddress;
    }

    @Nonnull
    public SessionState getState() {
        return state.get();
    }

    public void setState(@Nonnull SessionState newState) {
        SessionState oldState = state.getAndSet(newState);
        LOGGER.debug("Session {} state changed: {} -> {}", sessionId, oldState, newState);
    }

    // ==========================================
    // Backend Connection
    // ==========================================

    @Nullable
    public QuicChannel getBackendChannel() {
        return backendChannel.get();
    }

    public void setBackendChannel(@Nullable QuicChannel channel) {
        backendChannel.set(channel);
    }

    @Nullable
    public QuicStreamChannel getClientStream() {
        return clientStream.get();
    }

    public void setClientStream(@Nullable QuicStreamChannel stream) {
        clientStream.set(stream);
    }

    @Nullable
    public QuicStreamChannel getBackendStream() {
        return backendStream.get();
    }

    public void setBackendStream(@Nullable QuicStreamChannel stream) {
        backendStream.set(stream);
    }

    // ==========================================
    // Player Info
    // ==========================================

    @Nullable
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    @Nullable
    public String getPlayerName() {
        return playerName;
    }

    @Nullable
    public String getUsername() {
        return playerName;
    }

    @Nullable
    public String getProtocolHash() {
        return protocolHash;
    }

    @Nullable
    public String getClientIdentityToken() {
        return clientIdentityToken;
    }

    @Nullable
    public BackendServer getCurrentBackend() {
        return currentBackend;
    }

    public void setCurrentBackend(@Nullable BackendServer backend) {
        this.currentBackend = backend;
    }

    @Nullable
    public String getCurrentServerName() {
        return currentBackend != null ? currentBackend.getName() : null;
    }

    /**
     * Update session info from a Connect packet.
     */
    public void handleConnectPacket(@Nonnull Connect connect) {
        this.playerUuid = connect.uuid;
        this.playerName = connect.username;
        this.protocolHash = connect.protocolHash;
        this.clientIdentityToken = connect.identityToken;
        LOGGER.info("Session {} identified: {} ({})", sessionId, playerName, playerUuid);
    }

    // ==========================================
    // Packet Sending
    // ==========================================

    /**
     * Send a packet to the connected client.
     * Thread-safe: will execute on the client stream's event loop.
     */
    public void sendToClient(@Nonnull Packet packet) {
        QuicStreamChannel stream = clientStream.get();
        if (stream != null && stream.isActive()) {
            if (stream.eventLoop().inEventLoop()) {
                stream.writeAndFlush(packet);
            } else {
                stream.eventLoop().execute(() -> {
                    if (stream.isActive()) {
                        stream.writeAndFlush(packet);
                    }
                });
            }
        } else {
            LOGGER.warn("Session {}: Cannot send to client - stream not active", sessionId);
        }
    }

    /**
     * Send an arbitrary object (Packet or ByteBuf) to the client.
     */
    public void sendToClient(@Nonnull Object obj) {
        if (obj instanceof Packet) {
            sendToClient((Packet) obj);
            return;
        }

        QuicStreamChannel stream = clientStream.get();
        if (stream != null && stream.isActive()) {
            if (stream.eventLoop().inEventLoop()) {
                stream.writeAndFlush(obj);
            } else {
                final Object finalObj = obj;
                stream.eventLoop().execute(() -> {
                    if (stream.isActive()) {
                        stream.writeAndFlush(finalObj);
                    } else if (finalObj instanceof ByteBuf) {
                        ((ByteBuf) finalObj).release();
                    }
                });
            }
        } else {
            LOGGER.warn("Session {}: Cannot send to client - stream not active", sessionId);
            if (obj instanceof ByteBuf) {
                ((ByteBuf) obj).release();
            }
        }
    }

    /**
     * Send a packet to the backend server.
     * Thread-safe: will execute on the backend stream's event loop.
     */
    public void sendToBackend(@Nonnull Packet packet) {
        QuicStreamChannel stream = backendStream.get();
        if (stream != null && stream.isActive()) {
            if (stream.eventLoop().inEventLoop()) {
                stream.writeAndFlush(packet);
            } else {
                stream.eventLoop().execute(() -> {
                    if (stream.isActive()) {
                        stream.writeAndFlush(packet);
                    }
                });
            }
        } else {
            LOGGER.warn("Session {}: Cannot send to backend - stream not active", sessionId);
        }
    }

    /**
     * Send an arbitrary object (Packet or ByteBuf) to the backend server.
     */
    public void sendToBackend(@Nonnull Object obj) {
        if (obj instanceof Packet) {
            sendToBackend((Packet) obj);
            return;
        }

        QuicStreamChannel stream = backendStream.get();
        if (stream != null && stream.isActive()) {
            if (stream.eventLoop().inEventLoop()) {
                stream.writeAndFlush(obj);
            } else {
                final Object finalObj = obj;
                stream.eventLoop().execute(() -> {
                    if (stream.isActive()) {
                        stream.writeAndFlush(finalObj);
                    } else if (finalObj instanceof ByteBuf) {
                        ((ByteBuf) finalObj).release();
                    }
                });
            }
        } else {
            LOGGER.warn("Session {}: Cannot send to backend - stream not active", sessionId);
            if (obj instanceof ByteBuf) {
                ((ByteBuf) obj).release();
            }
        }
    }

    /**
     * Send a packet to the server (alias for sendToBackend).
     */
    public void sendToServer(@Nonnull Packet packet) {
        sendToBackend(packet);
    }

    // ==========================================
    // Session Lifecycle
    // ==========================================

    /**
     * Disconnect the client with a reason.
     */
    public void disconnect(@Nonnull String reason) {
        LOGGER.info("Session {} disconnecting: {}", sessionId, reason);
        state.set(SessionState.DISCONNECTED);

        // Close backend connection first
        QuicChannel backend = backendChannel.get();
        if (backend != null && backend.isActive()) {
            backend.close();
        }

        // Then close client connection
        if (clientChannel.isActive()) {
            clientChannel.close();
        }

        proxyServer.getSessionManager().removeSession(this);
    }

    /**
     * Close all connections for this session.
     */
    public void close() {
        state.set(SessionState.DISCONNECTED);

        QuicStreamChannel cs = clientStream.get();
        if (cs != null && cs.isActive()) {
            cs.close();
        }

        QuicStreamChannel bs = backendStream.get();
        if (bs != null && bs.isActive()) {
            bs.close();
        }

        QuicChannel bc = backendChannel.get();
        if (bc != null && bc.isActive()) {
            bc.close();
        }

        if (clientChannel.isActive()) {
            clientChannel.close();
        }
    }

    /**
     * Check if this session is still active.
     */
    public boolean isActive() {
        return clientChannel.isActive() && state.get() != SessionState.DISCONNECTED;
    }

    /**
     * Get the player's ping/latency in milliseconds.
     * Returns -1 if unknown.
     */
    public long getPing() {
        // TODO: Implement actual ping tracking
        return -1;
    }

    /**
     * Send a chat message to the player.
     */
    public void sendChatMessage(@Nonnull String message) {
        com.hypixel.hytale.protocol.packets.interface_.ChatMessage chatMsg =
            new com.hypixel.hytale.protocol.packets.interface_.ChatMessage(message);
        sendToClient(chatMsg);
    }

    // ==========================================
    // Server Transfer
    // ==========================================

    /**
     * Switch this session to a different backend server.
     */
    public boolean switchToServer(@Nonnull BackendServer newBackend) {
        SessionState currentState = state.get();
        if (currentState != SessionState.CONNECTED) {
            LOGGER.warn("Session {}: Cannot switch servers - not in CONNECTED state (current: {})",
                sessionId, currentState);
            return false;
        }

        if (currentBackend != null && currentBackend.getName().equalsIgnoreCase(newBackend.getName())) {
            LOGGER.warn("Session {}: Already connected to server {}", sessionId, newBackend.getName());
            return false;
        }

        LOGGER.info("Session {}: Initiating server switch from {} to {}",
            sessionId, currentBackend != null ? currentBackend.getName() : "none", newBackend.getName());

        // Set state to transferring
        setState(SessionState.TRANSFERRING);
        this.isServerTransfer = true;

        // Close the current backend connection
        closeBackendConnection();

        // Create a new Connect packet with the player's info
        Connect connectPacket = new Connect();
        connectPacket.uuid = playerUuid;
        connectPacket.username = playerName;
        connectPacket.protocolHash = protocolHash;
        connectPacket.identityToken = clientIdentityToken;

        // Initiate connection to the new backend
        proxyServer.getBackendConnector().reconnect(this, newBackend, connectPacket);

        return true;
    }

    /**
     * Close the backend connection without disconnecting the client.
     */
    private void closeBackendConnection() {
        QuicStreamChannel bs = backendStream.get();
        if (bs != null && bs.isActive()) {
            bs.close();
        }
        backendStream.set(null);

        QuicChannel bc = backendChannel.get();
        if (bc != null && bc.isActive()) {
            bc.close();
        }
        backendChannel.set(null);
    }

    /**
     * Transfer this player to a different server by host and port.
     */
    public boolean transferTo(@Nonnull String host, int port) {
        // Find existing backend server for this address
        for (BackendServer backend : proxyServer.getConfig().getBackends()) {
            if (backend.getHost().equalsIgnoreCase(host) && backend.getPort() == port) {
                return switchToServer(backend);
            }
        }

        // Create a temporary backend server
        BackendServer tempBackend = new BackendServer("temp-" + host + "-" + port, host, port, false);
        return switchToServer(tempBackend);
    }

    @Override
    public String toString() {
        return "ProxySession{" +
            "id=" + sessionId +
            ", player=" + playerName +
            ", uuid=" + playerUuid +
            ", state=" + state.get() +
            ", clientAddress=" + clientAddress +
            '}';
    }
}

