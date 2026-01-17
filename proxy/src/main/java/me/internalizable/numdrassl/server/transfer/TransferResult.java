package me.internalizable.numdrassl.server.transfer;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Immutable result of a player transfer attempt.
 *
 * @param success whether the transfer was initiated successfully
 * @param message descriptive message about the result
 */
public record TransferResult(boolean success, @Nonnull String message) {

    public TransferResult {
        Objects.requireNonNull(message, "message");
    }

    /**
     * Creates a successful transfer result.
     */
    @Nonnull
    public static TransferResult success(@Nonnull String message) {
        return new TransferResult(true, message);
    }

    /**
     * Creates a failed transfer result.
     */
    @Nonnull
    public static TransferResult failure(@Nonnull String message) {
        return new TransferResult(false, message);
    }

    /**
     * Checks if the transfer was successful.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Gets the result message.
     */
    @Nonnull
    public String getMessage() {
        return message;
    }
}

