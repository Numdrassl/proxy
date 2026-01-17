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
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import me.internalizable.numdrassl.api.Numdrassl;
import me.internalizable.numdrassl.api.event.proxy.ProxyInitializeEvent;
import me.internalizable.numdrassl.api.event.proxy.ProxyShutdownEvent;
import me.internalizable.numdrassl.auth.ProxyAuthenticator;
import me.internalizable.numdrassl.config.ProxyConfig;
import me.internalizable.numdrassl.event.EventManager;
import me.internalizable.numdrassl.pipeline.ClientPacketHandler;
import me.internalizable.numdrassl.pipeline.ProxyPacketDecoder;
import me.internalizable.numdrassl.pipeline.ProxyPacketEncoder;
import me.internalizable.numdrassl.plugin.NumdrasslProxyServer;
import me.internalizable.numdrassl.session.ProxySession;
import me.internalizable.numdrassl.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Main Hytale QUIC Proxy Server.
 *
 * <p>Authentication flow:</p>
 * <ul>
 *   <li><b>Client → Proxy</b>: Standard Hytale authentication (JWT/mTLS).
 *       The proxy authenticates with Hytale session service as a genuine server.</li>
 *   <li><b>Proxy → Backend</b>: Secret-based authentication (HMAC referral data).
 *       The backend trusts the proxy via a shared secret, no Hytale auth needed.</li>
 * </ul>
 */
