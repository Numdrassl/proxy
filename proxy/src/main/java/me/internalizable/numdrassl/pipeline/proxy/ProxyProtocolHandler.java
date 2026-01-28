package me.internalizable.numdrassl.pipeline.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Decodes the HAProxy PROXY protocol (versions 1 and 2) to extract real client addresses.
 *
 * <p>This handler MUST be placed at the beginning of the QUIC stream pipeline before any other handlers.
 * It will consume the PROXY protocol header and set the real client address as a channel attribute
 * on the parent QUIC channel, then remove itself from the pipeline.</p>
 *
 * <h2>Protocol Support</h2>
 * <ul>
 *   <li><b>Version 1</b>: Human-readable ASCII format (e.g., "PROXY TCP4 192.168.1.1 192.168.1.11 56324 443\r\n")</li>
 *   <li><b>Version 2</b>: Binary format with signature prefix</li>
 * </ul>
 *
 * <h2>Security</h2>
 * <p>The PROXY protocol MUST only be accepted from trusted sources (DDoS protection providers).
 * This handler should only be enabled when configured and connections should be filtered by IP.</p>
 *
 * <h2>Usage with QUIC</h2>
 * <p>For QUIC connections, the PROXY protocol header is sent as the first data on the bidirectional stream.
 * The DDoS protection service terminates the client's QUIC connection, reads the PROXY header from
 * its stream, and when forwarding to this proxy, prepends a new PROXY header with the original client's address.</p>
 *
 * @see <a href="https://www.haproxy.org/download/2.0/doc/proxy-protocol.txt">PROXY Protocol Specification</a>
 */
