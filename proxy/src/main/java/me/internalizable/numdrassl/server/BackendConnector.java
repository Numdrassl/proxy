package me.internalizable.numdrassl.server;

import com.hypixel.hytale.protocol.HostAddress;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.connection.Connect;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
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
import me.internalizable.numdrassl.event.packet.ProxyPing;
import me.internalizable.numdrassl.event.packet.ProxyPong;
import me.internalizable.numdrassl.pipeline.BackendPacketHandler;
import me.internalizable.numdrassl.pipeline.codec.ProxyPacketDecoder;
import me.internalizable.numdrassl.pipeline.codec.ProxyPacketEncoder;
import me.internalizable.numdrassl.profiling.ProxyMetrics;
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
import java.util.concurrent.CompletableFuture;
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
                .applicationProtocols(
                    "hytale/10", "hytale/9", "hytale/8", "hytale/7", "hytale/6",
                    "hytale/5", "hytale/4", "hytale/3", "hytale/2", "hytale/1"
                )
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
        ProxyMetrics.getInstance().recordBackendConnection(backend.getName());

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
        LOGGER.debug("Session {}: Writing Connect to backend stream - final check: protocolCrc={}, buildNumber={}, clientVersion='{}'",
            session.getSessionId(), signedConnect.protocolCrc, signedConnect.protocolBuildNumber, signedConnect.clientVersion);
        stream.writeAndFlush(signedConnect);
        session.setState(SessionState.AUTHENTICATING);

        if (isReconnect) {
            sendTransferSuccessMessage(session, backend.getName());
        }
    }

    private void handleConnectionFailure(ProxySession session, String serverName, boolean isReconnect) {
        ProxyMetrics.getInstance().recordBackendConnectionFailure(serverName);
        if (isReconnect) {
            sendTransferFailedMessage(session, serverName);
        } else {
            session.disconnect("Failed to connect to backend server");
        }
    }

    // ==================== Signed Connect Packet ====================

    private Connect createSignedConnectPacket(ProxySession session, Connect original) {
        LOGGER.debug("Session {}: Original Connect packet - protocolCrc={}, buildNumber={}, clientVersion='{}'",
            session.getSessionId(), original.protocolCrc, original.protocolBuildNumber, original.clientVersion);

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
        proxyConnect.referralSource = createReferralSource();

        LOGGER.debug("Session {}: Sending Connect to backend - protocolCrc={}, buildNumber={}, clientVersion='{}', uuid={}, username={}, referralSource={}:{}",
            session.getSessionId(), proxyConnect.protocolCrc, proxyConnect.protocolBuildNumber,
            proxyConnect.clientVersion, proxyConnect.uuid, proxyConnect.username,
            proxyConnect.referralSource != null ? proxyConnect.referralSource.host : "null",
            proxyConnect.referralSource != null ? proxyConnect.referralSource.port : 0);

        return proxyConnect;
    }

    /**
     * Creates the referral source address pointing to this proxy.
     * This allows the backend to know where the player was referred from.
     */
    private HostAddress createReferralSource() {
        String host = proxyCore.getConfig().getPublicAddress();
        if (host == null || host.isEmpty()) {
            host = proxyCore.getConfig().getBindAddress();
        }
        int port = proxyCore.getConfig().getBindPort();
        return new HostAddress(host, (short) port);
    }

    public CompletableFuture<Boolean> checkBackendAlive(BackendServer backend, long timeoutMs) {
        Objects.requireNonNull(backend, "backend");
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        InetSocketAddress address = new InetSocketAddress(backend.getHost(), backend.getPort());

        Bootstrap bootstrap = createBootstrap();
        bootstrap.bind(0).addListener((ChannelFutureListener) bindFuture -> {
            if (!bindFuture.isSuccess()) {
                future.complete(false);
                return;
            }

            Channel datagramChannel = bindFuture.channel();

            datagramChannel.eventLoop().schedule(() -> {
                if (future.complete(false)) {
                    datagramChannel.close();
                }
            }, timeoutMs, TimeUnit.MILLISECONDS);

            QuicChannel.newBootstrap(datagramChannel)
                    .remoteAddress(address)
                    .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                        @Override
                        protected void initChannel(QuicStreamChannel ch) throws Exception {
                        }
                    })
                    .connect()
                    .addListener(connectFuture -> {
                        if (!connectFuture.isSuccess()) {
                            if (future.complete(false)) {
                                datagramChannel.close();
                            }
                            return;
                        }

                        QuicChannel quicChannel = (QuicChannel) connectFuture.getNow();

                        quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, new ChannelInitializer<QuicStreamChannel>() {
                            @Override
                            protected void initChannel(QuicStreamChannel ch) {
                                boolean debugMode = proxyCore.getConfig().isDebugMode();

                                ch.pipeline().addLast(new ProxyPacketDecoder("backend-ping", debugMode));
                                ch.pipeline().addLast(new ProxyPacketEncoder("backend-ping", debugMode));

                                ch.pipeline().addLast(new SimpleChannelInboundHandler<Packet>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
                                        if (packet instanceof ProxyPong) {
                                            if (!future.isDone()) {
                                                future.complete(true);
                                            }
                                            ctx.close();
                                            return;
                                        }
                                    }
                                });
                            }
                        }).addListener(streamFuture -> {
                            if (!streamFuture.isSuccess()) {
                                if (future.complete(false)) {
                                    quicChannel.eventLoop().execute(() -> quicChannel.close());
                                    datagramChannel.close();
                                }
                                return;
                            }

                            QuicStreamChannel stream = (QuicStreamChannel) streamFuture.getNow();

                            ProxyPing ping = new ProxyPing();
                            ping.timestamp = System.currentTimeMillis();
                            ping.nonce = new SecureRandom().nextLong();

                            stream.writeAndFlush(ping).addListener(writeFuture -> {
                                if (!writeFuture.isSuccess()) {
                                    if (future.complete(false)) {
                                        stream.close();
                                    }
                                }
                            });
                        });

                        future.whenComplete((ok, err) -> {
                            quicChannel.eventLoop().execute(quicChannel::close);
                            datagramChannel.close();
                        });
                    });
        });

        return future;
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