public class ProxyServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyServer.class);

    private final ProxyConfig config;
    private final SessionManager sessionManager;
    private final EventManager eventManager;
    private final BackendConnector backendConnector;
    private final ProxyAuthenticator authenticator;
    private final ReferralManager referralManager;
    private NumdrasslProxyServer apiServer;

    private EventLoopGroup group;
    private Channel serverChannel;
    private volatile boolean running = false;

    public ProxyServer(@Nonnull ProxyConfig config) {
        this.config = config;
        this.sessionManager = new SessionManager();
        this.eventManager = new EventManager();
        this.backendConnector = new BackendConnector(this);
        this.referralManager = new ReferralManager(this);
        this.authenticator = new ProxyAuthenticator(
            config.getCertificatePath(),
            config.getPrivateKeyPath(),
            "config/proxy_credentials.json"
        );
    }

    /**
     * Start the proxy server
     */
    public void start() throws Exception {
        if (running) {
            throw new IllegalStateException("Proxy server is already running");
        }

        LOGGER.info("Starting Hytale QUIC Proxy Server...");
        LOGGER.info("Bind address: {}:{}", config.getBindAddress(), config.getBindPort());
        LOGGER.info("Debug mode: {}", config.isDebugMode());

        // Initialize the proxy authenticator (for client-facing auth)
        authenticator.initialize();
        if (authenticator.isAuthenticated()) {
            LOGGER.info("Proxy authenticated as: {} ({})",
                authenticator.getProfileUsername(), authenticator.getProfileUuid());
        } else {
            LOGGER.warn("Proxy is NOT authenticated with Hytale!");
            LOGGER.warn("Clients will not be able to authenticate.");
            LOGGER.warn("Use 'auth login' command to authenticate the proxy.");
        }

        LOGGER.info("Backend authentication: Secret-based (HMAC referral)");

        // Load SSL context
        QuicSslContext sslContext = createSslContext();

        group = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors());

        boolean debugMode = config.isDebugMode();

        // Create the QUIC server codec
        ChannelHandler serverCodec = new QuicServerCodecBuilder()
            .sslContext(sslContext)
            .maxIdleTimeout(config.getConnectionTimeoutSeconds(), TimeUnit.SECONDS)
            .initialMaxData(10000000)
            .initialMaxStreamDataBidirectionalLocal(1000000)
            .initialMaxStreamDataBidirectionalRemote(1000000)
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
                    // Get the parent QuicChannel to find the session
                    QuicChannel quicChannel = (QuicChannel) ch.parent();
                    ProxySession session = sessionManager.getSession(quicChannel);

                    if (session == null) {
                        LOGGER.error("Received stream on unknown QUIC channel");
                        ch.close();
                        return;
                    }

                    // Set up the pipeline for this stream
                    session.setClientStream(ch);
                    ch.pipeline().addLast(new ProxyPacketDecoder("client", debugMode));
                    ch.pipeline().addLast(new ProxyPacketEncoder("client", debugMode));
                    ch.pipeline().addLast(new ClientPacketHandler(ProxyServer.this, session));

                    LOGGER.debug("Session {}: Client stream initialized", session.getSessionId());
                }
            })
            .build();

        // Start the server
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
            .channel(NioDatagramChannel.class)
            .handler(serverCodec);

        InetSocketAddress bindAddress = new InetSocketAddress(config.getBindAddress(), config.getBindPort());
        serverChannel = bootstrap.bind(bindAddress).sync().channel();

        running = true;
        LOGGER.info("Hytale QUIC Proxy Server started on {}:{}", config.getBindAddress(), config.getBindPort());
        LOGGER.info("Configured backends:");
        for (var backend : config.getBackends()) {
            LOGGER.info("  - {} -> {}:{} {}",
                backend.getName(), backend.getHost(), backend.getPort(),
                backend.isDefaultServer() ? "(default)" : "");
        }

        // Initialize the API server and load plugins
        initializeApiServer();
    }

    /**
     * Initialize the API server, load plugins, and fire ProxyInitializeEvent.
     */
    private void initializeApiServer() {
        LOGGER.info("Initializing API server and loading plugins...");

        // Create the API server wrapper
        apiServer = new NumdrasslProxyServer(this);

        // Set the static API accessor
        Numdrassl.setServer(apiServer);

        // Initialize (loads and enables plugins)
        apiServer.initialize();

        // Fire the ProxyInitializeEvent
        apiServer.getNumdrasslEventManager().fireSync(new ProxyInitializeEvent());

        LOGGER.info("API server initialized, {} plugin(s) loaded",
            apiServer.getPluginManager().getPlugins().size());
    }

    private void handleNewConnection(QuicChannel quicChannel) {
        if (sessionManager.getSessionCount() >= config.getMaxConnections()) {
            LOGGER.warn("Max connections reached, rejecting new connection");
            quicChannel.close();
            return;
        }

        ProxySession session = new ProxySession(this, quicChannel);
        sessionManager.addSession(session);

        LOGGER.info("New connection from {} (Session {})",
            quicChannel.remoteAddress(), session.getSessionId());

        // Notify listeners
        eventManager.dispatchSessionCreated(session);
    }

    private QuicSslContext createSslContext() throws Exception {
        File certFile = new File(config.getCertificatePath());
        File keyFile = new File(config.getPrivateKeyPath());

        if (!certFile.exists() || !keyFile.exists()) {
            LOGGER.info("SSL certificates not found, generating self-signed certificate...");
            CertificateGenerator.generateSelfSigned(
                config.getCertificatePath(),
                config.getPrivateKeyPath()
            );
        }

        // Initialize the backend connector with the same certificate
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

    /**
     * Stop the proxy server
     */
    public void stop() {
        if (!running) {
            return;
        }

        LOGGER.info("Stopping Hytale QUIC Proxy Server...");

        running = false;

        // Fire ProxyShutdownEvent and shutdown API server
        if (apiServer != null) {
            apiServer.getNumdrasslEventManager().fireSync(new ProxyShutdownEvent());
            apiServer.shutdownApi();
        }

        // Close all sessions
        sessionManager.closeAll();

        // Shutdown backend connector
        backendConnector.shutdown();

        // Shutdown referral manager
        referralManager.shutdown();

        // Shutdown authenticator
        authenticator.shutdown();

        // Close server channel
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }

        // Shutdown event loop
        if (group != null) {
            group.shutdownGracefully().syncUninterruptibly();
        }

        LOGGER.info("Hytale QUIC Proxy Server stopped");
    }

    public boolean isRunning() {
        return running;
    }

    @Nullable
    public ProxyAuthenticator getAuthenticator() {
        return authenticator;
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
    public EventManager getEventManager() {
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

    @Nullable
    public NumdrasslProxyServer getApiServer() {
        return apiServer;
    }
}

