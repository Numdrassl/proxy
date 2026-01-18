package me.internalizable.numdrassl.session;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.connection.Connect;
import io.netty.buffer.ByteBuf;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import me.internalizable.numdrassl.api.chat.ChatMessageBuilder;
import me.internalizable.numdrassl.auth.CertificateExtractor;
import me.internalizable.numdrassl.config.BackendServer;
import me.internalizable.numdrassl.server.ProxyCore;
import me.internalizable.numdrassl.server.network.ChatMessageConverter;
import me.internalizable.numdrassl.session.auth.SessionAuthState;
import me.internalizable.numdrassl.session.channel.PacketSender;
import me.internalizable.numdrassl.session.channel.SessionChannels;
import me.internalizable.numdrassl.session.identity.PlayerIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a proxy session for a connected Hytale client.
 *
 * <p>A session manages the bidirectional connection between a Hytale client
 * and a backend server, handling authentication, packet routing, and server transfers.</p>
 *
 * <p>This class coordinates several focused components:</p>
 * <ul>
 *   <li>{@link PlayerIdentity} - Immutable player information</li>
 *   <li>{@link SessionChannels} - QUIC channel management</li>
 *   <li>{@link SessionAuthState} - Authentication state during handshake</li>
 *   <li>{@link PacketSender} - Thread-safe packet sending</li>
 * </ul>
 *
 * @see SessionState for the session lifecycle states
 */
