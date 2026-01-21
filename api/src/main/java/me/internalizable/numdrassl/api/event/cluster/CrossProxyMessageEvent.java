package me.internalizable.numdrassl.api.event.cluster;

import me.internalizable.numdrassl.api.messaging.message.PluginMessage;

import javax.annotation.Nonnull;

/**
 * Event fired when a plugin message is received from another proxy.
 *
 * <p>Plugins can use this for custom cross-proxy communication.</p>
 */
public final class CrossProxyMessageEvent {

    private final PluginMessage message;

    public CrossProxyMessageEvent(@Nonnull PluginMessage message) {
        this.message = message;
    }

    /**
     * Get the plugin message.
     *
     * @return the message
     */
    @Nonnull
    public PluginMessage getMessage() {
        return message;
    }

    /**
     * Get the ID of the plugin that sent this message.
     *
     * @return the plugin ID
     */
    @Nonnull
    public String getPluginId() {
        return message.pluginId();
    }

    /**
     * Get the custom channel within the plugin namespace.
     *
     * @return the plugin channel
     */
    @Nonnull
    public String getChannel() {
        return message.channel();
    }

    /**
     * Get the message payload.
     *
     * @return the payload string (typically JSON)
     */
    @Nonnull
    public String getPayload() {
        return message.payload();
    }

    /**
     * Get the ID of the proxy that sent this message.
     *
     * @return the source proxy ID
     */
    @Nonnull
    public String getSourceProxyId() {
        return message.sourceProxyId();
    }
}

