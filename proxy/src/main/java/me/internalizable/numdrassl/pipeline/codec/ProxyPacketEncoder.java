package me.internalizable.numdrassl.pipeline.codec;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.io.PacketIO;
import com.hypixel.hytale.protocol.io.PacketStatsRecorder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Encodes Hytale protocol packets to bytes.
 *
 * <p>Supports two message types:</p>
 * <ul>
 *   <li>{@link Packet} - Encoded using the Hytale protocol</li>
 *   <li>{@link ByteBuf} - Forwarded as-is (for unknown packets)</li>
 * </ul>
 *
 * <p>This encoder is marked as {@link ChannelHandler.Sharable @Sharable} and can be
 * reused across multiple channels since it has no per-channel state.</p>
 */
@ChannelHandler.Sharable
public final class ProxyPacketEncoder extends MessageToByteEncoder<Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyPacketEncoder.class);

    private final String connectionType;
    private final boolean debugMode;

    public ProxyPacketEncoder(@Nonnull String connectionType, boolean debugMode) {
        this.connectionType = Objects.requireNonNull(connectionType, "connectionType");
        this.debugMode = debugMode;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) {
        if (msg instanceof ByteBuf raw) {
            encodeRawPacket(raw, out);
        } else if (msg instanceof Packet packet) {
            encodePacket(packet, out);
        } else {
            LOGGER.warn("[{}] Unknown message type: {}", connectionType, msg.getClass().getName());
        }
    }

    private void encodeRawPacket(ByteBuf raw, ByteBuf out) {
        int packetId = extractPacketId(raw);

        // Simply copy bytes to output - DO NOT release raw buffer here
        // MessageToByteEncoder handles releasing the input message
        out.writeBytes(raw);

        if (debugMode) {
            LOGGER.debug("[{}] Forwarding raw packet id={}", connectionType, packetId);
        }
    }

    private int extractPacketId(ByteBuf raw) {
        if (raw.readableBytes() >= 8) {
            return raw.getIntLE(raw.readerIndex() + 4);
        }
        return -1;
    }

    private void encodePacket(Packet packet, ByteBuf out) {
        if (debugMode) {
            LOGGER.debug("[{}] Encoding packet: {} (id={})",
                connectionType, packet.getClass().getSimpleName(), packet.getId());
        }

        PacketIO.writeFramedPacket(packet, packet.getClass(), out, PacketStatsRecorder.NOOP);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("[{}] Exception in packet encoder", connectionType, cause);
        ctx.close();
    }
}

