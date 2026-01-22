package me.internalizable.numdrassl.plugin.messaging;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicClientCodecBuilder;
import io.netty.incubator.codec.quic.QuicCongestionControlAlgorithm;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import me.internalizable.numdrassl.api.event.connection.PluginMessageEvent;
import me.internalizable.numdrassl.api.plugin.messaging.ChannelIdentifier;
import me.internalizable.numdrassl.api.server.RegisteredServer;
import me.internalizable.numdrassl.common.PluginMessagePacket;
import me.internalizable.numdrassl.common.SecretMessageUtil;
import me.internalizable.numdrassl.config.BackendServer;
import me.internalizable.numdrassl.config.ProxyConfig;
import me.internalizable.numdrassl.event.api.NumdrasslEventManager;
import me.internalizable.numdrassl.plugin.NumdrasslProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages persistent control connections to backend servers for plugin messaging.
 *
 * <p>Unlike player connections, control connections are dedicated channels for
 * proxy-to-backend communication that don't require a player to be connected.</p>
 *
 * <h2>Architecture</h2>
 * <pre>
 * Proxy                          Backend (with Bridge)
 *   │                                  │
 *   │◄──── Control Connection ────────►│
 *   │      (PluginMessagePacket)       │
 *   │                                  │
 *   │◄──── Player Connections ────────►│
 *   │      (Normal game traffic)       │
 * </pre>
 */
