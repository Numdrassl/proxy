package me.internalizable.numdrassl.api.messaging.channel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of message channels with predefined system channels and custom channel support.
 *
 * <p>This class provides:</p>
 * <ul>
 *   <li>Predefined system channels (HEARTBEAT, CHAT, etc.)</li>
 *   <li>Dynamic channel registration for plugins</li>
 *   <li>Channel lookup by ID</li>
 * </ul>
 *
 * <h2>Predefined Channels</h2>
 * <pre>{@code
 * Channels.HEARTBEAT  // numdrassl:heartbeat - Proxy health monitoring
 * Channels.PLAYER_COUNT // numdrassl:player-count - Player count updates
 * Channels.CHAT       // numdrassl:chat - Cross-proxy chat
 * Channels.TRANSFER   // numdrassl:transfer - Player transfers
 * Channels.PLUGIN     // numdrassl:plugin - Generic plugin messages
 * Channels.BROADCAST  // numdrassl:broadcast - Cluster-wide announcements
 * }</pre>
 *
 * <h2>Custom Channels</h2>
 * <pre>{@code
 * // Register a custom channel for your plugin
 * MessageChannel myChannel = Channels.register("myplugin", "game-events");
 *
 * // Use it for messaging
 * messaging.publish(myChannel, new PluginMessage(...));
 * messaging.subscribe(myChannel, handler);
 * }</pre>
 */
public final class Channels {

    private static final Map<String, MessageChannel> REGISTRY = new ConcurrentHashMap<>();

    // ==================== System Channels ====================

    /**
     * Proxy heartbeat and registration.
     * Used for proxy discovery and health monitoring.
     */
    public static final MessageChannel HEARTBEAT = registerSystem("heartbeat");

    /**
     * Player count updates.
     * Proxies publish their current player count periodically.
     */
    public static final MessageChannel PLAYER_COUNT = registerSystem("player-count");

    /**
     * Cross-proxy chat messages.
     * Used to send messages to players connected to other proxies.
     */
    public static final MessageChannel CHAT = registerSystem("chat");

    /**
     * Player transfer coordination.
     * Used when transferring players between proxies.
     */
    public static final MessageChannel TRANSFER = registerSystem("transfer");

    /**
     * Plugin-defined custom messages (generic).
     * Plugins can use this channel for simple cross-proxy communication.
     */
    public static final MessageChannel PLUGIN = registerSystem("plugin");

    /**
     * Broadcast messages to all proxies.
     * Used for cluster-wide announcements.
     */
    public static final MessageChannel BROADCAST = registerSystem("broadcast");

    private Channels() {
        // Utility class
    }

    // ==================== Registration ====================

    /**
     * Register a custom message channel.
     *
     * <p>The channel ID will be {@code namespace:name}. If a channel with this ID
     * already exists, the existing channel is returned.</p>
     *
     * @param namespace the namespace (typically your plugin ID)
     * @param name the channel name within the namespace
     * @return the registered channel
     * @throws IllegalArgumentException if namespace or name is invalid
     */
    @Nonnull
    public static MessageChannel register(@Nonnull String namespace, @Nonnull String name) {
        validateNamespace(namespace);
        validateName(name);

        String id = namespace + ":" + name;
        return REGISTRY.computeIfAbsent(id, k -> new SimpleMessageChannel(namespace, name));
    }

    /**
     * Register a custom channel using full ID format.
     *
     * @param channelId the full channel ID (format: namespace:name)
     * @return the registered channel
     * @throws IllegalArgumentException if the ID format is invalid
     */
    @Nonnull
    public static MessageChannel register(@Nonnull String channelId) {
        Objects.requireNonNull(channelId, "channelId");

        int colonIndex = channelId.indexOf(':');
        if (colonIndex <= 0 || colonIndex >= channelId.length() - 1) {
            throw new IllegalArgumentException("Invalid channel ID format. Expected 'namespace:name', got: " + channelId);
        }

        String namespace = channelId.substring(0, colonIndex);
        String name = channelId.substring(colonIndex + 1);
        return register(namespace, name);
    }

    /**
     * Get a channel by its ID.
     *
     * @param channelId the channel ID
     * @return the channel, or null if not registered
     */
    @Nullable
    public static MessageChannel get(@Nonnull String channelId) {
        return REGISTRY.get(channelId);
    }

    /**
     * Get a channel by its ID, registering it if not found.
     *
     * @param channelId the channel ID (format: namespace:name)
     * @return the channel (existing or newly registered)
     */
    @Nonnull
    public static MessageChannel getOrRegister(@Nonnull String channelId) {
        MessageChannel existing = REGISTRY.get(channelId);
        if (existing != null) {
            return existing;
        }
        return register(channelId);
    }

    /**
     * Check if a channel is registered.
     *
     * @param channelId the channel ID
     * @return true if registered
     */
    public static boolean isRegistered(@Nonnull String channelId) {
        return REGISTRY.containsKey(channelId);
    }

    /**
     * Get all registered channels.
     *
     * @return unmodifiable collection of all channels
     */
    @Nonnull
    public static Collection<MessageChannel> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /**
     * Unregister a custom channel.
     *
     * <p>System channels (numdrassl:*) cannot be unregistered.</p>
     *
     * @param channelId the channel ID to unregister
     * @return true if the channel was unregistered
     */
    public static boolean unregister(@Nonnull String channelId) {
        MessageChannel channel = REGISTRY.get(channelId);
        if (channel != null && channel.isSystemChannel()) {
            return false; // Cannot unregister system channels
        }
        return REGISTRY.remove(channelId) != null;
    }

    // ==================== Internal ====================

    private static MessageChannel registerSystem(String name) {
        String id = MessageChannel.SYSTEM_NAMESPACE + ":" + name;
        MessageChannel channel = new SimpleMessageChannel(MessageChannel.SYSTEM_NAMESPACE, name);
        REGISTRY.put(id, channel);
        return channel;
    }

    private static void validateNamespace(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        if (namespace.isEmpty()) {
            throw new IllegalArgumentException("Namespace cannot be empty");
        }
        if (!namespace.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Namespace must contain only lowercase letters, numbers, hyphens, and underscores: " + namespace);
        }
        if ("numdrassl".equals(namespace)) {
            throw new IllegalArgumentException("Cannot register channels in the 'numdrassl' namespace");
        }
    }

    private static void validateName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Channel name cannot be empty");
        }
        if (!name.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Channel name must contain only lowercase letters, numbers, hyphens, and underscores: " + name);
        }
    }

    // ==================== Simple Implementation ====================

    /**
     * Simple immutable implementation of MessageChannel.
     */
    private record SimpleMessageChannel(
            @Nonnull String namespace,
            @Nonnull String name
    ) implements MessageChannel {

        @Override
        @Nonnull
        public String getId() {
            return namespace + ":" + name;
        }

        @Override
        @Nonnull
        public String getNamespace() {
            return namespace;
        }

        @Override
        @Nonnull
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return getId();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MessageChannel that)) return false;
            return getId().equals(that.getId());
        }

        @Override
        public int hashCode() {
            return getId().hashCode();
        }
    }
}

