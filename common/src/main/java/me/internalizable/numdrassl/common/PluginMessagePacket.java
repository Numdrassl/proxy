package me.internalizable.numdrassl.common;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Custom packet for plugin messaging between the proxy and backend servers.
 *
 * <p>This packet enables bidirectional communication between Numdrassl proxy
 * and backend servers running the Bridge plugin, similar to Minecraft's
 * plugin message channels.</p>
 *
 * <h2>Packet Structure</h2>
 * <pre>
 * +----------------+------------------+----------------+------------------+
 * | Magic (4 bytes)| Channel (varstr) | Data Len (int) | Data (bytes)     |
 * +----------------+------------------+----------------+------------------+
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create a message
 * PluginMessagePacket packet = new PluginMessagePacket("luckperms:sync", data);
 *
 * // Serialize to bytes
 * byte[] bytes = packet.toBytes();
 *
 * // Deserialize from bytes
 * PluginMessagePacket received = PluginMessagePacket.fromBytes(bytes);
 * }</pre>
 */
public final class PluginMessagePacket {

    /**
     * Magic bytes to identify plugin message packets.
     * "NDPM" = Numdrassl Plugin Message
     */
    public static final int MAGIC = 0x4E44504D; // "NDPM" in ASCII

    /**
     * Maximum channel name length.
     */
    public static final int MAX_CHANNEL_LENGTH = 256;

    /**
     * Maximum data payload size (64KB).
     */
    public static final int MAX_DATA_SIZE = 65536;

    private final String channel;
    private final byte[] data;

    /**
     * Creates a new plugin message packet.
     *
     * @param channel the channel identifier (e.g., "luckperms:sync")
     * @param data the message payload
     * @throws IllegalArgumentException if channel is too long or data exceeds max size
     */
    public PluginMessagePacket(@Nonnull String channel, @Nonnull byte[] data) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(data, "data");

        if (channel.length() > MAX_CHANNEL_LENGTH) {
            throw new IllegalArgumentException("Channel name too long: " + channel.length() + " > " + MAX_CHANNEL_LENGTH);
        }
        if (data.length > MAX_DATA_SIZE) {
            throw new IllegalArgumentException("Data too large: " + data.length + " > " + MAX_DATA_SIZE);
        }

        this.channel = channel;
        this.data = data.clone();
    }

    /**
     * Gets the channel identifier.
     *
     * @return the channel
     */
    @Nonnull
    public String getChannel() {
        return channel;
    }

    /**
     * Gets the message data.
     *
     * @return a copy of the data
     */
    @Nonnull
    public byte[] getData() {
        return data.clone();
    }

    /**
     * Gets the raw data reference (no copy).
     * Use with caution - do not modify the returned array.
     *
     * @return the data array
     */
    @Nonnull
    public byte[] getDataUnsafe() {
        return data;
    }

    /**
     * Serializes this packet to a byte array.
     *
     * @return the serialized bytes
     */
    @Nonnull
    public byte[] toBytes() {
        ByteBuf buf = Unpooled.buffer();
        try {
            serialize(buf);
            byte[] result = new byte[buf.readableBytes()];
            buf.readBytes(result);
            return result;
        } finally {
            buf.release();
        }
    }

    /**
     * Serializes this packet to a ByteBuf.
     *
     * @param buf the buffer to write to
     */
    public void serialize(@Nonnull ByteBuf buf) {
        // Magic
        buf.writeInt(MAGIC);

        // Channel (length-prefixed UTF-8)
        byte[] channelBytes = channel.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(channelBytes.length);
        buf.writeBytes(channelBytes);

        // Data (length-prefixed)
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    /**
     * Computes the serialized size of this packet.
     *
     * @return the size in bytes
     */
    public int computeSize() {
        return 4 + // Magic
               2 + channel.getBytes(StandardCharsets.UTF_8).length + // Channel
               4 + data.length; // Data
    }

    /**
     * Deserializes a packet from a byte array.
     *
     * @param bytes the serialized bytes
     * @return the packet, or null if invalid
     */
    @Nullable
    public static PluginMessagePacket fromBytes(@Nonnull byte[] bytes) {
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        try {
            return deserialize(buf);
        } finally {
            buf.release();
        }
    }

    /**
     * Deserializes a packet from a ByteBuf.
     *
     * @param buf the buffer to read from
     * @return the packet, or null if invalid
     */
    @Nullable
    public static PluginMessagePacket deserialize(@Nonnull ByteBuf buf) {
        if (buf.readableBytes() < 4) {
            return null;
        }

        // Check magic
        int magic = buf.readInt();
        if (magic != MAGIC) {
            return null;
        }

        // Read channel
        if (buf.readableBytes() < 2) {
            return null;
        }
        int channelLen = buf.readUnsignedShort();
        if (channelLen > MAX_CHANNEL_LENGTH || buf.readableBytes() < channelLen) {
            return null;
        }
        byte[] channelBytes = new byte[channelLen];
        buf.readBytes(channelBytes);
        String channel = new String(channelBytes, StandardCharsets.UTF_8);

        // Read data
        if (buf.readableBytes() < 4) {
            return null;
        }
        int dataLen = buf.readInt();
        if (dataLen > MAX_DATA_SIZE || dataLen < 0 || buf.readableBytes() < dataLen) {
            return null;
        }
        byte[] data = new byte[dataLen];
        buf.readBytes(data);

        return new PluginMessagePacket(channel, data);
    }

    /**
     * Checks if the given bytes start with the plugin message magic.
     *
     * @param bytes the bytes to check
     * @return true if this looks like a plugin message packet
     */
    public static boolean isPluginMessage(@Nonnull byte[] bytes) {
        if (bytes.length < 4) {
            return false;
        }
        int magic = ((bytes[0] & 0xFF) << 24) |
                    ((bytes[1] & 0xFF) << 16) |
                    ((bytes[2] & 0xFF) << 8) |
                    (bytes[3] & 0xFF);
        return magic == MAGIC;
    }

    /**
     * Checks if the given ByteBuf starts with the plugin message magic.
     *
     * @param buf the buffer to check (position not modified)
     * @return true if this looks like a plugin message packet
     */
    public static boolean isPluginMessage(@Nonnull ByteBuf buf) {
        if (buf.readableBytes() < 4) {
            return false;
        }
        int magic = buf.getInt(buf.readerIndex());
        return magic == MAGIC;
    }

    @Override
    public String toString() {
        return String.format("PluginMessagePacket{channel='%s', dataSize=%d}", channel, data.length);
    }
}

