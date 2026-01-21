package me.internalizable.numdrassl.api.messaging.message;

import me.internalizable.numdrassl.api.messaging.ChannelMessage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.UUID;

/**
 * Player transfer coordination message between proxies.
 *
 * <p>Used to coordinate player transfers between proxy instances,
 * enabling features like:</p>
 * <ul>
 *   <li>Cross-proxy server switching</li>
 *   <li>Load balancing player distribution</li>
 *   <li>Graceful proxy shutdown with player migration</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Initiate a transfer
 * TransferMessage transfer = new TransferMessage(
 *     proxyId, Instant.now(),
 *     playerUuid, playerName,
 *     targetProxyId, targetServer,
 *     generateTransferToken()
 * );
 * messaging.publish(Channels.TRANSFER, transfer);
 *
 * // Subscribe to handle incoming transfers
 * @Subscribe(SystemChannel.TRANSFER)
 * public void onTransfer(TransferMessage transfer) {
 *     if (transfer.targetProxyId().equals(myProxyId)) {
 *         prepareForIncomingPlayer(transfer);
 *     }
 * }
 * }</pre>
 *
 * @param sourceProxyId the proxy initiating the transfer
 * @param timestamp when the transfer was initiated
 * @param playerUuid UUID of the player being transferred
 * @param playerName username of the player being transferred
 * @param targetProxyId the proxy that should receive the player
 * @param targetServer the backend server to connect the player to
 * @param transferToken optional security token for validating the transfer
 */
public record TransferMessage(
        @Nonnull String sourceProxyId,
        @Nonnull Instant timestamp,
        @Nonnull UUID playerUuid,
        @Nonnull String playerName,
        @Nonnull String targetProxyId,
        @Nonnull String targetServer,
        @Nullable String transferToken
) implements ChannelMessage {

    @Override
    @Nonnull
    public String messageType() {
        return "transfer";
    }
}

