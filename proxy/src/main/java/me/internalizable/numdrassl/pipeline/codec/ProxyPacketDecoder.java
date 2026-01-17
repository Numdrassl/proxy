package me.internalizable.numdrassl.pipeline.codec;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.PacketRegistry;
import com.hypixel.hytale.protocol.io.PacketIO;
import com.hypixel.hytale.protocol.io.PacketStatsRecorder;
import com.hypixel.hytale.protocol.io.ProtocolException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;

/**
 * Decodes Hytale protocol packets from raw bytes.
 *
 * <p>Unknown packets (not in {@link PacketRegistry}) are forwarded as raw
 * {@link ByteBuf} to allow transparent proxying of new packet types.</p>
 */
public final class ProxyPacketDecoder extends ByteToMessageDecoder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyPacketDecoder.class);

    private static final int HEADER_SIZE = 8;           // 4 bytes length + 4 bytes packet ID
    private static final int MAX_PAYLOAD_SIZE = 100_000_000; // 100MB

    private final String connectionType;
    private final boolean debugMode;

    public ProxyPacketDecoder(@Nonnull String connectionType, boolean debugMode) {
        this.connectionType = Objects.requireNonNull(connectionType, "connectionType");
        this.debugMode = debugMode;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < HEADER_SIZE) {
            return;
        }

        in.markReaderIndex();

        int payloadLength = in.readIntLE();
        if (!validatePayloadLength(ctx, payloadLength)) {
            return;
        }

        int packetId = in.readIntLE();
        PacketRegistry.PacketInfo packetInfo = PacketRegistry.getById(packetId);

        if (packetInfo == null) {
            decodeUnknownPacket(ctx, in, out, payloadLength, packetId);
        } else {
            decodeKnownPacket(ctx, in, out, payloadLength, packetId, packetInfo);
        }
    }

    // ==================== Validation ====================

    private boolean validatePayloadLength(ChannelHandlerContext ctx, int payloadLength) {
        if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_SIZE) {
            LOGGER.error("[{}] Invalid payload length: {}", connectionType, payloadLength);
            ctx.close();
            return false;
        }
        return true;
    }

    private boolean validatePacketSize(ChannelHandlerContext ctx, int payloadLength,
                                        PacketRegistry.PacketInfo packetInfo) {
        if (payloadLength > packetInfo.maxSize()) {
            LOGGER.error("[{}] Packet {} payload too large: {} > {}",
                connectionType, packetInfo.name(), payloadLength, packetInfo.maxSize());
            ctx.close();
            return false;
        }
        return true;
    }

    // ==================== Decoding ====================

    private void decodeUnknownPacket(ChannelHandlerContext ctx, ByteBuf in, List<Object> out,
                                      int payloadLength, int packetId) {
        if (in.readableBytes() < payloadLength) {
            in.resetReaderIndex();
            return;
        }

        // Copy raw bytes for forwarding
        in.resetReaderIndex();
        int totalSize = HEADER_SIZE + payloadLength;
        ByteBuf rawCopy = ctx.alloc().buffer(totalSize);

        try {
            in.readBytes(rawCopy, totalSize);
            out.add(rawCopy);

            if (debugMode) {
                LOGGER.debug("[{}] Forwarding unknown packet id={} (size={} bytes)",
                    connectionType, packetId, totalSize);
            }
        } catch (Exception e) {
            rawCopy.release();
            throw e;
        }
    }

    private void decodeKnownPacket(ChannelHandlerContext ctx, ByteBuf in, List<Object> out,
                                    int payloadLength, int packetId, PacketRegistry.PacketInfo packetInfo) {
        if (!validatePacketSize(ctx, payloadLength, packetInfo)) {
            return;
        }

        if (in.readableBytes() < payloadLength) {
            in.resetReaderIndex();
            return;
        }

        try {
            Packet packet = PacketIO.readFramedPacketWithInfo(in, payloadLength, packetInfo, PacketStatsRecorder.NOOP);
            out.add(packet);

            if (debugMode) {
                LOGGER.debug("[{}] Decoded packet: {} (id={})",
                    connectionType, packetInfo.name(), packetId);
            }
        } catch (ProtocolException e) {
            LOGGER.error("[{}] Protocol error decoding {}: {}", connectionType, packetInfo.name(), e.getMessage());
            ctx.close();
        } catch (IndexOutOfBoundsException e) {
            LOGGER.error("[{}] Buffer underflow decoding {}: {}", connectionType, packetInfo.name(), e.getMessage());
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("[{}] Exception in packet decoder", connectionType, cause);
        ctx.close();
    }
}