public final class ProxySession {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxySession.class);
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);

    // Immutable session identity
    private final long id;
    private final ProxyCore proxyCore;
    private final InetSocketAddress clientAddress;

    // Composed components
    private final SessionChannels channels;
    private final SessionAuthState authState;
    private final PacketSender packetSender;

    // Mutable state (thread-safe)
    private final AtomicReference<SessionState> state = new AtomicReference<>(SessionState.HANDSHAKING);
    private final AtomicReference<PlayerIdentity> identity = new AtomicReference<>(PlayerIdentity.unknown());
    private final AtomicReference<BackendServer> currentBackend = new AtomicReference<>();

    // Transfer flag
    private volatile boolean serverTransfer = false;

    // ==================== Construction ====================

    public ProxySession(@Nonnull ProxyCore proxyCore, @Nonnull QuicChannel clientChannel) {
        Objects.requireNonNull(proxyCore, "proxyCore");
        Objects.requireNonNull(clientChannel, "clientChannel");

        this.id = ID_GENERATOR.incrementAndGet();
        this.proxyCore = proxyCore;
        this.clientAddress = extractAddress(clientChannel);
        this.channels = new SessionChannels(id, clientChannel);
        this.authState = new SessionAuthState();
        this.packetSender = new PacketSender(id, channels);

        extractCertificate(clientChannel);
    }

    private InetSocketAddress extractAddress(QuicChannel channel) {
        SocketAddress addr = channel.remoteAddress();
        if (addr instanceof InetSocketAddress inet) {
            return inet;
        }
        return new InetSocketAddress("0.0.0.0", 0);
    }

    private void extractCertificate(QuicChannel channel) {
        X509Certificate cert = CertificateExtractor.extractClientCertificate(channel);
        if (cert != null) {
            String fingerprint = CertificateExtractor.computeCertificateFingerprint(cert);
            authState.setClientCertificate(cert, fingerprint);
            LOGGER.debug("Session {}: Certificate fingerprint: {}", id, fingerprint);
        }
    }

    // ==================== Identity ====================

    public long getSessionId() {
        return id;
    }

    @Nonnull
    public InetSocketAddress getClientAddress() {
        return clientAddress;
    }

    @Nonnull
    public PlayerIdentity getIdentity() {
        return identity.get();
    }

    @Nullable
    public UUID getPlayerUuid() {
        return identity.get().uuid();
    }

    @Nullable
    public String getUsername() {
        return identity.get().username();
    }

    @Nullable
    public String getPlayerName() {
        return getUsername();
    }

    @Nullable
    public String getProtocolHash() {
        return identity.get().protocolHash();
    }

    @Nullable
    public String getClientIdentityToken() {
        return identity.get().identityToken();
    }

    /**
     * Updates session identity from a Connect packet.
     */
    public void handleConnectPacket(@Nonnull Connect connect) {
        Objects.requireNonNull(connect, "connect");
        PlayerIdentity newIdentity = PlayerIdentity.fromConnect(connect);
        identity.set(newIdentity);
        authState.setOriginalConnect(connect);
        LOGGER.info("Session {} identified: {}", id, newIdentity);
    }

    // ==================== State ====================

    @Nonnull
    public SessionState getState() {
        return state.get();
    }

    public void setState(@Nonnull SessionState newState) {
        Objects.requireNonNull(newState, "newState");
        SessionState oldState = state.getAndSet(newState);
        if (oldState != newState) {
            LOGGER.debug("Session {} state: {} -> {}", id, oldState, newState);
        }
    }

    public boolean isActive() {
        return channels.isClientActive() && state.get() != SessionState.DISCONNECTED;
    }

    // ==================== Backend Server ====================

    @Nullable
    public BackendServer getCurrentBackend() {
        return currentBackend.get();
    }

    public void setCurrentBackend(@Nullable BackendServer backend) {
        currentBackend.set(backend);
    }

    @Nullable
    public String getCurrentServerName() {
        BackendServer backend = currentBackend.get();
        return backend != null ? backend.getName() : null;
    }

    // ==================== Transfer State ====================

    public boolean isServerTransfer() {
        return serverTransfer;
    }

    public void setServerTransfer(boolean transfer) {
        this.serverTransfer = transfer;
    }

    // ==================== Components Access ====================

    @Nonnull
    public ProxyCore getProxyCore() {
        return proxyCore;
    }

    @Nonnull
    public SessionChannels getChannels() {
        return channels;
    }

    @Nonnull
    public SessionAuthState getAuthState() {
        return authState;
    }

    // ==================== Channel Delegation ====================

    @Nonnull
    public QuicChannel getClientChannel() {
        return channels.clientChannel();
    }

    @Nullable
    public QuicStreamChannel getClientStream() {
        return channels.clientStream();
    }

    public void setClientStream(@Nullable QuicStreamChannel stream) {
        channels.setClientStream(stream);
    }

    @Nullable
    public QuicChannel getBackendChannel() {
        return channels.backendChannel();
    }

    public void setBackendChannel(@Nullable QuicChannel channel) {
        channels.setBackendChannel(channel);
    }

    @Nullable
    public QuicStreamChannel getBackendStream() {
        return channels.backendStream();
    }

    public void setBackendStream(@Nullable QuicStreamChannel stream) {
        channels.setBackendStream(stream);
    }

    // ==================== Auth State Delegation ====================

    @Nullable
    public X509Certificate getClientCertificate() {
        return authState.clientCertificate();
    }

    @Nullable
    public String getClientCertificateFingerprint() {
        return authState.certificateFingerprint();
    }

    @Nullable
    public Connect getOriginalConnect() {
        return authState.originalConnect();
    }

    public void setOriginalConnect(@Nullable Connect connect) {
        authState.setOriginalConnect(connect);
    }

    @Nullable
    public String getClientAuthGrant() {
        return authState.authorizationGrant();
    }

    public void setClientAuthGrant(@Nullable String grant) {
        authState.setAuthorizationGrant(grant);
    }

    @Nullable
    public String getClientAccessToken() {
        return authState.accessToken();
    }

    public void setClientAccessToken(@Nullable String token) {
        authState.setAccessToken(token);
    }

    // ==================== Packet Sending ====================

    /**
     * Sends a packet to the connected client.
     */
    public void sendToClient(@Nonnull Packet packet) {
        packetSender.sendToClient(packet);
    }

    /**
     * Sends data to the connected client.
     */
    public void sendToClient(@Nonnull Object obj) {
        if (obj instanceof Packet packet) {
            packetSender.sendToClient(packet);
        } else if (obj instanceof ByteBuf buf) {
            packetSender.sendToClient(buf);
        } else {
            LOGGER.warn("Session {}: Unsupported send type: {}", id, obj.getClass());
        }
    }

    /**
     * Sends a packet to the backend server.
     */
    public void sendToBackend(@Nonnull Packet packet) {
        packetSender.sendToBackend(packet);
    }

    /**
     * Sends data to the backend server.
     */
    public void sendToBackend(@Nonnull Object obj) {
        if (obj instanceof Packet packet) {
            packetSender.sendToBackend(packet);
        } else if (obj instanceof ByteBuf buf) {
            packetSender.sendToBackend(buf);
        } else {
            LOGGER.warn("Session {}: Unsupported send type: {}", id, obj.getClass());
        }
    }

    /**
     * Alias for {@link #sendToBackend(Packet)}.
     */
    public void sendToServer(@Nonnull Packet packet) {
        sendToBackend(packet);
    }

    /**
     * Sends a plain text chat message to the player.
     *
     * @param message the message to send
     */
    public void sendChatMessage(@Nonnull String message) {
        Objects.requireNonNull(message, "message");
        sendChatMessage(ChatMessageBuilder.create().white(message));
    }

    /**
     * Sends a formatted chat message to the player.
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * session.sendChatMessage(ChatMessageBuilder.create()
     *     .green("Success: ")
     *     .white("You have been teleported!"));
     * }</pre>
     *
     * @param builder the ChatMessageBuilder with the formatted message
     */
    public void sendChatMessage(@Nonnull ChatMessageBuilder builder) {
        Objects.requireNonNull(builder, "builder");
        sendToClient(ChatMessageConverter.toServerMessage(builder));
    }

    // ==================== Lifecycle ====================

    /**
     * Disconnects the session with a reason.
     */
    public void disconnect(@Nonnull String reason) {
        Objects.requireNonNull(reason, "reason");
        LOGGER.info("Session {} disconnecting: {}", id, reason);

        state.set(SessionState.DISCONNECTED);
        channels.closeAll();
        proxyCore.getSessionManager().removeSession(this);
    }

    /**
     * Closes all connections without logging a disconnect reason.
     */
    public void close() {
        state.set(SessionState.DISCONNECTED);
        channels.closeAll();
    }

    /**
     * Returns the player's ping in milliseconds, or -1 if unknown.
     */
    public long getPing() {
        // TODO: Implement ping tracking
        return -1;
    }

    // ==================== Server Transfer ====================

    /**
     * Switches this session to a different backend server.
     *
     * @param newBackend the target backend server
     * @return true if the transfer was initiated
     */
    public boolean switchToServer(@Nonnull BackendServer newBackend) {
        Objects.requireNonNull(newBackend, "newBackend");

        SessionState currentState = state.get();
        if (currentState != SessionState.CONNECTED) {
            LOGGER.warn("Session {}: Cannot switch - not connected (state: {})", id, currentState);
            return false;
        }

        BackendServer current = currentBackend.get();
        if (current != null && current.getName().equalsIgnoreCase(newBackend.getName())) {
            LOGGER.warn("Session {}: Already connected to {}", id, newBackend.getName());
            return false;
        }

        LOGGER.info("Session {}: Switching from {} to {}",
            id,
            current != null ? current.getName() : "none",
            newBackend.getName());

        setState(SessionState.TRANSFERRING);
        serverTransfer = true;
        channels.closeBackend();

        Connect connectPacket = createTransferConnect();
        proxyCore.getBackendConnector().reconnect(this, newBackend, connectPacket);

        return true;
    }

    /**
     * Transfers to a server by address.
     */
    public boolean transferTo(@Nonnull String host, int port) {
        Objects.requireNonNull(host, "host");

        // Find existing backend
        for (BackendServer backend : proxyCore.getConfig().getBackends()) {
            if (backend.getHost().equalsIgnoreCase(host) && backend.getPort() == port) {
                return switchToServer(backend);
            }
        }

        // Create temporary backend
        BackendServer temp = new BackendServer("temp-" + host + "-" + port, host, port, false);
        return switchToServer(temp);
    }

    private Connect createTransferConnect() {
        PlayerIdentity id = identity.get();
        Connect connect = new Connect();
        connect.uuid = id.uuid();
        connect.username = id.username();
        connect.protocolHash = id.protocolHash();
        connect.identityToken = id.identityToken();
        return connect;
    }

    // ==================== Object Methods ====================

    @Override
    public String toString() {
        PlayerIdentity id = identity.get();
        return String.format("ProxySession{id=%d, player=%s, uuid=%s, state=%s, address=%s}",
            this.id,
            id.username(),
            id.uuid(),
            state.get(),
            clientAddress);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProxySession that)) return false;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
