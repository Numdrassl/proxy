package me.internalizable.numdrassl.api.messaging.channel;

import me.internalizable.numdrassl.api.messaging.MessagingService;

import javax.annotation.Nonnull;

/**
 * Represents a pub/sub channel for cross-proxy messaging.
 *
 * <p>Channels can be either predefined system channels (see {@link Channels}) or
 * custom channels created by plugins via {@link Channels#register(String, String)}.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Use predefined channel
 * messaging.subscribe(Channels.CHAT, handler);
 *
 * // Create and use custom channel
 * MessageChannel myChannel = Channels.register("myplugin", "events");
 * messaging.subscribe(myChannel, handler);
 * }</pre>
 *
 * @see Channels for predefined channels and registration
 * @see MessagingService for pub/sub operations
 */
public interface MessageChannel {

    /**
     * The namespace used for all system channels.
     */
    String SYSTEM_NAMESPACE = "numdrassl";

    /**
     * Get the unique channel identifier.
     *
     * <p>Format: {@code namespace:name} (e.g., "numdrassl:heartbeat" or "myplugin:events")</p>
     *
     * @return the channel identifier
     */
    @Nonnull
    String getId();

    /**
     * Get the namespace portion of the channel ID.
     *
     * @return the namespace (e.g., "numdrassl" or plugin ID)
     */
    @Nonnull
    String getNamespace();

    /**
     * Get the name portion of the channel ID.
     *
     * @return the channel name within the namespace
     */
    @Nonnull
    String getName();

    /**
     * Check if this is a system channel (numdrassl namespace).
     *
     * @return true if this is a predefined system channel
     */
    default boolean isSystemChannel() {
        return SYSTEM_NAMESPACE.equals(getNamespace());
    }
}

