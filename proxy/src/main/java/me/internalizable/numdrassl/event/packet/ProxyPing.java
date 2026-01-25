package me.internalizable.numdrassl.event.packet;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.io.ValidationResult;
import com.hypixel.hytale.protocol.io.VarInt;
import io.netty.buffer.ByteBuf;

import javax.annotation.Nonnull;

public class ProxyPing implements Packet {

    public static final int PACKET_ID = 998;
    public long nonce;
    public long timestamp;

    public ProxyPing() {

    }

    public ProxyPing(long nonce, long timestamp) {
        this.nonce = nonce;
        this.timestamp = timestamp;
    }

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

    public static ProxyPing deserialize(@Nonnull ByteBuf buf, int offset) {
        ProxyPing ping = new ProxyPing();
        ping.nonce = buf.getLong(offset);
        ping.timestamp = buf.getLong(offset + Long.BYTES);
        return ping;
    }

    public static ValidationResult validateStructure(@Nonnull ByteBuf buf, int offset) {
        int readable = buf.readableBytes() - offset;
        if (readable < 16) {
            return ValidationResult.error("ProxyPing too small: " + readable + " bytes");
        }
        return ValidationResult.OK;
    }
}
