package me.internalizable.numdrassl.session.channel;

import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the QUIC channels and streams for a proxy session.
 *
 * <p>This class encapsulates the bidirectional channel management:</p>
 * <ul>
 *   <li><b>Client side</b>: The downstream connection from the Hytale client</li>
 *   <li><b>Backend side</b>: The upstream connection to the Hytale server</li>
 * </ul>
 *
 * <p>All operations are thread-safe via atomic references.</p>
 */
public final class SessionChannels {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionChannels.class);

    private final long sessionId;
    private final QuicChannel clientChannel;

    private final AtomicReference<QuicStreamChannel> clientStream = new AtomicReference<>();
    private final AtomicReference<QuicChannel> backendChannel = new AtomicReference<>();
    private final AtomicReference<QuicStreamChannel> backendStream = new AtomicReference<>();

    public SessionChannels(long sessionId, @Nonnull QuicChannel clientChannel) {
        this.sessionId = sessionId;
        this.clientChannel = clientChannel;
    }

    // ==================== Client Side ====================

    @Nonnull
    public QuicChannel clientChannel() {
        return clientChannel;
    }

    @Nullable
    public QuicStreamChannel clientStream() {
        return clientStream.get();
    }

    public void setClientStream(@Nullable QuicStreamChannel stream) {
        clientStream.set(stream);
    }

    public boolean isClientActive() {
        return clientChannel.isActive();
    }

    public boolean isClientStreamActive() {
        QuicStreamChannel stream = clientStream.get();
        return stream != null && stream.isActive();
    }

    // ==================== Backend Side ====================

    @Nullable
    public QuicChannel backendChannel() {
        return backendChannel.get();
    }

    public void setBackendChannel(@Nullable QuicChannel channel) {
        backendChannel.set(channel);
    }

    @Nullable
    public QuicStreamChannel backendStream() {
        return backendStream.get();
    }

    public void setBackendStream(@Nullable QuicStreamChannel stream) {
        backendStream.set(stream);
    }

    public boolean isBackendActive() {
        QuicChannel channel = backendChannel.get();
        return channel != null && channel.isActive();
    }

    public boolean isBackendStreamActive() {
        QuicStreamChannel stream = backendStream.get();
        return stream != null && stream.isActive();
    }

    // ==================== Lifecycle ====================

    /**
     * Closes all channels and streams.
     */
    public void closeAll() {
        closeBackend();
        closeClient();
    }

    /**
     * Closes only the backend connection, keeping client connected.
     * Used during server transfers.
     */
    public void closeBackend() {
        QuicStreamChannel bs = backendStream.getAndSet(null);
        if (bs != null && bs.isActive()) {
            bs.close();
            LOGGER.debug("Session {}: Closed backend stream", sessionId);
        }

        QuicChannel bc = backendChannel.getAndSet(null);
        if (bc != null && bc.isActive()) {
            bc.close();
            LOGGER.debug("Session {}: Closed backend channel", sessionId);
        }
    }

    /**
     * Closes the client connection.
     */
    public void closeClient() {
        QuicStreamChannel cs = clientStream.getAndSet(null);
        if (cs != null && cs.isActive()) {
            cs.close();
            LOGGER.debug("Session {}: Closed client stream", sessionId);
        }

        if (clientChannel.isActive()) {
            clientChannel.close();
            LOGGER.debug("Session {}: Closed client channel", sessionId);
        }
    }
}

