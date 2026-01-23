package me.internalizable.numdrassl.api.messaging.backend;
import javax.annotation.Nonnull;
import java.util.function.BiConsumer;
/**
 * Service for proxy-to-backend communication.
 *
 * <p>Enables bidirectional messaging between the proxy and backend servers
 * running the Bridge plugin.</p>
 *
 * <!-- TODO: Implement via Redis pub/sub -->
 */
public interface BackendMessagingService {
    /**
     * Sends a message to a specific backend server.
     *
     * @param serverName the name of the backend server
     * @param channel the channel to send on
     * @param data the message data
     * @return true if the message was queued for delivery
     */
    boolean sendToBackend(@Nonnull String serverName, @Nonnull String channel, @Nonnull byte[] data);
    /**
     * Broadcasts a message to all backend servers.
     *
     * @param channel the channel to send on
     * @param data the message data
     */
    void broadcastToBackends(@Nonnull String channel, @Nonnull byte[] data);
    /**
     * Registers a handler for messages from backends.
     *
     * @param channel the channel to listen on
     * @param handler the handler that receives (serverName, data)
     */
    void registerHandler(@Nonnull String channel, @Nonnull BiConsumer<String, byte[]> handler);
    /**
     * Unregisters a handler for messages from backends.
     *
     * @param channel the channel to stop listening on
     */
    void unregisterHandler(@Nonnull String channel);
}
