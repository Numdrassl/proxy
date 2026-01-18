package me.internalizable.numdrassl.api.player;

import me.internalizable.numdrassl.api.chat.ChatMessageBuilder;
import me.internalizable.numdrassl.api.permission.PermissionSubject;
import me.internalizable.numdrassl.api.server.RegisteredServer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a player connected to the proxy.
 *
 * <p>Players are permission subjects and can be checked for permissions
 * using {@link #hasPermission(String)} or {@link #getPermissionValue(String)}.</p>
 */
public interface Player extends PermissionSubject {

    /**
     * Get the player's UUID.
     *
     * @return the player's UUID
     */
    @Nonnull
    UUID getUniqueId();

    /**
     * Get the player's username.
     *
     * @return the username
     */
    @Nonnull
    String getUsername();

    /**
     * Get the player's remote address.
     *
     * @return the address the player connected from
     */
    @Nonnull
    InetSocketAddress getRemoteAddress();

    /**
     * Get the server the player is currently connected to.
     *
     * @return the current server, or empty if not connected to any backend
     */
    @Nonnull
    Optional<RegisteredServer> getCurrentServer();

    /**
     * Check if the player is currently connected.
     *
     * @return true if connected
     */
    boolean isConnected();

    /**
     * Get the player's ping/latency in milliseconds.
     *
     * @return the ping in milliseconds, or -1 if unknown
     */
    long getPing();

    /**
     * Send a packet to the player's client.
     *
     * @param packet the packet to send
     */
    void sendPacket(@Nonnull Object packet);

    /**
     * Send a packet to the backend server on behalf of this player.
     *
     * @param packet the packet to send
     */
    void sendPacketToServer(@Nonnull Object packet);

    /**
     * Send a chat message to the player.
     *
     * @param message the message to send
     */
    void sendMessage(@Nonnull String message);

    /**
     * Send a formatted chat message to the player.
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * player.sendMessage(ChatMessageBuilder.create()
     *     .green("[Success] ")
     *     .white("Operation completed!"));
     * }</pre>
     *
     * @param builder the ChatMessageBuilder with the formatted message
     */
    void sendMessage(@Nonnull ChatMessageBuilder builder);

    /**
     * Disconnect the player with a reason.
     *
     * @param reason the disconnect reason to show the player
     */
    void disconnect(@Nonnull String reason);

    /**
     * Transfer the player to a different backend server.
     *
     * @param server the server to transfer to
     * @return a future that completes when the transfer is initiated
     */
    @Nonnull
    CompletableFuture<TransferResult> transfer(@Nonnull RegisteredServer server);

    /**
     * Transfer the player to a different backend server by name.
     *
     * @param serverName the name of the server to transfer to
     * @return a future that completes when the transfer is initiated
     */
    @Nonnull
    CompletableFuture<TransferResult> transfer(@Nonnull String serverName);

    /**
     * Get the protocol hash the player is using.
     *
     * @return the protocol hash, or null if unknown
     */
    @Nullable
    String getProtocolHash();

    /**
     * Get the session ID for this player's connection.
     *
     * @return the internal session ID
     */
    long getSessionId();
}

