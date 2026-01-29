package me.internalizable.numdrassl.plugin.bridge;

import me.internalizable.numdrassl.api.chat.ChatMessageBuilder;
import me.internalizable.numdrassl.config.BackendServer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Internal result of firing a PlayerTransferEvent.
 *
 * <p>This record bridges the API event result to the internal proxy
 * representation using {@link BackendServer}.</p>
 *
 * @param allowed whether the transfer is allowed
 * @param targetServer the target backend server (may be redirected)
 * @param denyMessage the denial message if not allowed
 */
public record PlayerTransferBridgeResult(
    boolean allowed,
    @Nullable BackendServer targetServer,
    @Nullable ChatMessageBuilder denyMessage
) {
    /**
     * Creates an allowed result with the given target server.
     */
    @Nonnull
    public static PlayerTransferBridgeResult allow(@Nonnull BackendServer targetServer) {
        Objects.requireNonNull(targetServer, "targetServer");
        return new PlayerTransferBridgeResult(true, targetServer, null);
    }

    /**
     * Creates a denied result with the given message.
     */
    @Nonnull
    public static PlayerTransferBridgeResult deny(@Nullable ChatMessageBuilder message) {
        return new PlayerTransferBridgeResult(false, null, message);
    }

    /**
     * Creates a redirected result to a different server.
     */
    @Nonnull
    public static PlayerTransferBridgeResult redirect(@Nonnull BackendServer newTarget) {
        Objects.requireNonNull(newTarget, "newTarget");
        return new PlayerTransferBridgeResult(true, newTarget, null);
    }

    public boolean isAllowed() {
        return allowed;
    }

    @Nullable
    public BackendServer getTargetServer() {
        return targetServer;
    }

    @Nullable
    public ChatMessageBuilder getDenyMessage() {
        return denyMessage;
    }
}