public final class ProxyProtocolHandler extends ByteToMessageDecoder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyProtocolHandler.class);

    /**
     * Channel attribute key for the real client address extracted from PROXY protocol.
     * Stored on the QUIC channel (parent of stream).
     */
    public static final AttributeKey<InetSocketAddress> REAL_CLIENT_ADDRESS =
            AttributeKey.valueOf("PROXY_REAL_CLIENT_ADDRESS");

    /**
     * Channel attribute key for the destination address from PROXY protocol.
     */
    public static final AttributeKey<InetSocketAddress> PROXY_DESTINATION_ADDRESS =
            AttributeKey.valueOf("PROXY_DESTINATION_ADDRESS");

    // Version 1 signature: "PROXY "
    private static final byte[] V1_SIGNATURE = "PROXY ".getBytes(StandardCharsets.US_ASCII);

    // Version 2 signature: \x0D\x0A\x0D\x0A\x00\x0D\x0A\x51\x55\x49\x54\x0A
    private static final byte[] V2_SIGNATURE = {
            0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A
    };

    // Version 1 max line length (including CRLF)
    private static final int V1_MAX_LINE_LENGTH = 108;

    // Version 2 header minimum size
    private static final int V2_HEADER_MIN_SIZE = 16;

    // Minimum bytes needed to detect protocol version
    private static final int MIN_DETECT_SIZE = 8;

    // Trusted source IPs (configured via ProxyConfig)
    private final Set<String> trustedProxies;
    private final boolean required;
    private final boolean debugMode;

    /**
     * Creates a new PROXY protocol handler.
     *
     * @param trustedProxies set of trusted proxy IP addresses (empty = trust all)
     * @param required       whether PROXY protocol is required (reject if missing)
     * @param debugMode      whether to log detailed debug information
     */
    public ProxyProtocolHandler(@Nonnull Set<String> trustedProxies, boolean required, boolean debugMode) {
        this.trustedProxies = Objects.requireNonNull(trustedProxies, "trustedProxies");
        this.required = required;
        this.debugMode = debugMode;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Need at least some bytes to determine protocol
        if (in.readableBytes() < MIN_DETECT_SIZE) {
            return;
        }

        // Get the parent QUIC channel for trusted proxy check
        QuicChannel quicChannel = getQuicChannel(ctx);
        if (quicChannel == null) {
            LOGGER.error("PROXY protocol handler not on a QUIC stream channel");
            ctx.close();
            return;
        }

        // Check trusted proxy (based on immediate connection address)
        if (!trustedProxies.isEmpty()) {
            java.net.SocketAddress remoteAddr = quicChannel.remoteSocketAddress();
            if (remoteAddr instanceof InetSocketAddress remoteAddress) {
                String remoteIp = remoteAddress.getAddress().getHostAddress();
                if (!trustedProxies.contains(remoteIp)) {
                    LOGGER.warn("PROXY protocol received from untrusted source: {}", remoteIp);
                    ctx.close();
                    return;
                }
            }
        }

        int readerIndex = in.readerIndex();

        // Try to detect version
        if (in.readableBytes() >= V2_SIGNATURE.length && isV2Signature(in, readerIndex)) {
            decodeV2(ctx, in, quicChannel);
        } else if (isV1Signature(in, readerIndex)) {
            decodeV1(ctx, in, quicChannel);
        } else if (required) {
            // Not a valid PROXY protocol header - error if required
            LOGGER.error("Invalid or missing PROXY protocol header from {} (required=true)",
                    quicChannel.remoteAddress());
            ctx.close();
        } else {
            // Not required - just pass through and use real address
            LOGGER.debug("No PROXY protocol header detected, using real address");
            ctx.pipeline().remove(this);
        }
    }

    private QuicChannel getQuicChannel(ChannelHandlerContext ctx) {
        if (ctx.channel() instanceof QuicStreamChannel streamChannel) {
            return (QuicChannel) streamChannel.parent();
        }
        return null;
    }

    private boolean isV1Signature(ByteBuf buf, int readerIndex) {
        if (buf.readableBytes() < V1_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < V1_SIGNATURE.length; i++) {
            if (buf.getByte(readerIndex + i) != V1_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isV2Signature(ByteBuf buf, int readerIndex) {
        if (buf.readableBytes() < V2_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < V2_SIGNATURE.length; i++) {
            if (buf.getByte(readerIndex + i) != V2_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    // ==================== Version 1 Decoding ====================

    private void decodeV1(ChannelHandlerContext ctx, ByteBuf in, QuicChannel quicChannel) {
        // Find CRLF
        int lineEnd = findCRLF(in, in.readerIndex(), Math.min(in.readableBytes(), V1_MAX_LINE_LENGTH));
        if (lineEnd < 0) {
            if (in.readableBytes() >= V1_MAX_LINE_LENGTH) {
                LOGGER.error("PROXY v1 header too long (no CRLF within {} bytes)", V1_MAX_LINE_LENGTH);
                ctx.close();
            }
            // Need more data
            return;
        }

        // Read the line (excluding CRLF)
        int lineLength = lineEnd - in.readerIndex();
        byte[] lineBytes = new byte[lineLength];
        in.readBytes(lineBytes);
        in.skipBytes(2); // Skip CRLF

        String line = new String(lineBytes, StandardCharsets.US_ASCII);
        if (debugMode) {
            LOGGER.debug("PROXY v1 header: {}", line);
        }

        parseV1Header(ctx, line, quicChannel);
    }

    private int findCRLF(ByteBuf buf, int start, int maxLength) {
        int end = Math.min(start + maxLength, buf.writerIndex());
        for (int i = start; i < end - 1; i++) {
            if (buf.getByte(i) == '\r' && buf.getByte(i + 1) == '\n') {
                return i;
            }
        }
        return -1;
    }

    private void parseV1Header(ChannelHandlerContext ctx, String line, QuicChannel quicChannel) {
        // Format: "PROXY <protocol> <src_addr> <dst_addr> <src_port> <dst_port>"
        // Or: "PROXY UNKNOWN ..."
        String[] parts = line.split(" ");

        if (parts.length < 2) {
            LOGGER.error("Invalid PROXY v1 header: {}", line);
            ctx.close();
            return;
        }

        String protocol = parts[1];

        if ("UNKNOWN".equals(protocol)) {
            // UNKNOWN protocol - use real connection endpoints
            LOGGER.debug("PROXY v1 UNKNOWN protocol - using real addresses");
            completeAndRemove(ctx, quicChannel, null, null);
            return;
        }

        if (parts.length < 6) {
            LOGGER.error("Invalid PROXY v1 header (missing fields): {}", line);
            ctx.close();
            return;
        }

        if (!"TCP4".equals(protocol) && !"TCP6".equals(protocol) &&
            !"UDP4".equals(protocol) && !"UDP6".equals(protocol)) {
            LOGGER.warn("Unsupported PROXY v1 protocol: {}", protocol);
            completeAndRemove(ctx, quicChannel, null, null);
            return;
        }

        try {
            String srcAddr = parts[2];
            String dstAddr = parts[3];
            int srcPort = Integer.parseInt(parts[4]);
            int dstPort = Integer.parseInt(parts[5]);

            InetSocketAddress sourceAddress = new InetSocketAddress(srcAddr, srcPort);
            InetSocketAddress destAddress = new InetSocketAddress(dstAddr, dstPort);

            completeAndRemove(ctx, quicChannel, sourceAddress, destAddress);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Failed to parse PROXY v1 addresses: {}", line, e);
            ctx.close();
        }
    }

    // ==================== Version 2 Decoding ====================

    private void decodeV2(ChannelHandlerContext ctx, ByteBuf in, QuicChannel quicChannel) {
        // Need at least 16 bytes for v2 header
        if (in.readableBytes() < V2_HEADER_MIN_SIZE) {
            return;
        }

        int readerIndex = in.readerIndex();

        // Verify signature (already checked, but let's be sure)
        in.skipBytes(12); // Skip signature

        // Byte 13: version and command
        byte verCmd = in.readByte();
        int version = (verCmd & 0xF0) >> 4;
        int command = verCmd & 0x0F;

        if (version != 2) {
            LOGGER.error("Unsupported PROXY protocol version: {}", version);
            in.readerIndex(readerIndex);
            ctx.close();
            return;
        }

        // Byte 14: address family and protocol
        byte famProto = in.readByte();
        int addressFamily = (famProto & 0xF0) >> 4;
        int transportProtocol = famProto & 0x0F;

        // Bytes 15-16: address length
        int addressLen = in.readUnsignedShort();

        // Total header size: 16 + addressLen
        int totalHeaderSize = V2_HEADER_MIN_SIZE + addressLen;
        in.readerIndex(readerIndex); // Reset reader

        if (in.readableBytes() < totalHeaderSize) {
            // Need more data
            return;
        }

        if (debugMode) {
            LOGGER.debug("PROXY v2 header: version={}, command={}, family={}, protocol={}, addrLen={}",
                    version, command, addressFamily, transportProtocol, addressLen);
        }

        // Skip the base header
        in.skipBytes(V2_HEADER_MIN_SIZE);

        // Handle command
        switch (command) {
            case 0x00: // LOCAL
                // Local connection - use real endpoints
                in.skipBytes(addressLen);
                LOGGER.debug("PROXY v2 LOCAL command - using real addresses");
                completeAndRemove(ctx, quicChannel, null, null);
                return;

            case 0x01: // PROXY
                parseV2Addresses(ctx, in, quicChannel, addressFamily, transportProtocol, addressLen);
                return;

            default:
                LOGGER.error("Unsupported PROXY v2 command: {}", command);
                in.skipBytes(addressLen);
                ctx.close();
        }
    }

    private void parseV2Addresses(ChannelHandlerContext ctx, ByteBuf in, QuicChannel quicChannel,
                                   int addressFamily, int transportProtocol, int addressLen) {
        InetSocketAddress sourceAddress = null;
        InetSocketAddress destAddress = null;

        try {
            switch (addressFamily) {
                case 0x00: // AF_UNSPEC
                    in.skipBytes(addressLen);
                    break;

                case 0x01: // AF_INET (IPv4)
                    if (addressLen < 12) {
                        LOGGER.error("PROXY v2 IPv4 address too short: {}", addressLen);
                        in.skipBytes(addressLen);
                        ctx.close();
                        return;
                    }
                    sourceAddress = readIPv4Address(in);
                    destAddress = readIPv4Address(in);
                    // Skip any TLV data
                    if (addressLen > 12) {
                        in.skipBytes(addressLen - 12);
                    }
                    break;

                case 0x02: // AF_INET6 (IPv6)
                    if (addressLen < 36) {
                        LOGGER.error("PROXY v2 IPv6 address too short: {}", addressLen);
                        in.skipBytes(addressLen);
                        ctx.close();
                        return;
                    }
                    sourceAddress = readIPv6Address(in);
                    destAddress = readIPv6Address(in);
                    // Skip any TLV data
                    if (addressLen > 36) {
                        in.skipBytes(addressLen - 36);
                    }
                    break;

                case 0x03: // AF_UNIX
                    LOGGER.debug("PROXY v2 AF_UNIX not supported - using real addresses");
                    in.skipBytes(addressLen);
                    break;

                default:
                    LOGGER.warn("Unknown PROXY v2 address family: {}", addressFamily);
                    in.skipBytes(addressLen);
            }

            completeAndRemove(ctx, quicChannel, sourceAddress, destAddress);

        } catch (Exception e) {
            LOGGER.error("Failed to parse PROXY v2 addresses", e);
            ctx.close();
        }
    }

    private InetSocketAddress readIPv4Address(ByteBuf in) {
        byte[] addr = new byte[4];
        in.readBytes(addr);
        int port = in.readUnsignedShort();
        return new InetSocketAddress(
                formatIPv4(addr),
                port
        );
    }

    private InetSocketAddress readIPv6Address(ByteBuf in) {
        byte[] addr = new byte[16];
        in.readBytes(addr);
        int port = in.readUnsignedShort();
        return new InetSocketAddress(
                formatIPv6(addr),
                port
        );
    }

    private String formatIPv4(byte[] addr) {
        return String.format("%d.%d.%d.%d",
                addr[0] & 0xFF, addr[1] & 0xFF, addr[2] & 0xFF, addr[3] & 0xFF);
    }

    private String formatIPv6(byte[] addr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i += 2) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%x", ((addr[i] & 0xFF) << 8) | (addr[i + 1] & 0xFF)));
        }
        return sb.toString();
    }

    // ==================== Completion ====================

    private void completeAndRemove(ChannelHandlerContext ctx, QuicChannel quicChannel,
                                    @Nullable InetSocketAddress sourceAddress,
                                    @Nullable InetSocketAddress destAddress) {
        // Store addresses as channel attributes on the QUIC channel
        if (sourceAddress != null) {
            quicChannel.attr(REAL_CLIENT_ADDRESS).set(sourceAddress);
            if (debugMode) {
                LOGGER.debug("PROXY protocol: real client address = {}", sourceAddress);
            }
        }

        if (destAddress != null) {
            quicChannel.attr(PROXY_DESTINATION_ADDRESS).set(destAddress);
        }

        // Remove this handler from the pipeline
        ctx.pipeline().remove(this);

        LOGGER.info("PROXY protocol processed: source={}, dest={}",
                sourceAddress != null ? sourceAddress : "unchanged",
                destAddress != null ? destAddress : "unchanged");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("Error processing PROXY protocol", cause);
        ctx.close();
    }
}
