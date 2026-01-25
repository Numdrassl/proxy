package me.internalizable.numdrassl.event.packet;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.io.ValidationResult;
import io.netty.buffer.ByteBuf;

import javax.annotation.Nonnull;

public class ProxyPong implements Packet {

    public static final int PACKET_ID = 999;
    public long nonce;
    public long timestamp;

    @Override
    public int getId() {
        return PACKET_ID;
    }

    @Override
    public void serialize(@Nonnull ByteBuf buf) {
        buf.writeLong(nonce);
        buf.writeLong(timestamp);
    }

    @Override
    public int computeSize() {
        return 16;
    }

    public static ProxyPong deserialize(@Nonnull ByteBuf buf, int offset) {
        ProxyPong pong = new ProxyPong();
        pong.nonce = buf.getLong(offset);
        pong.timestamp = buf.getLong(offset + Long.BYTES);
        return pong;
    }

    public static ValidationResult validateStructure(@Nonnull ByteBuf buf, int offset) {
        int readable = buf.readableBytes() - offset;
        if (readable < 16) {
            return ValidationResult.error("ProxyPong too small: " + readable + " bytes");
        }
        return ValidationResult.OK;
    }
}