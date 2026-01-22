package me.internalizable.numdrassl.api.plugin.messaging;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * Manages registration of plugin message channels.
 *
 * <p>Plugin message channels allow communication between the proxy and backend servers.
 * Channels must be registered before they can be used to send or receive messages.</p>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * ChannelIdentifier channel = ChannelIdentifier.create("myplugin", "data");
 *
 * // Register the channel
 * proxy.getChannelRegistrar().register(channel);
 *
 * // Later, unregister when done
 * proxy.getChannelRegistrar().unregister(channel);
 * }</pre>
 *
 * @see ChannelIdentifier
 * @see me.internalizable.numdrassl.api.event.connection.PluginMessageEvent
 */
public interface ChannelRegistrar {

    /**
     * Registers a plugin message channel.
     *
     * <p>After registration, the proxy will listen for messages on this channel
     * and fire {@link me.internalizable.numdrassl.api.event.connection.PluginMessageEvent}
     * when messages are received.</p>
     *
     * @param channel the channel to register
     */
    void register(@Nonnull ChannelIdentifier channel);

    /**
     * Registers multiple plugin message channels.
     *
     * @param channels the channels to register
     */
    void register(@Nonnull ChannelIdentifier... channels);

    /**
     * Unregisters a plugin message channel.
     *
     * <p>After unregistration, messages on this channel will no longer be processed.</p>
     *
     * @param channel the channel to unregister
     */
    void unregister(@Nonnull ChannelIdentifier channel);

    /**
     * Unregisters multiple plugin message channels.
     *
     * @param channels the channels to unregister
     */
    void unregister(@Nonnull ChannelIdentifier... channels);

    /**
     * Checks if a channel is registered.
     *
     * @param channel the channel to check
     * @return true if the channel is registered
     */
    boolean isRegistered(@Nonnull ChannelIdentifier channel);

    /**
     * Gets all registered channels.
     *
     * @return an unmodifiable collection of registered channels
     */
    @Nonnull
    Collection<ChannelIdentifier> getRegisteredChannels();
}

