package me.internalizable.numdrassl.messaging.redis;

/**
 * Exception thrown when Redis connection fails.
 *
 * <p>This exception wraps connection failures from Lettuce and provides
 * a clear indication that the messaging service could not be initialized.</p>
 */
public final class RedisConnectionException extends RuntimeException {

    /**
     * Create a new Redis connection exception.
     *
     * @param message the error message
     */
    public RedisConnectionException(String message) {
        super(message);
    }

    /**
     * Create a new Redis connection exception with a cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public RedisConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}

