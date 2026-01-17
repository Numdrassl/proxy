package me.internalizable.numdrassl.plugin;

import com.hypixel.hytale.protocol.Packet;
import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.player.TransferResult;
import me.internalizable.numdrassl.api.server.RegisteredServer;
import me.internalizable.numdrassl.session.ProxySession;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of the Player API interface.
 */
public class NumdrasslPlayer implements Player {

    private final ProxySession session;
    private final NumdrasslProxyServer proxyServer;

    public NumdrasslPlayer(ProxySession session, NumdrasslProxyServer proxyServer) {
        this.session = session;
        this.proxyServer = proxyServer;
    }

    @Override
    @Nonnull
    public UUID getUniqueId() {
        UUID uuid = session.getPlayerUuid();
        return uuid != null ? uuid : new UUID(0, 0);
    }

    @Override
    @Nonnull
    public String getUsername() {
        String username = session.getUsername();
        return username != null ? username : "Unknown";
    }

    @Override
    @Nonnull
    public InetSocketAddress getRemoteAddress() {
        return session.getClientAddress();
    }

    @Override
    @Nonnull
    public Optional<RegisteredServer> getCurrentServer() {
        String serverName = session.getCurrentServerName();
        if (serverName != null) {
            return proxyServer.getServer(serverName);
        }
        return Optional.empty();
    }

    @Override
    public boolean isConnected() {
        return session.isActive();
    }

    @Override
    public long getPing() {
        return session.getPing();
    }

    @Override
    public void sendPacket(@Nonnull Object packet) {
        if (packet instanceof Packet) {
            session.sendToClient((Packet) packet);
        }
    }

    @Override
    public void sendPacketToServer(@Nonnull Object packet) {
        if (packet instanceof Packet) {
            session.sendToServer((Packet) packet);
        }
    }

    @Override
    public void sendMessage(@Nonnull String message) {
        session.sendChatMessage(message);
    }

    @Override
    public void disconnect(@Nonnull String reason) {
        session.disconnect(reason);
    }

    @Override
    @Nonnull
    public CompletableFuture<TransferResult> transfer(@Nonnull RegisteredServer server) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                InetSocketAddress address = server.getAddress();
                boolean success = session.transferTo(address.getHostString(), address.getPort());
                if (success) {
                    return TransferResult.success();
                } else {
                    return TransferResult.failure("Transfer failed");
                }
            } catch (Exception e) {
                return TransferResult.failure(e.getMessage());
            }
        });
    }

    @Override
    @Nonnull
    public CompletableFuture<TransferResult> transfer(@Nonnull String serverName) {
        Optional<RegisteredServer> server = proxyServer.getServer(serverName);
        if (server.isPresent()) {
            return transfer(server.get());
        }
        return CompletableFuture.completedFuture(TransferResult.failure("Server not found: " + serverName));
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

    /**
     * Get the underlying proxy session.
     */
    public ProxySession getSession() {
        return session;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NumdrasslPlayer that = (NumdrasslPlayer) o;
        return session.getSessionId() == that.session.getSessionId();
    }

    @Override
    public int hashCode() {
        return Long.hashCode(session.getSessionId());
    }
}

