package me.internalizable.numdrassl.session.channel;

import com.hypixel.hytale.protocol.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.ReferenceCountUtil;
import me.internalizable.numdrassl.profiling.ProxyMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.List;
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
        boolean result = sendToStream(stream, packet, "client");
        if (result) {
            ProxyMetrics.getInstance().recordPacketToClient(packet.getClass().getSimpleName(), 0);
        }
        return result;
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
        int bytes = data.readableBytes();
        boolean result = sendToStream(stream, data, "client");
        if (result) {
            ProxyMetrics.getInstance().recordPacketToClient("RawPacket", bytes);
        }
        return result;
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
        boolean result = sendToStream(stream, packet, "backend");
        if (result) {
            ProxyMetrics.getInstance().recordPacketToBackend(packet.getClass().getSimpleName(), 0);
        }
        return result;
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
        int bytes = data.readableBytes();
        boolean result = sendToStream(stream, data, "backend");
        if (result) {
            ProxyMetrics.getInstance().recordPacketToBackend("RawPacket", bytes);
        }
        return result;
    }

    // ==================== Raw Packet Batch Sending ====================

    /**
     * Writes an entire batch of raw packets to the client stream and flushes,
     * all within a <b>single cross-event-loop task</b>.
     *
     * <p>This is the fast path for forwarding raw (unregistered) packets such as
     * chunk data. By submitting all writes + flush as one Runnable, we reduce
     * cross-thread scheduling overhead from O(N) to O(1) per Netty read batch,
     * dramatically reducing latency during high-throughput scenarios.</p>
     *
     * <p>Ownership of every {@link ByteBuf} in the list transfers to this method;
     * buffers are released on failure.</p>
     *
     * @param packets the batch of raw packets to send
     */
    public void sendRawBatchToClient(@Nonnull List<ByteBuf> packets) {
        Objects.requireNonNull(packets, "packets");
        QuicStreamChannel stream = channels.clientStream();
        if (stream == null || !stream.isActive()) {
            releaseAll(packets);
            return;
        }
        if (stream.eventLoop().inEventLoop()) {
            writeBatchAndFlush(stream, packets);
        } else {
            stream.eventLoop().execute(() -> {
                if (stream.isActive()) {
                    writeBatchAndFlush(stream, packets);
                } else {
                    releaseAll(packets);
                }
            });
        }
    }

    /**
     * Writes an entire batch of raw packets to the backend stream and flushes,
     * all within a <b>single cross-event-loop task</b>.
     *
     * @param packets the batch of raw packets to send
     * @see #sendRawBatchToClient(List) for design rationale
     */
    public void sendRawBatchToBackend(@Nonnull List<ByteBuf> packets) {
        Objects.requireNonNull(packets, "packets");
        QuicStreamChannel stream = channels.backendStream();
        if (stream == null || !stream.isActive()) {
            releaseAll(packets);
            return;
        }
        if (stream.eventLoop().inEventLoop()) {
            writeBatchAndFlush(stream, packets);
        } else {
            stream.eventLoop().execute(() -> {
                if (stream.isActive()) {
                    writeBatchAndFlush(stream, packets);
                } else {
                    releaseAll(packets);
                }
            });
        }
    }

    /**
     * Writes all packets in the batch and flushes once at the end.
     * Must be called from the stream's event loop thread.
     */
    private void writeBatchAndFlush(QuicStreamChannel stream, List<ByteBuf> packets) {
        for (ByteBuf packet : packets) {
            stream.write(packet, stream.voidPromise());
        }
        stream.flush();
    }

    private void releaseAll(List<ByteBuf> packets) {
        for (ByteBuf buf : packets) {
            ReferenceCountUtil.safeRelease(buf);
        }
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

            //bytebuf released by SimpleChannelInbound so no need to track

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

