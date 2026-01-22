package me.internalizable.numdrassl.api.event.connection;

import me.internalizable.numdrassl.api.plugin.messaging.ChannelIdentifier;
import me.internalizable.numdrassl.api.server.RegisteredServer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Event fired when a plugin message is received from a backend server.
 *
 * <p>Plugin messages allow backend servers to communicate with the proxy through
 * registered channels. This is commonly used for cross-server synchronization.</p>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @Subscribe
 * public void onPluginMessage(PluginMessageEvent event) {
 *     if (event.getIdentifier().getId().equals("luckperms:data")) {
 *         byte[] data = event.getData();
 *         // Process the LuckPerms sync data
 *
 *         // Mark as handled to prevent forwarding
 *         event.setResult(ForwardResult.handled());
 *     }
 * }
 * }</pre>
 *
 * @see me.internalizable.numdrassl.api.plugin.messaging.ChannelRegistrar
 */
public final class PluginMessageEvent {

    private final ChannelIdentifier identifier;
    private final Object source;
    private final byte[] data;
    private ForwardResult result = ForwardResult.forward();

    /**
     * Creates a new plugin message event.
     *
     * @param identifier the channel identifier
     * @param source the source (RegisteredServer or Player)
     * @param data the message data
     */
    public PluginMessageEvent(
            @Nonnull ChannelIdentifier identifier,
            @Nonnull Object source,
            @Nonnull byte[] data) {
        this.identifier = Objects.requireNonNull(identifier, "identifier");
        this.source = Objects.requireNonNull(source, "source");
        this.data = Objects.requireNonNull(data, "data");
    }

    /**
     * Gets the channel identifier for this message.
     *
     * @return the channel identifier
     */
    @Nonnull
    public ChannelIdentifier getIdentifier() {
        return identifier;
    }

    /**
     * Gets the source of this message.
     *
     * <p>This can be either a {@link RegisteredServer} (if the message came from a backend)
     * or a {@link me.internalizable.numdrassl.api.player.Player} (if it came from a client).</p>
     *
     * @return the message source
     */
    @Nonnull
    public Object getSource() {
        return source;
    }

    /**
     * Gets the message data.
     *
     * @return the raw message bytes
     */
    @Nonnull
    public byte[] getData() {
        return data;
    }

    /**
     * Gets the forward result for this message.
     *
     * @return the forward result
     */
    @Nonnull
    public ForwardResult getResult() {
        return result;
    }

    /**
     * Sets whether this message should be forwarded.
     *
     * @param result the forward result
     */
    public void setResult(@Nonnull ForwardResult result) {
        this.result = Objects.requireNonNull(result, "result");
    }

    /**
     * Represents the result of plugin message handling.
     */
    public static final class ForwardResult {
        private static final ForwardResult FORWARD = new ForwardResult(true);
        private static final ForwardResult HANDLED = new ForwardResult(false);

        private final boolean shouldForward;

        private ForwardResult(boolean shouldForward) {
            this.shouldForward = shouldForward;
        }

        /**
         * Creates a result indicating the message should be forwarded.
         *
         * @return forward result
         */
        @Nonnull
        public static ForwardResult forward() {
            return FORWARD;
        }

        /**
         * Creates a result indicating the message was handled and should not be forwarded.
         *
         * @return handled result
         */
        @Nonnull
        public static ForwardResult handled() {
            return HANDLED;
        }

        /**
         * Checks if the message should be forwarded.
         *
         * @return true if the message should be forwarded
         */
        public boolean shouldForward() {
            return shouldForward;
        }
    }
}

