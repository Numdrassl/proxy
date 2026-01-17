package me.internalizable.numdrassl.plugin.player;

import com.hypixel.hytale.protocol.Packet;
import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.player.TransferResult;
import me.internalizable.numdrassl.api.server.RegisteredServer;
import me.internalizable.numdrassl.plugin.NumdrasslProxy;
import me.internalizable.numdrassl.session.ProxySession;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of the {@link Player} API interface.
 *
 * <p>Wraps a {@link ProxySession} and provides a clean API for plugins to interact
 * with connected players.</p>
 */
public final class NumdrasslPlayer implements Player {

    private static final UUID UNKNOWN_UUID = new UUID(0, 0);
    private static final String UNKNOWN_USERNAME = "Unknown";

    private final ProxySession session;
    private final NumdrasslProxy proxy;

    public NumdrasslPlayer(@Nonnull ProxySession session, @Nonnull NumdrasslProxy proxy) {
        this.session = Objects.requireNonNull(session, "session");
        this.proxy = Objects.requireNonNull(proxy, "proxy");
    }

    // ==================== Identity ====================

    @Override
    @Nonnull
    public UUID getUniqueId() {
        UUID uuid = session.getPlayerUuid();
        return uuid != null ? uuid : UNKNOWN_UUID;
    }

    @Override
    @Nonnull
    public String getUsername() {
        String username = session.getUsername();
        return username != null ? username : UNKNOWN_USERNAME;
    }

    @Override
    @Nonnull
    public InetSocketAddress getRemoteAddress() {
        return session.getClientAddress();
    }

    @Override
    @Nullable
    public String getProtocolHash() {
        return session.getProtocolHash();
    }

    @Override
    public long getSessionId() {
        return session.getSessionId();
    }

    // ==================== Connection State ====================

    @Override
    @Nonnull
    public Optional<RegisteredServer> getCurrentServer() {
        String serverName = session.getCurrentServerName();
        return serverName != null ? proxy.getServer(serverName) : Optional.empty();
    }

    @Override
    public boolean isConnected() {
        return session.isActive();
    }

    @Override
    public long getPing() {
        return session.getPing();
    }

    // ==================== Packet Sending ====================

    @Override
    public void sendPacket(@Nonnull Object packet) {
        Objects.requireNonNull(packet, "packet");
        if (packet instanceof Packet p) {
            session.sendToClient(p);
        }
    }

    @Override
    public void sendPacketToServer(@Nonnull Object packet) {
        Objects.requireNonNull(packet, "packet");
        if (packet instanceof Packet p) {
            session.sendToServer(p);
        }
    }

    @Override
    public void sendMessage(@Nonnull String message) {
        Objects.requireNonNull(message, "message");
        session.sendChatMessage(message);
    }

    // ==================== Connection Management ====================

    @Override
    public void disconnect(@Nonnull String reason) {
        Objects.requireNonNull(reason, "reason");
        session.disconnect(reason);
    }

    @Override
    @Nonnull
    public CompletableFuture<TransferResult> transfer(@Nonnull RegisteredServer server) {
        Objects.requireNonNull(server, "server");

        return CompletableFuture.supplyAsync(() -> {
            try {
                InetSocketAddress address = server.getAddress();
                boolean success = session.transferTo(address.getHostString(), address.getPort());
                return success
                    ? TransferResult.success()
                    : TransferResult.failure("Transfer failed");
            } catch (Exception e) {
                return TransferResult.failure(e.getMessage());
            }
        });
    }

    @Override
    @Nonnull
    public CompletableFuture<TransferResult> transfer(@Nonnull String serverName) {
        Objects.requireNonNull(serverName, "serverName");

        return proxy.getServer(serverName)
            .map(this::transfer)
            .orElseGet(() -> CompletableFuture.completedFuture(
                TransferResult.failure("Server not found: " + serverName)
            ));
    }

    // ==================== Internal Access ====================

    /**
     * Gets the underlying proxy session.
     */
    @Nonnull
    public ProxySession getSession() {
        return session;
    }

    // ==================== Object Methods ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NumdrasslPlayer that)) return false;
        return session.getSessionId() == that.session.getSessionId();
    }

    @Override
    public int hashCode() {
        return Long.hashCode(session.getSessionId());
    }

    @Override
    public String toString() {
        return String.format("Player{name=%s, uuid=%s, session=%d}",
            getUsername(), getUniqueId(), session.getSessionId());
    }
}

