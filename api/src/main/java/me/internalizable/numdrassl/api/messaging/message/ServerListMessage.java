package me.internalizable.numdrassl.api.messaging.message;

import me.internalizable.numdrassl.api.messaging.ChannelMessage;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.List;

/**
 * Server list synchronization message for sharing backend servers across proxies.
 *
 * <p>When a proxy registers or unregisters a backend server, it publishes this
 * message to notify other proxies in the cluster. This enables:</p>
 * <ul>
 *   <li>Cross-proxy server visibility in {@code /server} command</li>
 *   <li>Players can see and transfer to servers on other proxies</li>
 *   <li>Automatic cleanup when a proxy disconnects</li>
 * </ul>
 *
 * <h2>Message Types</h2>
 * <ul>
 *   <li><b>REGISTER</b> - Announces a new server or updates an existing one</li>
 *   <li><b>UNREGISTER</b> - Removes a server from the cluster</li>
 *   <li><b>SYNC</b> - Sends complete server list (used on proxy join)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Register a server
 * ServerListMessage msg = new ServerListMessage(
 *     proxyId, Instant.now(), ServerListMessage.Type.REGISTER,
 *     "lobby", "192.168.1.100", 25565, true
 * );
 * messaging.publish(Channels.SERVER_LIST, msg);
 *
 * // Unregister a server
 * ServerListMessage unregister = new ServerListMessage(
 *     proxyId, Instant.now(), ServerListMessage.Type.UNREGISTER,
 *     "lobby", null, 0, false
 * );
 * messaging.publish(Channels.SERVER_LIST, unregister);
 * }</pre>
 *
 * @param sourceProxyId the proxy that owns this server
 * @param timestamp when the message was generated
 * @param type the message type (REGISTER, UNREGISTER, or SYNC)
 * @param serverName the name of the server (required for all types)
 * @param host the server hostname/IP (required for REGISTER/SYNC, null for UNREGISTER)
 * @param port the server port (required for REGISTER/SYNC, 0 for UNREGISTER)
 * @param isDefault whether this is the default server (only meaningful for REGISTER/SYNC)
 */
public record ServerListMessage(
        @Nonnull String sourceProxyId,
        @Nonnull Instant timestamp,
        @Nonnull Type type,
        @Nonnull String serverName,
        @javax.annotation.Nullable String host,
        int port,
        boolean isDefault
) implements ChannelMessage {

    /**
     * Message type indicating the action to perform.
     */
    public enum Type {
        /**
         * Register or update a server. Other proxies will add/update this server
         * in their server list.
         */
        REGISTER,

        /**
         * Unregister a server. Other proxies will remove this server from their list.
         */
        UNREGISTER,

        /**
         * Synchronize complete server list. Used when a proxy first joins the cluster
         * to request all servers, or when responding to such a request.
         */
        SYNC
    }

    /**
     * Validates message fields based on message type.
     */
    public ServerListMessage {
        if (sourceProxyId == null) {
            throw new NullPointerException("sourceProxyId must not be null");
        }
        if (timestamp == null) {
            throw new NullPointerException("timestamp must not be null");
        }
        if (type == null) {
            throw new NullPointerException("type must not be null");
        }
        if (serverName == null || serverName.isEmpty()) {
            throw new IllegalArgumentException("serverName must not be null or empty");
        }

        // Validate fields based on type
        switch (type) {
            case REGISTER, SYNC -> {
                if (host == null || host.isEmpty()) {
                    throw new IllegalArgumentException("host must not be null or empty for " + type);
                }
                if (port <= 0 || port > 65535) {
                    throw new IllegalArgumentException("port must be between 1 and 65535 for " + type);
                }
            }
            case UNREGISTER -> {
                // host and port are ignored for UNREGISTER
            }
        }
    }

    /**
     * Creates a REGISTER message for a server.
     *
     * @param sourceProxyId the proxy ID
     * @param serverName the server name
     * @param host the server host
     * @param port the server port
     * @param isDefault whether this is the default server
     * @return a new REGISTER message
     */
    @Nonnull
    public static ServerListMessage register(
            @Nonnull String sourceProxyId,
            @Nonnull String serverName,
            @Nonnull String host,
            int port,
            boolean isDefault) {
        return new ServerListMessage(
                sourceProxyId,
                Instant.now(),
                Type.REGISTER,
                serverName,
                host,
                port,
                isDefault
        );
    }

    /**
     * Creates an UNREGISTER message for a server.
     *
     * @param sourceProxyId the proxy ID
     * @param serverName the server name to unregister
     * @return a new UNREGISTER message
     */
    @Nonnull
    public static ServerListMessage unregister(
            @Nonnull String sourceProxyId,
            @Nonnull String serverName) {
        return new ServerListMessage(
                sourceProxyId,
                Instant.now(),
                Type.UNREGISTER,
                serverName,
                null,
                0,
                false
        );
    }

    @Override
    @Nonnull
    public String messageType() {
        return "server_list";
    }
}
