package me.internalizable.numdrassl.api.event.player;

import me.internalizable.numdrassl.api.chat.ChatMessageBuilder;
import me.internalizable.numdrassl.api.event.ResultedEvent;
import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.server.RegisteredServer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

/**
 * Event fired when a player is being transferred to another server.
 *
 * <p>This event is triggered for all transfer types:</p>
 * <ul>
 *   <li>Backend-initiated transfers (via ClientReferral)</li>
 *   <li>Proxy-initiated transfers (via commands or API)</li>
 * </ul>
 *
 * <p>Plugins can use this event to:</p>
 * <ul>
 *   <li>Cancel the transfer</li>
 *   <li>Redirect the player to a different server</li>
 *   <li>Log or audit transfer attempts</li>
 *   <li>Apply custom transfer logic based on conditions</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @Subscribe
 * public void onPlayerTransfer(PlayerTransferEvent event) {
 *     if (event.getTargetServer().getName().equals("maintenance")) {
 *         event.setResult(PlayerTransferEvent.PlayerTransferResult.denied("Server under maintenance"));
 *     }
 * }
 * }</pre>
 */
public class PlayerTransferEvent implements ResultedEvent<PlayerTransferEvent.PlayerTransferResult> {

    private final Player player;
    private final RegisteredServer currentServer;
    private final RegisteredServer targetServer;
    private PlayerTransferResult result;

    public PlayerTransferEvent(
            @Nonnull Player player,
            @Nullable RegisteredServer currentServer,
            @Nonnull RegisteredServer targetServer) {
        this.player = player;
        this.currentServer = currentServer;
        this.targetServer = targetServer;
        this.result = PlayerTransferResult.allowed(targetServer);
    }

    /**
     * Get the player being transferred.
     *
     * @return the player
     */
    @Nonnull
    public Player getPlayer() {
        return player;
    }

    /**
     * Get the server the player is currently connected to, if any.
     *
     * @return the current server, or empty if not connected
     */
    @Nonnull
    public Optional<RegisteredServer> getCurrentServer() {
        return Optional.ofNullable(currentServer);
    }

    /**
     * Get the server that was originally requested as the transfer target.
     *
     * @return the original target server
     */
    @Nonnull
    public RegisteredServer getTargetServer() {
        return targetServer;
    }

    @Override
    public PlayerTransferResult getResult() {
        return result;
    }

    @Override
    public void setResult(PlayerTransferResult result) {
        this.result = result;
    }

    /**
     * Result for a player transfer event.
     */
    public static final class PlayerTransferResult implements Result {

        private static final PlayerTransferResult DENIED = new PlayerTransferResult(false, null, null);

        private final boolean allowed;
        private final RegisteredServer server;
        private final ChatMessageBuilder denyMessage;

        private PlayerTransferResult(boolean allowed, @Nullable RegisteredServer server, @Nullable ChatMessageBuilder denyMessage) {
            this.allowed = allowed;
            this.server = server;
            this.denyMessage = denyMessage;
        }

        @Override
        public boolean isAllowed() {
            return allowed;
        }

        /**
         * Get the server to transfer to.
         *
         * @return the target server, or null if denied
         */
        @Nullable
        public RegisteredServer getServer() {
            return server;
        }

        /**
         * Get the denial message.
         *
         * @return the deny message, or null if allowed or silent denial
         */
        @Nullable
        public ChatMessageBuilder getDenyMessage() {
            return denyMessage;
        }

        /**
         * Allow the transfer to the specified server.
         *
         * @param server the server to transfer to
         * @return an allowed result
         */
        public static PlayerTransferResult allowed(@Nonnull RegisteredServer server) {
            return new PlayerTransferResult(true, server, null);
        }

        /**
         * Deny the transfer silently (no message shown to player).
         *
         * @return a denied result without message
         */
        public static PlayerTransferResult denied() {
            return DENIED;
        }

        /**
         * Deny the transfer with a plain text reason.
         *
         * @param reason the reason to show the player
         * @return a denied result
         */
        public static PlayerTransferResult denied(@Nonnull String reason) {
            Objects.requireNonNull(reason, "reason");
            return new PlayerTransferResult(false, null, ChatMessageBuilder.create().white(reason));
        }

        /**
         * Deny the transfer with a formatted message.
         *
         * @param message the formatted message to show the player
         * @return a denied result
         */
        public static PlayerTransferResult denied(@Nonnull ChatMessageBuilder message) {
            Objects.requireNonNull(message, "message");
            return new PlayerTransferResult(false, null, message);
        }
    }
}
