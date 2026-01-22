package me.internalizable.numdrassl.api.plugin.messaging;

import javax.annotation.Nonnull;

/**
 * Represents a channel identifier for plugin messaging between the proxy and backend servers.
 *
 * <p>Plugin messages allow communication between the proxy and backend Hytale servers.
 * Each channel has a unique identifier in the format "namespace:name".</p>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Create a channel identifier
 * ChannelIdentifier channel = ChannelIdentifier.create("luckperms", "data");
 *
 * // Register the channel
 * proxy.getChannelRegistrar().register(channel);
 *
 * // Send a message to all servers
 * for (RegisteredServer server : proxy.getAllServers()) {
 *     server.sendPluginMessage(channel, data);
 * }
 * }</pre>
 *
 * @see ChannelRegistrar
 */
public interface ChannelIdentifier {

    /**
     * Gets the namespace of this channel.
     *
     * @return the namespace
     */
    @Nonnull
    String getNamespace();

    /**
     * Gets the name of this channel.
     *
     * @return the name
     */
    @Nonnull
    String getName();

    /**
     * Gets the full identifier in the format "namespace:name".
     *
     * @return the full identifier string
     */
    @Nonnull
    String getId();

    /**
     * Creates a new channel identifier.
     *
     * @param namespace the namespace (e.g., "luckperms", "bungeecord")
     * @param name the channel name (e.g., "data", "main")
     * @return the channel identifier
     */
    @Nonnull
    static ChannelIdentifier create(@Nonnull String namespace, @Nonnull String name) {
        return new SimpleChannelIdentifier(namespace, name);
    }

    /**
     * Creates a channel identifier from a full ID string.
     *
     * @param id the full identifier in format "namespace:name"
     * @return the channel identifier
     * @throws IllegalArgumentException if the ID format is invalid
     */
    @Nonnull
    static ChannelIdentifier fromId(@Nonnull String id) {
        int colonIndex = id.indexOf(':');
        if (colonIndex == -1) {
            throw new IllegalArgumentException("Invalid channel ID format: " + id + " (expected 'namespace:name')");
        }
        return create(id.substring(0, colonIndex), id.substring(colonIndex + 1));
    }
}

