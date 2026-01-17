package me.internalizable.numdrassl.common;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility for creating signed messages between proxy and backend servers.
 * Uses HMAC-SHA256 to sign player information, allowing backends to trust
 * the proxy without JWT certificate validation.
 *
 * <p>Message format:</p>
 * <pre>
 * [4 bytes] Protocol version (int LE)
 * [8 bytes] UUID most significant bits (long LE)
 * [8 bytes] UUID least significant bits (long LE)
 * [4 bytes] Username length (int LE)
 * [N bytes] Username (UTF-8)
 * [4 bytes] Backend name length (int LE)
 * [N bytes] Backend name (UTF-8)
 * [4 bytes] Remote address length (int LE)
 * [N bytes] Remote address (UTF-8)
 * [4 bytes] Timestamp (unix seconds, int LE)
 * [32 bytes] HMAC-SHA256 signature
 * </pre>
 */
public class SecretMessageUtil {

    private static final Logger LOGGER = Logger.getLogger(SecretMessageUtil.class.getName());

    private static final int PROTOCOL_VERSION = 1;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int HMAC_LENGTH = 32;

    // Message validity window (5 minutes)
    private static final long MESSAGE_VALIDITY_SECONDS = 300;

    /**
     * Create a signed player info message to be sent in Connect packet's referralData.
     *
     * @param uuid Player's UUID
     * @param username Player's username
     * @param backendName Target backend server name
     * @param remoteAddress Player's remote address
     * @param secret Shared secret between proxy and backend
     * @return Encoded and signed message bytes
     */
    public static byte[] createPlayerInfoReferral(
            @Nonnull UUID uuid,
            @Nonnull String username,
            @Nonnull String backendName,
            @Nullable InetSocketAddress remoteAddress,
            @Nonnull byte[] secret) {

        ByteBuf buf = Unpooled.buffer();
        try {
            // Write protocol version
            buf.writeIntLE(PROTOCOL_VERSION);

            // Write UUID
            buf.writeLongLE(uuid.getMostSignificantBits());
            buf.writeLongLE(uuid.getLeastSignificantBits());

            // Write username
            byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
            buf.writeIntLE(usernameBytes.length);
            buf.writeBytes(usernameBytes);

            // Write backend name
            byte[] backendBytes = backendName.getBytes(StandardCharsets.UTF_8);
            buf.writeIntLE(backendBytes.length);
            buf.writeBytes(backendBytes);

            // Write remote address
            String remoteStr = remoteAddress != null ? remoteAddress.toString() : "unknown";
            byte[] remoteBytes = remoteStr.getBytes(StandardCharsets.UTF_8);
            buf.writeIntLE(remoteBytes.length);
            buf.writeBytes(remoteBytes);

            // Write timestamp
            long timestamp = System.currentTimeMillis() / 1000;
            buf.writeIntLE((int) timestamp);

            // Calculate HMAC over the data written so far
            byte[] dataToSign = new byte[buf.readableBytes()];
            buf.getBytes(0, dataToSign);

            byte[] hmac = calculateHmac(dataToSign, secret);
            buf.writeBytes(hmac);

            // Extract final bytes
            byte[] result = new byte[buf.readableBytes()];
            buf.readBytes(result);

            LOGGER.fine("Created player info referral for " + username + " (" + uuid + ") -> " + backendName);
            return result;

        } finally {
            buf.release();
        }
    }

