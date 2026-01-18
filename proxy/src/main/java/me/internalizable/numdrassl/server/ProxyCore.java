package me.internalizable.numdrassl.server;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicCongestionControlAlgorithm;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import me.internalizable.numdrassl.api.Numdrassl;
import me.internalizable.numdrassl.api.event.proxy.ProxyInitializeEvent;
import me.internalizable.numdrassl.api.event.proxy.ProxyShutdownEvent;
import me.internalizable.numdrassl.auth.ProxyAuthenticator;
import me.internalizable.numdrassl.config.ProxyConfig;
import me.internalizable.numdrassl.event.packet.PacketEventManager;
import me.internalizable.numdrassl.pipeline.ClientPacketHandler;
import me.internalizable.numdrassl.pipeline.codec.ProxyPacketDecoder;
import me.internalizable.numdrassl.pipeline.codec.ProxyPacketEncoder;
import me.internalizable.numdrassl.plugin.NumdrasslProxy;
import me.internalizable.numdrassl.server.ssl.CertificateGenerator;
import me.internalizable.numdrassl.server.transfer.PlayerTransfer;
import me.internalizable.numdrassl.server.transfer.ReferralManager;
import me.internalizable.numdrassl.session.ProxySession;
import me.internalizable.numdrassl.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Core implementation of the Hytale QUIC Proxy Server.
 *
 * <p>Handles low-level QUIC networking, SSL/TLS configuration, session management,
 * and connection lifecycle. This is the internal engine powering the proxy.</p>
 *
 * <h2>Authentication Flow</h2>
 * <ul>
 *   <li><b>Client → Proxy</b>: Standard Hytale authentication (JWT/mTLS).
 *       The proxy authenticates as a genuine Hytale server.</li>
 *   <li><b>Proxy → Backend</b>: Secret-based authentication (HMAC referral).
 *       Backends trust the proxy via a shared secret.</li>
 * </ul>
 *
 * @see NumdrasslProxy for the public API implementation
 */
