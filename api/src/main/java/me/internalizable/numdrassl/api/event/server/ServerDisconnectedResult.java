package me.internalizable.numdrassl.api.event.server;

import me.internalizable.numdrassl.api.server.RegisteredServer;

import javax.annotation.Nullable;

public class ServerDisconnectedResult {

    private final RegisteredServer server;
    private final String disconnectReason;

    private RegisteredServer fallbackServer;

    public ServerDisconnectedResult(@Nullable RegisteredServer server, @Nullable String disconnectReason, RegisteredServer fallbackServer) {
        this.server = server;
        this.disconnectReason = disconnectReason;
        this.fallbackServer = fallbackServer;
    }

    public RegisteredServer getFallbackServer() {
        return this.fallbackServer;
    }

    public RegisteredServer getPreviousServer() {
        return server;
    }

    public String getDisconnectReason() {
        return disconnectReason;
    }
}