    /**
     * Validate and decode a player info referral message.
     *
     * @param data The referral data bytes (as ByteBuf)
     * @param expectedUuid Expected player UUID
     * @param expectedUsername Expected player username
     * @param expectedBackend Expected backend name
     * @param secret Shared secret
     * @return Decoded message info, or null if validation failed
     */
    @Nullable
    public static BackendPlayerInfoMessage validateAndDecodePlayerInfoReferral(
            @Nonnull ByteBuf data,
            @Nonnull UUID expectedUuid,
            @Nonnull String expectedUsername,
            @Nonnull String expectedBackend,
            @Nonnull byte[] secret) {

        try {
            if (data.readableBytes() < 4 + 16 + 4 + 4 + 4 + 4 + HMAC_LENGTH) {
                LOGGER.warning("Referral data too short: " + data.readableBytes() + " bytes");
                return null;
            }

            int startIndex = data.readerIndex();

            // Read protocol version
            int version = data.readIntLE();
            if (version != PROTOCOL_VERSION) {
                LOGGER.warning("Invalid protocol version: " + version + " (expected " + PROTOCOL_VERSION + ")");
                return null;
            }

            // Read UUID
            long uuidMsb = data.readLongLE();
            long uuidLsb = data.readLongLE();
            UUID uuid = new UUID(uuidMsb, uuidLsb);

            // Read username
            int usernameLen = data.readIntLE();
            if (usernameLen < 0 || usernameLen > 256) {
                LOGGER.warning("Invalid username length: " + usernameLen);
                return null;
            }
            byte[] usernameBytes = new byte[usernameLen];
            data.readBytes(usernameBytes);
            String username = new String(usernameBytes, StandardCharsets.UTF_8);

            // Read backend name
            int backendLen = data.readIntLE();
            if (backendLen < 0 || backendLen > 256) {
                LOGGER.warning("Invalid backend name length: " + backendLen);
                return null;
            }
            byte[] backendBytes = new byte[backendLen];
            data.readBytes(backendBytes);
            String backendName = new String(backendBytes, StandardCharsets.UTF_8);

            // Read remote address
            int remoteLen = data.readIntLE();
            if (remoteLen < 0 || remoteLen > 256) {
                LOGGER.warning("Invalid remote address length: " + remoteLen);
                return null;
            }
            byte[] remoteBytes = new byte[remoteLen];
            data.readBytes(remoteBytes);
            String remoteAddress = new String(remoteBytes, StandardCharsets.UTF_8);

            // Read timestamp
            int timestamp = data.readIntLE();

            // Verify timestamp is within validity window
            long now = System.currentTimeMillis() / 1000;
            if (Math.abs(now - timestamp) > MESSAGE_VALIDITY_SECONDS) {
                LOGGER.warning("Referral message expired or from future: timestamp=" + timestamp + ", now=" + now);
                return null;
            }

            // Read HMAC
            byte[] receivedHmac = new byte[HMAC_LENGTH];
            data.readBytes(receivedHmac);

            // Calculate expected HMAC
            int dataLength = data.readerIndex() - startIndex - HMAC_LENGTH;
            byte[] dataToVerify = new byte[dataLength];
            data.getBytes(startIndex, dataToVerify);

            byte[] expectedHmac = calculateHmac(dataToVerify, secret);

            // Constant-time comparison
            if (!constantTimeEquals(receivedHmac, expectedHmac)) {
                LOGGER.warning("HMAC verification failed for player " + username);
                return null;
            }

            // Verify expected values
            if (!uuid.equals(expectedUuid)) {
                LOGGER.warning("UUID mismatch: got " + uuid + ", expected " + expectedUuid);
                return null;
            }

            if (!username.equals(expectedUsername)) {
                LOGGER.warning("Username mismatch: got " + username + ", expected " + expectedUsername);
                return null;
            }

            if (!backendName.equalsIgnoreCase(expectedBackend)) {
                LOGGER.warning("Backend mismatch: got " + backendName + ", expected " + expectedBackend);
                return null;
            }

            return new BackendPlayerInfoMessage(uuid, username, backendName, remoteAddress, timestamp);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error decoding player info referral", e);
            return null;
        }
    }

    /**
     * Calculate HMAC-SHA256.
     */
    private static byte[] calculateHmac(byte[] data, byte[] secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secret, HMAC_ALGORITHM);
            mac.init(keySpec);
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to calculate HMAC", e);
        }
    }

    /**
     * Constant-time byte array comparison to prevent timing attacks.
     */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    /**
     * Decoded player info from a referral message.
     */
    public static class BackendPlayerInfoMessage {
        private final UUID uuid;
        private final String username;
        private final String backendName;
        private final String remoteAddress;
        private final long timestamp;

        public BackendPlayerInfoMessage(UUID uuid, String username, String backendName,
                                         String remoteAddress, long timestamp) {
            this.uuid = uuid;
            this.username = username;
            this.backendName = backendName;
            this.remoteAddress = remoteAddress;
            this.timestamp = timestamp;
        }

        public UUID uuid() { return uuid; }
        public String username() { return username; }
        public String backendName() { return backendName; }
        public String remoteAddress() { return remoteAddress; }
        public long timestamp() { return timestamp; }

        @Override
        public String toString() {
            return "BackendPlayerInfoMessage{" +
                "uuid=" + uuid +
                ", username='" + username + '\'' +
                ", backendName='" + backendName + '\'' +
                ", remoteAddress='" + remoteAddress + '\'' +
                ", timestamp=" + timestamp +
                '}';
        }
    }
}
