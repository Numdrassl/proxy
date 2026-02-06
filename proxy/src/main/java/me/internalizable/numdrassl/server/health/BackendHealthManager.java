package me.internalizable.numdrassl.server.health;

import com.hypixel.hytale.protocol.Packet;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import me.internalizable.numdrassl.config.BackendServer;
import me.internalizable.numdrassl.event.packet.ProxyPing;
import me.internalizable.numdrassl.event.packet.ProxyPong;
import me.internalizable.numdrassl.pipeline.codec.ProxyPacketDecoder;
import me.internalizable.numdrassl.pipeline.codec.ProxyPacketEncoder;
import me.internalizable.numdrassl.server.ProxyCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class BackendHealthManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackendHealthManager.class);

    private final ProxyCore proxyCore;

    private final ConcurrentHashMap<String, BackendHealth> backendHealth = new ConcurrentHashMap<>(); // backend name, backend health object
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> inFlightPings = new ConcurrentHashMap<>();

    public BackendHealthManager(ProxyCore proxyCore) {
        this.proxyCore = proxyCore;
    }

    public BackendHealth get(@Nonnull BackendServer backend) {
        return backendHealth.computeIfAbsent(backend.getName(), n -> new BackendHealth());
    }

    public ConcurrentHashMap<String, BackendHealth> getAll() {
        return backendHealth;
    }

    public CompletableFuture<Boolean> sendPingAsync(BackendServer backendServer, long timeoutMs) {
        return inFlightPings.computeIfAbsent(backendServer.getName(), name -> {

            LOGGER.debug("inflight start backend={}", name);
            CompletableFuture<Boolean> ping = sendPingAsyncInternal(backendServer, timeoutMs).exceptionally(ex -> false);

            ping.whenComplete((ok, ex) -> {
                inFlightPings.remove(name);
            });

            return ping;
        });
    }

    private CompletableFuture<Boolean> sendPingAsyncInternal(BackendServer backend, long timeoutMs) {
        Objects.requireNonNull(backend, "backend");

        CompletableFuture<Boolean> result = new CompletableFuture<>();

        InetSocketAddress address = new InetSocketAddress(backend.getHost(), backend.getPort());
        Bootstrap bootstrap = proxyCore.getBackendConnector().createBootstrap();

        bootstrap.bind(0).addListener((ChannelFutureListener) bindFuture -> {
            if (!bindFuture.isSuccess()) {
                result.complete(false);
                return;
            }

            Channel datagram = bindFuture.channel();

            Runnable cleanup = () -> {
                if (datagram.isOpen()) {
                    datagram.close();
                }
            };

            datagram.eventLoop().schedule(() -> {
                completeOnce(result, false, cleanup);
            }, timeoutMs, TimeUnit.MILLISECONDS);

            QuicChannel.newBootstrap(datagram).remoteAddress(address).streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                @Override
                protected void initChannel(QuicStreamChannel ch) {
                }
            }).connect().addListener(connectFuture -> {
                if (!connectFuture.isSuccess()) {
                    completeOnce(result, false, cleanup);
                    return;
                }

                QuicChannel quic = (QuicChannel) connectFuture.getNow();

                ChannelInitializer<QuicStreamChannel> streamInitializer = new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        boolean debug = proxyCore.getConfig().isDebugMode();

                        ch.pipeline().addLast(new ProxyPacketDecoder("backend-ping", debug));
                        ch.pipeline().addLast(new ProxyPacketEncoder("backend-ping", debug));

                        ch.pipeline().addLast(new SimpleChannelInboundHandler<Packet>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
                                if (packet instanceof ProxyPong) {
                                    completeOnce(result, true, () -> {
                                        get(backend).markPingResponse();

                                        ctx.channel().parent().close();
                                        cleanup.run();
                                    });
                                }
                            }
                        });
                    }
                };


                quic.createStream(QuicStreamType.BIDIRECTIONAL, streamInitializer).addListener(streamFuture -> {
                    if (!streamFuture.isSuccess()) {
                        completeOnce(result, false, cleanup);
                        quic.close();
                        return;
                    }

                    QuicStreamChannel stream = (QuicStreamChannel) streamFuture.getNow();

                    ProxyPing ping = new ProxyPing();
                    ping.timestamp = System.currentTimeMillis();
                    ping.nonce = ThreadLocalRandom.current().nextLong();

                    if (proxyCore.getBackendHealthManager() != null) {
                        proxyCore.getBackendHealthManager().get(backend).markPingSent();
                    }
                    stream.writeAndFlush(ping).addListener(writeFuture -> {
                        if (!writeFuture.isSuccess()) {
                            completeOnce(result, false, cleanup);
                        }
                    });
                });


                result.whenComplete((ok, err) -> quic.close());
            });
        });

        return result;
    }

    private boolean completeOnce(CompletableFuture<Boolean> future, boolean value, Runnable cleanup) {
        if (future.complete(value)) {
            cleanup.run();
            return true;
        }
        return false;
    }


}