public final class ProxyCore {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyCore.class);

    // Configuration
    private final ProxyConfig config;

    // Core components
    private final SessionManager sessionManager;
    private final PacketEventManager eventManager;
    private final BackendConnector backendConnector;
    private final ProxyAuthenticator authenticator;
    private final ReferralManager referralManager;
    private final PlayerTransfer playerTransfer;

    // Networking
    private EventLoopGroup eventLoopGroup;
    private Channel serverChannel;

    // API layer
    private NumdrasslProxy apiProxy;

    // State
    private volatile boolean running = false;

    // ==================== Construction ====================

    public ProxyCore(@Nonnull ProxyConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.sessionManager = new SessionManager();
        this.eventManager = new PacketEventManager();
        this.backendConnector = new BackendConnector(this);
        this.referralManager = new ReferralManager(this);
        this.playerTransfer = new PlayerTransfer(this);
        this.authenticator = createAuthenticator();
    }

    private ProxyAuthenticator createAuthenticator() {
        return new ProxyAuthenticator(
            config.getCertificatePath(),
            config.getPrivateKeyPath(),
            "config/proxy_credentials.json"
        );
    }

    // ==================== Lifecycle ====================

    /**
     * Starts the proxy server.
     *
     * @throws Exception if startup fails
     * @throws IllegalStateException if already running
     */
    public void start() throws Exception {
        if (running) {
            throw new IllegalStateException("Proxy server is already running");
        }

        logStartupInfo();
        initializeAuthenticator();

        QuicSslContext sslContext = createSslContext();
        startNetworking(sslContext);
        initializeApi();

        running = true;
    }

    /**
     * Stops the proxy server.
     */
    public void stop() {
        if (!running) {
            return;
        }

        LOGGER.info("Stopping proxy server...");
        running = false;

        shutdownApi();
        shutdownComponents();

        LOGGER.info("Proxy server stopped");
    }

    private void logStartupInfo() {
        LOGGER.info("Starting Hytale QUIC Proxy Server...");
        LOGGER.info("Bind: {}:{}", config.getBindAddress(), config.getBindPort());
        LOGGER.info("Debug mode: {}", config.isDebugMode());
        LOGGER.info("Backend auth: Secret-based (HMAC referral)");
    }

    // ==================== Authentication ====================

    private void initializeAuthenticator() {
        authenticator.initialize();

        if (authenticator.isAuthenticated()) {
            LOGGER.info("Authenticated as: {} ({})",
                authenticator.getProfileUsername(),
                authenticator.getProfileUuid());
        } else {
            LOGGER.warn("Proxy is NOT authenticated with Hytale!");
            LOGGER.warn("Clients will not be able to authenticate.");
            LOGGER.warn("Use 'auth login' to authenticate the proxy.");
        }
    }

    // ==================== Networking ====================

    private void startNetworking(QuicSslContext sslContext) throws InterruptedException {
        int threads = Runtime.getRuntime().availableProcessors();
        eventLoopGroup = new NioEventLoopGroup(threads);

        ChannelHandler serverCodec = buildServerCodec(sslContext);

        Bootstrap bootstrap = new Bootstrap()
            .group(eventLoopGroup)
            .channel(NioDatagramChannel.class)
            .handler(serverCodec);

        InetSocketAddress bindAddress = new InetSocketAddress(
            config.getBindAddress(),
            config.getBindPort()
        );

        serverChannel = bootstrap.bind(bindAddress).sync().channel();

        LOGGER.info("Proxy started on {}:{}", config.getBindAddress(), config.getBindPort());
        logBackendServers();
    }

    private ChannelHandler buildServerCodec(QuicSslContext sslContext) {
        boolean debugMode = config.isDebugMode();

        return new QuicServerCodecBuilder()
            .sslContext(sslContext)
            .congestionControlAlgorithm(QuicCongestionControlAlgorithm.BBR)
            .maxIdleTimeout(config.getConnectionTimeoutSeconds(), TimeUnit.SECONDS)
            .initialMaxData(10_000_000)
            .initialMaxStreamDataBidirectionalLocal(1_000_000)
            .initialMaxStreamDataBidirectionalRemote(1_000_000)
            .initialMaxStreamsBidirectional(100)
            .initialMaxStreamsUnidirectional(100)
            .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
            .handler(new ChannelInitializer<QuicChannel>() {
                @Override
                protected void initChannel(QuicChannel ch) {
                    handleNewConnection(ch);
                }
            })
            .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                @Override
                protected void initChannel(QuicStreamChannel ch) {
                    initializeClientStream(ch, debugMode);
                }
            })
            .build();
    }

    private void handleNewConnection(QuicChannel quicChannel) {
        if (sessionManager.getSessionCount() >= config.getMaxConnections()) {
            LOGGER.warn("Max connections reached, rejecting connection");
            quicChannel.close();
            return;
        }

        ProxySession session = new ProxySession(this, quicChannel);
        sessionManager.addSession(session);

        LOGGER.info("New connection from {} (Session {})",
            quicChannel.remoteAddress(), session.getSessionId());

        eventManager.dispatchSessionCreated(session);
    }

    private void initializeClientStream(QuicStreamChannel ch, boolean debugMode) {
        QuicChannel quicChannel = (QuicChannel) ch.parent();
        ProxySession session = sessionManager.getSession(quicChannel);

        if (session == null) {
            LOGGER.error("Stream received on unknown QUIC channel");
            ch.close();
            return;
        }

        session.setClientStream(ch);
        ch.pipeline().addLast(new ProxyPacketDecoder("client", debugMode));
        ch.pipeline().addLast(new ProxyPacketEncoder("client", debugMode));
        ch.pipeline().addLast(new ClientPacketHandler(this, session));

        LOGGER.debug("Session {}: Client stream initialized", session.getSessionId());
    }

    private void logBackendServers() {
        LOGGER.info("Configured backends:");
        for (var backend : config.getBackends()) {
            String suffix = backend.isDefaultServer() ? " (default)" : "";
            LOGGER.info("  - {} -> {}:{}{}",
                backend.getName(), backend.getHost(), backend.getPort(), suffix);
        }
    }

    // ==================== SSL ====================

    private QuicSslContext createSslContext() throws Exception {
        ensureCertificatesExist();
        backendConnector.initSslContext(config.getCertificatePath(), config.getPrivateKeyPath());

        return QuicSslContextBuilder.forServer(
                new File(config.getPrivateKeyPath()),
                null,
                new File(config.getCertificatePath()))
            .applicationProtocols("hytale/1")
            .clientAuth(io.netty.handler.ssl.ClientAuth.REQUIRE)
            .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
            .build();
    }

    private void ensureCertificatesExist() throws Exception {
        if (!CertificateGenerator.certificatesExist(
                config.getCertificatePath(),
                config.getPrivateKeyPath())) {

            LOGGER.info("Certificates not found, generating self-signed...");
            CertificateGenerator.generateSelfSigned(
                config.getCertificatePath(),
                config.getPrivateKeyPath()
            );
        }
    }

    // ==================== API ====================

    private void initializeApi() {
        LOGGER.info("Initializing API and loading plugins...");

        apiProxy = new NumdrasslProxy(this);
        Numdrassl.setServer(apiProxy);
        apiProxy.initialize();

        apiProxy.getNumdrasslEventManager().fireSync(new ProxyInitializeEvent());

        LOGGER.info("API initialized, {} plugin(s) loaded",
            apiProxy.getPluginManager().getPlugins().size());
    }

    private void shutdownApi() {
        if (apiProxy != null) {
            apiProxy.getNumdrasslEventManager().fireSync(new ProxyShutdownEvent());
            apiProxy.shutdownApi();
        }
    }

    private void shutdownComponents() {
        sessionManager.closeAll();
        backendConnector.shutdown();
        referralManager.shutdown();
        authenticator.shutdown();

        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }

        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully().syncUninterruptibly();
        }
    }

    // ==================== Accessors ====================

    public boolean isRunning() {
        return running;
    }

    @Nonnull
    public ProxyConfig getConfig() {
        return config;
    }

    @Nonnull
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    @Nonnull
    public PacketEventManager getEventManager() {
        return eventManager;
    }

    @Nonnull
    public BackendConnector getBackendConnector() {
        return backendConnector;
    }

    @Nonnull
    public ReferralManager getReferralManager() {
        return referralManager;
    }

    @Nonnull
    public PlayerTransfer getPlayerTransfer() {
        return playerTransfer;
    }

    @Nullable
    public ProxyAuthenticator getAuthenticator() {
        return authenticator;
    }

    @Nullable
    public NumdrasslProxy getApiProxy() {
        return apiProxy;
    }
}
