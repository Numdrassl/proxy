package me.internalizable.numdrassl.command.builtin;

import me.internalizable.numdrassl.api.command.Command;
import me.internalizable.numdrassl.api.command.CommandResult;
import me.internalizable.numdrassl.api.command.CommandSource;
import me.internalizable.numdrassl.server.ProxyCore;
import me.internalizable.numdrassl.session.ProxySession;

import javax.annotation.Nonnull;

/**
 * Built-in sessions command for listing active player sessions.
 */
public class SessionsCommand implements Command {

    private static final String PERMISSION_BASE = "numdrassl.command.sessions";

    private final ProxyCore proxyCore;

    public SessionsCommand(ProxyCore proxyCore) {
        this.proxyCore = proxyCore;
    }

    @Override
    @Nonnull
    public String getName() {
        return "sessions";
    }

    @Override
    public String getDescription() {
        return "List all active player sessions";
    }

    @Override
    public String getPermission() {
        return PERMISSION_BASE;
    }

    @Override
    @Nonnull
    public CommandResult execute(@Nonnull CommandSource source, @Nonnull String[] args) {
        var sessions = proxyCore.getSessionManager().getAllSessions();
        source.sendMessage("Active sessions: " + sessions.size());

        for (ProxySession session : sessions) {
            String playerName = session.getPlayerName() != null ? session.getPlayerName() : "unknown";
            String playerUuid = session.getPlayerUuid() != null ? session.getPlayerUuid().toString() : "unknown";
            String backend = session.getCurrentBackend() != null ? session.getCurrentBackend().getName() : "none";

            source.sendMessage("  - Session " + session.getSessionId() + ": " +
                playerName + " (" + playerUuid + ") -> " + backend);
        }

        return CommandResult.success();
    }
}