public final class BackendControlManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackendControlManager.class);

    /**
     * Magic identifier sent during control connection handshake.
     * This tells the bridge that this is a control connection, not a player.
     */
    public static final String CONTROL_CONNECTION_MARKER = "NUMDRASSL_CONTROL";

    private final ProxyConfig config;
    private final NumdrasslProxy proxy;
    private final NumdrasslEventManager eventManager;
    private final NumdrasslChannelRegistrar channelRegistrar;

    private final Map<String, ControlConnection> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reconnectExecutor;
    private final EventLoopGroup eventLoopGroup;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private QuicSslContext sslContext;
    private byte[] proxySecret;

    public BackendControlManager(
            @Nonnull ProxyConfig config,
            @Nonnull NumdrasslProxy proxy,
            @Nonnull NumdrasslEventManager eventManager,
            @Nonnull NumdrasslChannelRegistrar channelRegistrar) {
        this.config = Objects.requireNonNull(config, "config");
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.eventManager = Objects.requireNonNull(eventManager, "eventManager");
        this.channelRegistrar = Objects.requireNonNull(channelRegistrar, "channelRegistrar");
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BackendControlManager-Reconnect");
            t.setDaemon(true);
            return t;
        });
        this.eventLoopGroup = new NioEventLoopGroup(1, r -> {
            Thread t = new Thread(r, "BackendControlManager-IO");
            t.setDaemon(true);
            return t;
        });

        initProxySecret();
    }

    private void initProxySecret() {
        String envSecret = System.getenv("NUMDRASSL_SECRET");
        if (envSecret != null && !envSecret.isEmpty()) {
            this.proxySecret = envSecret.getBytes(StandardCharsets.UTF_8);
            return;
        }

        String configSecret = config.getProxySecret();
        if (configSecret != null && !configSecret.isEmpty()) {
            this.proxySecret = configSecret.getBytes(StandardCharsets.UTF_8);
            return;
        }

        // Generate a random secret if none configured
        this.proxySecret = new byte[32];
        new java.security.SecureRandom().nextBytes(proxySecret);
        LOGGER.warn("No proxy secret configured for control connections!");
    }

    /**
     * Initializes the SSL context for control connections.
     *
     * @param certPath path to the certificate file
     * @param keyPath path to the private key file
     */
    public void initSslContext(@Nonnull String certPath, @Nonnull String keyPath) {
        File certFile = new File(certPath);
        File keyFile = new File(keyPath);

        if (!certFile.exists() || !keyFile.exists()) {
            LOGGER.error("Certificate files not found for control connections");
            return;
        }

        try {
            this.sslContext = QuicSslContextBuilder.forClient()
                .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                .keyManager(keyFile, null, certFile)
                .applicationProtocols("hytale/1")
                .build();
            LOGGER.info("SSL context initialized for control connections");
        } catch (Exception e) {
            LOGGER.error("Failed to create SSL context for control connections", e);
        }
    }

    /**
     * Starts the control manager and connects to all configured backends.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        if (sslContext == null) {
            LOGGER.warn("SSL context not initialized - control connections disabled");
            return;
        }

        LOGGER.info("Starting backend control connections...");

        for (BackendServer backend : config.getBackends()) {
            connectToBackend(backend);
        }

        // Schedule periodic reconnection attempts
        reconnectExecutor.scheduleWithFixedDelay(
            this::checkAndReconnect,
            30, 30, TimeUnit.SECONDS
        );
    }

    /**
     * Stops the control manager and closes all connections.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        LOGGER.info("Stopping backend control connections...");

        reconnectExecutor.shutdownNow();

        for (ControlConnection conn : connections.values()) {
            conn.close();
        }
        connections.clear();

        eventLoopGroup.shutdownGracefully();
    }

    /**
     * Sends a plugin message to a specific backend server.
     *
     * @param serverName the backend server name
     * @param channel the channel identifier
     * @param data the message data
     * @return true if the message was sent
     */
    public boolean sendPluginMessage(@Nonnull String serverName, @Nonnull ChannelIdentifier channel, @Nonnull byte[] data) {
        ControlConnection conn = connections.get(serverName.toLowerCase());
        if (conn == null || !conn.isActive()) {
            LOGGER.debug("Cannot send plugin message to {}: no active control connection", serverName);
            return false;
        }

        PluginMessagePacket packet = new PluginMessagePacket(channel.getId(), data);
        return conn.send(packet);
    }

    /**
     * Sends a plugin message to all connected backends.
     *
     * @param channel the channel identifier
     * @param data the message data
     * @return the number of backends the message was sent to
     */
    public int broadcastPluginMessage(@Nonnull ChannelIdentifier channel, @Nonnull byte[] data) {
        int sent = 0;
        for (Map.Entry<String, ControlConnection> entry : connections.entrySet()) {
            if (entry.getValue().isActive()) {
                PluginMessagePacket packet = new PluginMessagePacket(channel.getId(), data);
                if (entry.getValue().send(packet)) {
                    sent++;
                }
            }
        }
        return sent;
    }

    /**
     * Checks if a backend has an active control connection.
     *
     * @param serverName the server name
     * @return true if connected
     */
    public boolean isConnected(@Nonnull String serverName) {
        ControlConnection conn = connections.get(serverName.toLowerCase());
        return conn != null && conn.isActive();
    }

    // ==================== Internal Methods ====================

    private void connectToBackend(BackendServer backend) {
        String name = backend.getName().toLowerCase();

        ControlConnection existing = connections.get(name);
        if (existing != null && existing.isActive()) {
            LOGGER.debug("Already have active connection to {}", name);
            return;
        }

        LOGGER.info("Establishing control connection to backend: {}", backend.getName());

        ControlConnection conn = new ControlConnection(backend);
        connections.put(name, conn);

        // Connect asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                conn.connect();
            } catch (Exception e) {
                LOGGER.error("Failed to connect to backend {}: {}", backend.getName(), e.getMessage());
            }
        });
    }

    private void checkAndReconnect() {
        if (!running.get()) return;

        for (BackendServer backend : config.getBackends()) {
            String name = backend.getName().toLowerCase();
            ControlConnection conn = connections.get(name);

            if (conn == null || !conn.isActive()) {
                LOGGER.debug("Reconnecting to backend: {}", backend.getName());
                connections.remove(name);
                connectToBackend(backend);
            }
        }
    }

    private void handleIncomingMessage(String serverName, PluginMessagePacket packet) {
        LOGGER.debug("Received plugin message from {}: channel={}, size={}",
            serverName, packet.getChannel(), packet.getDataUnsafe().length);

        // Find the registered server
        RegisteredServer server = proxy.getServer(serverName).orElse(null);
        if (server == null) {
            LOGGER.warn("Received plugin message from unknown server: {}", serverName);
            return;
        }

        // Check if channel is registered
        ChannelIdentifier channelId = ChannelIdentifier.fromId(packet.getChannel());
        if (!channelRegistrar.isRegistered(channelId)) {
            LOGGER.debug("Ignoring plugin message on unregistered channel: {}", packet.getChannel());
            return;
        }

        // Fire event
        PluginMessageEvent event = new PluginMessageEvent(channelId, server, packet.getDataUnsafe());
        eventManager.fire(event);
    }

    // ==================== Control Connection ====================

    /**
     * Represents a control connection to a single backend server.
     */
    private class ControlConnection {
        private final BackendServer backend;
        private volatile QuicChannel quicChannel;
        private volatile QuicStreamChannel streamChannel;
        private volatile Channel datagramChannel;
        private volatile boolean active = false;

        ControlConnection(BackendServer backend) {
            this.backend = backend;
        }

        void connect() throws Exception {
            InetSocketAddress address = new InetSocketAddress(backend.getHost(), backend.getPort());

            // Create QUIC client codec with BBR congestion control
            ChannelHandler codec = new QuicClientCodecBuilder()
                .sslContext(sslContext)
                .congestionControlAlgorithm(QuicCongestionControlAlgorithm.BBR)
                .maxIdleTimeout(60, TimeUnit.SECONDS)
                .initialMaxData(10_000_000)
                .initialMaxStreamDataBidirectionalLocal(1_000_000)
                .initialMaxStreamDataBidirectionalRemote(1_000_000)
                .initialMaxStreamsBidirectional(10)
                .initialMaxStreamsUnidirectional(10)
                .build();

            // Create bootstrap and bind
            Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioDatagramChannel.class)
                .handler(codec);

            datagramChannel = bootstrap.bind(0).sync().channel();

            // Connect QUIC channel
            QuicChannel.newBootstrap(datagramChannel)
                .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        ch.pipeline().addLast(new ControlMessageHandler(backend.getName()));
                    }
                })
                .remoteAddress(address)
                .connect()
                .addListener(future -> {
                    if (future.isSuccess()) {
                        quicChannel = (QuicChannel) future.getNow();
                        onQuicConnected();
                    } else {
                        LOGGER.error("Failed to establish QUIC connection to {}: {}",
                            backend.getName(), future.cause().getMessage());
                        active = false;
                    }
                });
        }

        private void onQuicConnected() {
            LOGGER.info("Control QUIC connection established to {}", backend.getName());

            // Create a bidirectional stream for control messages
            quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, new ControlMessageHandler(backend.getName()))
                .addListener(future -> {
                    if (future.isSuccess()) {
                        streamChannel = (QuicStreamChannel) future.getNow();
                        onStreamCreated();
                    } else {
                        LOGGER.error("Failed to create control stream to {}: {}",
                            backend.getName(), future.cause().getMessage());
                        close();
                    }
                });
        }

        private void onStreamCreated() {
            LOGGER.info("Control stream established to {}", backend.getName());

            // Send control handshake
            byte[] handshakeData = createControlHandshake();

            // Wrap handshake in a PluginMessagePacket for consistency
            PluginMessagePacket handshakePacket = new PluginMessagePacket(
                "numdrassl:control_handshake",
                handshakeData
            );

            ByteBuf buf = Unpooled.buffer();
            handshakePacket.serialize(buf);
            streamChannel.writeAndFlush(buf).addListener(f -> {
                if (f.isSuccess()) {
                    active = true;
                    LOGGER.info("Control connection to {} is now active", backend.getName());
                } else {
                    LOGGER.error("Failed to send handshake to {}: {}",
                        backend.getName(), f.cause().getMessage());
                    close();
                }
            });
        }

        private byte[] createControlHandshake() {
            ByteBuf buf = Unpooled.buffer();
            try {
                // Marker
                byte[] marker = CONTROL_CONNECTION_MARKER.getBytes(StandardCharsets.UTF_8);
                buf.writeShort(marker.length);
                buf.writeBytes(marker);

                // Timestamp for replay protection
                buf.writeLong(System.currentTimeMillis());

                // Server name we're connecting to
                byte[] serverName = backend.getName().getBytes(StandardCharsets.UTF_8);
                buf.writeShort(serverName.length);
                buf.writeBytes(serverName);

                // HMAC signature
                byte[] dataToSign = new byte[buf.readableBytes()];
                buf.getBytes(0, dataToSign);
                byte[] signature = SecretMessageUtil.calculateHmac(dataToSign, proxySecret);
                buf.writeBytes(signature);

                byte[] result = new byte[buf.readableBytes()];
                buf.readBytes(result);
                return result;
            } finally {
                buf.release();
            }
        }

        boolean send(PluginMessagePacket packet) {
            if (!active || streamChannel == null || !streamChannel.isActive()) {
                return false;
            }

            try {
                ByteBuf buf = Unpooled.buffer();
                packet.serialize(buf);
                streamChannel.writeAndFlush(buf).addListener(f -> {
                    if (!f.isSuccess()) {
                        LOGGER.warn("Failed to send plugin message to {}: {}",
                            backend.getName(), f.cause().getMessage());
                    }
                });
                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to send plugin message to {}: {}", backend.getName(), e.getMessage());
                return false;
            }
        }

        boolean isActive() {
            return active && streamChannel != null && streamChannel.isActive();
        }

        void close() {
            active = false;
            if (streamChannel != null) {
                streamChannel.close();
                streamChannel = null;
            }
            if (quicChannel != null) {
                quicChannel.close();
                quicChannel = null;
            }
            if (datagramChannel != null) {
                datagramChannel.close();
                datagramChannel = null;
            }
        }

        /**
         * Handler for incoming control messages from the backend.
         */
        private class ControlMessageHandler extends SimpleChannelInboundHandler<ByteBuf> {
            private final String serverName;

            ControlMessageHandler(String serverName) {
                this.serverName = serverName;
            }

            @Override
            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                // Check if this is a plugin message
                if (!PluginMessagePacket.isPluginMessage(msg)) {
                    LOGGER.debug("Received non-plugin message from {}", serverName);
                    return;
                }

                // Deserialize the packet
                PluginMessagePacket packet = PluginMessagePacket.deserialize(msg);
                if (packet == null) {
                    LOGGER.warn("Failed to deserialize plugin message from {}", serverName);
                    return;
                }

                // Handle the message
                handleIncomingMessage(serverName, packet);
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) {
                LOGGER.info("Control connection to {} closed", serverName);
                active = false;
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                LOGGER.error("Error in control connection to {}: {}", serverName, cause.getMessage());
                ctx.close();
            }
        }
    }
}

