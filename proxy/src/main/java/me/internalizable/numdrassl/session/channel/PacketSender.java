package me.internalizable.numdrassl.session.channel;

import com.hypixel.hytale.protocol.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Handles thread-safe packet sending to client and backend streams.
 *
 * <p>All send operations ensure they execute on the correct event loop thread,
 * preventing race conditions and ensuring proper Netty channel handling.</p>
 *
 * <p>ByteBuf resources are properly released if sending fails.</p>
 */
public final class PacketSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(PacketSender.class);

    private final long sessionId;
    private final SessionChannels channels;

    public PacketSender(long sessionId, @Nonnull SessionChannels channels) {
        this.sessionId = sessionId;
        this.channels = Objects.requireNonNull(channels, "channels");
    }

    // ==================== Send to Client ====================

    /**
     * Sends a packet to the connected client.
     * Thread-safe: executes on the client stream's event loop.
     *
     * @param packet the packet to send
     * @return true if the packet was queued for sending
     */
    public boolean sendToClient(@Nonnull Packet packet) {
        Objects.requireNonNull(packet, "packet");
        QuicStreamChannel stream = channels.clientStream();
        return sendToStream(stream, packet, "client");
    }

    /**
     * Sends raw data to the connected client.
     * Thread-safe: executes on the client stream's event loop.
     *
     * @param data the data to send (will be released on failure)
     * @return true if the data was queued for sending
     */
    public boolean sendToClient(@Nonnull ByteBuf data) {
        Objects.requireNonNull(data, "data");
        QuicStreamChannel stream = channels.clientStream();
        return sendToStream(stream, data, "client");
    }

    // ==================== Send to Backend ====================

    /**
     * Sends a packet to the backend server.
     * Thread-safe: executes on the backend stream's event loop.
     *
     * @param packet the packet to send
     * @return true if the packet was queued for sending
     */
    public boolean sendToBackend(@Nonnull Packet packet) {
        Objects.requireNonNull(packet, "packet");
        QuicStreamChannel stream = channels.backendStream();
        return sendToStream(stream, packet, "backend");
    }

    /**
     * Sends raw data to the backend server.
     * Thread-safe: executes on the backend stream's event loop.
     *
     * @param data the data to send (will be released on failure)
     * @return true if the data was queued for sending
     */
    public boolean sendToBackend(@Nonnull ByteBuf data) {
        Objects.requireNonNull(data, "data");
        QuicStreamChannel stream = channels.backendStream();
        return sendToStream(stream, data, "backend");
    }

    // ==================== Internal ====================

    private boolean sendToStream(QuicStreamChannel stream, Object message, String target) {
        if (stream == null || !stream.isActive()) {
            LOGGER.warn("Session {}: Cannot send to {} - stream not active", sessionId, target);
            releaseIfByteBuf(message);
            return false;
        }

        if (stream.eventLoop().inEventLoop()) {
            doWrite(stream, message, target);
        } else {
            // Retain ByteBuf when crossing event loops to prevent premature release
            // The encoder will release it after writing
            if (message instanceof ByteBuf buf) {
                buf.retain();
            }
            stream.eventLoop().execute(() -> {
                if (stream.isActive()) {
                    doWrite(stream, message, target);
                } else {
                    LOGGER.warn("Session {}: Stream became inactive before send to {}", sessionId, target);
                    releaseIfByteBuf(message);
                }
            });
        }
        return true;
    }

    private void doWrite(QuicStreamChannel stream, Object message, String target) {
        stream.writeAndFlush(message).addListener(future -> {
            if (!future.isSuccess()) {
                LOGGER.warn("Session {}: Failed to send to {}", sessionId, target, future.cause());
            }
        });
    }

    private void releaseIfByteBuf(Object obj) {
        if (obj instanceof ByteBuf buf && buf.refCnt() > 0) {
            buf.release();
        }
    }
}

