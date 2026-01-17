package me.internalizable.numdrassl.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCounted;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Wrapper for raw packet data that couldn't be decoded.
 *
 * <p>Used to forward unknown packets through the pipeline without modification.
 * Implements {@link ReferenceCounted} to properly manage the underlying buffer.</p>
 *
 * <p><b>Memory Management:</b> Callers must ensure this object is released
 * after use to prevent memory leaks.</p>
 */
public final class RawPacket implements ReferenceCounted {

    private final int packetId;
    private final ByteBuf data;

    public RawPacket(int packetId, @Nonnull ByteBuf data) {
        this.packetId = packetId;
        this.data = Objects.requireNonNull(data, "data");
    }

    public int getPacketId() {
        return packetId;
    }

    @Nonnull
    public ByteBuf getData() {
        return data;
    }

    // ==================== ReferenceCounted Implementation ====================

    @Override
    public int refCnt() {
        return data.refCnt();
    }

    @Override
    public RawPacket retain() {
        data.retain();
        return this;
    }

    @Override
    public RawPacket retain(int increment) {
        data.retain(increment);
        return this;
    }

    @Override
    public RawPacket touch() {
        data.touch();
        return this;
    }

    @Override
    public RawPacket touch(Object hint) {
        data.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return data.release();
    }

    @Override
    public boolean release(int decrement) {
        return data.release(decrement);
    }

    @Override
    public String toString() {
        return String.format("RawPacket{id=%d, size=%d}", packetId, data.readableBytes());
    }
}
