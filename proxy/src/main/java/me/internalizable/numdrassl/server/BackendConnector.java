package me.internalizable.numdrassl.server;

import com.hypixel.hytale.protocol.packets.connection.Connect;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicClientCodecBuilder;
import io.netty.incubator.codec.quic.QuicCongestionControlAlgorithm;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import me.internalizable.numdrassl.common.SecretMessageUtil;
import me.internalizable.numdrassl.config.BackendServer;
import me.internalizable.numdrassl.pipeline.BackendPacketHandler;
import me.internalizable.numdrassl.pipeline.codec.ProxyPacketDecoder;
import me.internalizable.numdrassl.pipeline.codec.ProxyPacketEncoder;
import me.internalizable.numdrassl.api.chat.ChatMessageBuilder;
import me.internalizable.numdrassl.session.ProxySession;
import me.internalizable.numdrassl.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Handles QUIC connections to backend Hytale servers.
 *
 * <p>Manages the client (outbound) side of the proxy's connection to backend servers.
 * Uses BBR congestion control and secret-based authentication via HMAC-signed referral data.</p>
 */
public final class BackendConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackendConnector.class);
    private static final int THREAD_COUNT = 2;

    private final ProxyCore proxyCore;
    private final EventLoopGroup group;
    private QuicSslContext sslContext;
    private byte[] proxySecret;

    // ==================== Construction ====================

    public BackendConnector(@Nonnull ProxyCore proxyCore) {
        this.proxyCore = Objects.requireNonNull(proxyCore, "proxyCore");
        this.group = new NioEventLoopGroup(THREAD_COUNT);
        initProxySecret();
    }

    private void initProxySecret() {
        String envSecret = System.getenv("NUMDRASSL_SECRET");
        if (envSecret != null && !envSecret.isEmpty()) {
            this.proxySecret = envSecret.getBytes(StandardCharsets.UTF_8);
            LOGGER.info("Using proxy secret from NUMDRASSL_SECRET environment variable");
            return;
        }

        String configSecret = proxyCore.getConfig().getProxySecret();
        if (configSecret != null && !configSecret.isEmpty()) {
            this.proxySecret = configSecret.getBytes(StandardCharsets.UTF_8);
            LOGGER.info("Using proxy secret from config");
            return;
        }

        this.proxySecret = generateRandomSecret();
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(proxySecret);
        LOGGER.warn("No proxy secret configured! Generated: {}", encoded);
        LOGGER.warn("Configure this in config.yml or NUMDRASSL_SECRET env var");
    }

    private byte[] generateRandomSecret() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        return secret;
    }

    // ==================== SSL Context ====================

    /**
     * Initializes the SSL context using the proxy's certificate.
     *
     * @param certPath path to the certificate file
     * @param keyPath path to the private key file
     */
    public void initSslContext(@Nonnull String certPath, @Nonnull String keyPath) {
        Objects.requireNonNull(certPath, "certPath");
        Objects.requireNonNull(keyPath, "keyPath");

        File certFile = new File(certPath);
        File keyFile = new File(keyPath);

        validateCertificateFiles(certFile, keyFile);
        logCertificateInfo(certFile);

        try {
            this.sslContext = QuicSslContextBuilder.forClient()
                .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                .keyManager(keyFile, null, certFile)
                .applicationProtocols("hytale/1")
                .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create SSL context", e);
        }
    }

    private void validateCertificateFiles(File certFile, File keyFile) {
        if (!certFile.exists() || !keyFile.exists()) {
            throw new IllegalStateException(
                "Certificate files not found: " + certFile.getPath() + ", " + keyFile.getPath()
            );
        }
        LOGGER.info("Using proxy certificate for backend connections: {}", certFile.getPath());
    }

    private void logCertificateInfo(File certFile) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(new FileInputStream(certFile));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(cert.getEncoded());
            String fingerprint = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            LOGGER.info("Certificate fingerprint: {}", fingerprint);
        } catch (Exception e) {
            LOGGER.warn("Could not compute certificate fingerprint", e);
        }
    }

    // ==================== Connection ====================

    /**
     * Connects a session to a backend server.
     */
    public void connect(
            @Nonnull ProxySession session,
            @Nonnull BackendServer backend,
            @Nonnull Connect connectPacket) {

        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(connectPacket, "connectPacket");

        BackendServer targetBackend = firePreConnectEvent(session, backend);
        if (targetBackend == null) {
            return; // Connection denied by plugin
        }

        initiateConnection(session, targetBackend, connectPacket, false);
    }

    /**
     * Reconnects a session to a new backend server (for server transfers).
     */
    public void reconnect(
            @Nonnull ProxySession session,
            @Nonnull BackendServer backend,
            @Nonnull Connect connectPacket) {

        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(connectPacket, "connectPacket");

        BackendServer targetBackend = firePreConnectEvent(session, backend);
        if (targetBackend == null) {
            handleTransferDenied(session, backend.getName());
            return;
        }

        initiateConnection(session, targetBackend, connectPacket, true);
    }

    private BackendServer firePreConnectEvent(ProxySession session, BackendServer backend) {
        var apiProxy = proxyCore.getApiProxy();
        if (apiProxy == null) {
            return backend;
        }

        var eventBridge = apiProxy.getEventBridge();
        if (eventBridge == null) {
            return backend;
        }

        var result = eventBridge.fireServerPreConnectEvent(session, backend);
        if (result == null || !result.isAllowed()) {
            String reason = result != null ? result.getDenyReason() : "Connection denied";
            LOGGER.info("Session {}: Connection denied by plugin: {}", session.getSessionId(), reason);
            session.disconnect(reason != null ? reason : "Connection denied");
            return null;
        }

        return result.getTargetServer() != null ? result.getTargetServer() : backend;
    }

    private void handleTransferDenied(ProxySession session, String serverName) {
        session.setState(SessionState.CONNECTED);
        session.setServerTransfer(false);
        sendTransferFailedMessage(session, serverName);
    }

    private void initiateConnection(
            ProxySession session,
            BackendServer backend,
            Connect connectPacket,
            boolean isReconnect) {

        String action = isReconnect ? "Reconnecting" : "Connecting";
        LOGGER.info("Session {}: {} to backend {} ({}:{})",
            session.getSessionId(), action, backend.getName(), backend.getHost(), backend.getPort());

        session.setCurrentBackend(backend);

        try {
            Bootstrap bootstrap = createBootstrap();
            InetSocketAddress address = new InetSocketAddress(backend.getHost(), backend.getPort());
            Channel datagramChannel = bootstrap.bind(0).sync().channel();

            connectQuicChannel(session, datagramChannel, address, backend, connectPacket, isReconnect);
        } catch (Exception e) {
            LOGGER.error("Session {}: Error connecting to backend", session.getSessionId(), e);
            handleConnectionFailure(session, backend.getName(), isReconnect);
        }
    }

    private Bootstrap createBootstrap() {
        ChannelHandler codec = new QuicClientCodecBuilder()
            .sslContext(sslContext)
            .congestionControlAlgorithm(QuicCongestionControlAlgorithm.BBR)
            .maxIdleTimeout(proxyCore.getConfig().getConnectionTimeoutSeconds(), TimeUnit.SECONDS)
            .initialMaxData(10_000_000)
            .initialMaxStreamDataBidirectionalLocal(1_000_000)
            .initialMaxStreamDataBidirectionalRemote(1_000_000)
            .initialMaxStreamsBidirectional(100)
            .initialMaxStreamsUnidirectional(100)
            .build();

        return new Bootstrap()
            .group(group)
            .channel(NioDatagramChannel.class)
            .handler(codec);
    }

    private void connectQuicChannel(
            ProxySession session,
            Channel datagramChannel,
            InetSocketAddress address,
            BackendServer backend,
            Connect connectPacket,
            boolean isReconnect) {

        boolean debugMode = proxyCore.getConfig().isDebugMode();

        QuicChannel.newBootstrap(datagramChannel)
            .streamHandler(createStreamHandler(session, debugMode))
            .remoteAddress(address)
            .connect()
            .addListener(future -> {
                if (future.isSuccess()) {
                    QuicChannel quicChannel = (QuicChannel) future.getNow();
                    onConnected(session, quicChannel, backend, connectPacket, isReconnect, debugMode);
                } else {
                    LOGGER.error("Session {}: Failed to connect to backend",
                        session.getSessionId(), future.cause());
                    handleConnectionFailure(session, backend.getName(), isReconnect);
                }
            });
    }

    private ChannelInitializer<QuicStreamChannel> createStreamHandler(ProxySession session, boolean debugMode) {
        return new ChannelInitializer<>() {
            @Override
            protected void initChannel(QuicStreamChannel ch) {
                ch.pipeline().addLast(new ProxyPacketDecoder("backend-server", debugMode));
                ch.pipeline().addLast(new ProxyPacketEncoder("backend-server", debugMode));
                ch.pipeline().addLast(new BackendPacketHandler(proxyCore, session));
            }
        };
    }

    private void onConnected(
            ProxySession session,
            QuicChannel quicChannel,
            BackendServer backend,
            Connect connectPacket,
            boolean isReconnect,
            boolean debugMode) {

        LOGGER.info("Session {}: Connected to backend {} QUIC channel",
            session.getSessionId(), backend.getName());
        session.setBackendChannel(quicChannel);

        createBackendStream(session, quicChannel, backend, connectPacket, isReconnect, debugMode);
    }

    private void createBackendStream(
            ProxySession session,
            QuicChannel quicChannel,
            BackendServer backend,
            Connect connectPacket,
            boolean isReconnect,
            boolean debugMode) {

        quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, createStreamHandler(session, debugMode))
            .addListener(future -> {
                if (future.isSuccess()) {
                    QuicStreamChannel stream = (QuicStreamChannel) future.getNow();
                    onStreamCreated(session, stream, backend, connectPacket, isReconnect);
                } else {
                    LOGGER.error("Session {}: Failed to create backend stream",
                        session.getSessionId(), future.cause());
                    handleConnectionFailure(session, backend.getName(), isReconnect);
                }
            });
    }

    private void onStreamCreated(
            ProxySession session,
            QuicStreamChannel stream,
            BackendServer backend,
            Connect connectPacket,
            boolean isReconnect) {

        session.setBackendStream(stream);
        session.setCurrentBackend(backend);

        LOGGER.info("Session {}: Backend stream created for {}, forwarding Connect packet",
            session.getSessionId(), backend.getName());

        Connect signedConnect = createSignedConnectPacket(session, connectPacket);
        stream.writeAndFlush(signedConnect);
        session.setState(SessionState.AUTHENTICATING);

        if (isReconnect) {
            sendTransferSuccessMessage(session, backend.getName());
        }
    }

    private void handleConnectionFailure(ProxySession session, String serverName, boolean isReconnect) {
        if (isReconnect) {
            sendTransferFailedMessage(session, serverName);
        } else {
            session.disconnect("Failed to connect to backend server");
        }
    }

    // ==================== Signed Connect Packet ====================

    private Connect createSignedConnectPacket(ProxySession session, Connect original) {
        String backendName = session.getCurrentBackend() != null
            ? session.getCurrentBackend().getName()
            : "unknown";

        byte[] referralData = SecretMessageUtil.createPlayerInfoReferral(
            session.getPlayerUuid(),
            session.getUsername(),
            backendName,
            session.getClientAddress(),
            proxySecret
        );

        LOGGER.debug("Session {}: Created signed referral ({} bytes) for {}",
            session.getSessionId(), referralData.length, backendName);

        Connect proxyConnect = new Connect(original);
        proxyConnect.referralData = referralData;
        return proxyConnect;
    }

    // ==================== Transfer Messages ====================

    private void sendTransferSuccessMessage(ProxySession session, String serverName) {
        session.sendChatMessage(
            ChatMessageBuilder.create()
                .gold("Connecting to ")
                .bold(serverName, ChatMessageBuilder.Colors.GREEN)
                .gold("...")
        );
    }

    private void sendTransferFailedMessage(ProxySession session, String serverName) {
        if (session.getState() == SessionState.TRANSFERRING) {
            session.setState(SessionState.CONNECTED);
        }

        session.sendChatMessage(
            ChatMessageBuilder.create()
                .red("Failed to connect to ")
                .bold(serverName, ChatMessageBuilder.Colors.GOLD)
                .red(". Please try again later.")
        );
    }

    // ==================== Accessors ====================

    /**
     * Gets the proxy secret for HMAC signing.
     */
    @Nonnull
    public byte[] getProxySecret() {
        return proxySecret.clone();
    }

    // ==================== Lifecycle ====================

    /**
     * Shuts down the backend connector.
     */
    public void shutdown() {
        group.shutdownGracefully();
        LOGGER.debug("BackendConnector shut down");
    }
}

