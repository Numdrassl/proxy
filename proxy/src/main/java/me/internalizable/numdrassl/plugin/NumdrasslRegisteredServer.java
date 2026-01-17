package me.internalizable.numdrassl.plugin;

import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.server.PingResult;
import me.internalizable.numdrassl.api.server.RegisteredServer;

import javax.annotation.Nonnull;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of the RegisteredServer API interface.
 */
public class NumdrasslRegisteredServer implements RegisteredServer {

    private final String name;
    private final InetSocketAddress address;
    private volatile boolean isDefault;

    public NumdrasslRegisteredServer(String name, InetSocketAddress address) {
        this.name = name;
        this.address = address;
    }

    @Override
    @Nonnull
    public String getName() {
        return name;
    }

    @Override
    @Nonnull
    public InetSocketAddress getAddress() {
        return address;
    }

    @Override
    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    @Override
    @Nonnull
    public Collection<Player> getPlayers() {
        // TODO: Track players per server
        return Collections.emptyList();
    }

    @Override
    public int getPlayerCount() {
        return getPlayers().size();
    }

    @Override
    @Nonnull
    public CompletableFuture<PingResult> ping() {
        // TODO: Implement actual server ping
        return CompletableFuture.completedFuture(PingResult.success(-1));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NumdrasslRegisteredServer that = (NumdrasslRegisteredServer) o;
        return name.equalsIgnoreCase(that.name);
    }

    @Override
    public int hashCode() {
        return name.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return "RegisteredServer{" + name + " @ " + address + "}";
    }
}

