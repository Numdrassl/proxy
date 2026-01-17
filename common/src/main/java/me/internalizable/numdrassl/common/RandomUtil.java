package me.internalizable.numdrassl.common;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility for generating random values.
 */
public class RandomUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * Generate a cryptographically secure random string of the specified length.
     *
     * @param length the length of the string to generate
     * @return a random alphanumeric string
     */
    public static String generateSecureRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = SECURE_RANDOM.nextInt(ALPHANUMERIC.length());
            sb.append(ALPHANUMERIC.charAt(index));
        }
        return sb.toString();
    }

    /**
     * Generate a cryptographically secure random byte array.
     *
     * @param length the number of bytes to generate
     * @return a random byte array
     */
    public static byte[] generateSecureRandomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * Generate a cryptographically secure random Base64 URL-safe string.
     *
     * @param byteLength the number of random bytes (output string will be longer)
     * @return a random Base64 URL-safe string
     */
    public static String generateSecureRandomBase64(int byteLength) {
        byte[] bytes = generateSecureRandomBytes(byteLength);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

