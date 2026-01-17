package me.internalizable.numdrassl.command.builtin;

import me.internalizable.numdrassl.api.command.Command;
import me.internalizable.numdrassl.api.command.CommandResult;
import me.internalizable.numdrassl.api.command.CommandSource;
import me.internalizable.numdrassl.server.ProxyCore;

import javax.annotation.Nonnull;

/**
 * Built-in stop command for shutting down the proxy server.
 */
public class StopCommand implements Command {

    private final ProxyCore proxyCore;

    public StopCommand(ProxyCore proxyCore) {
        this.proxyCore = proxyCore;
    }

    @Override
    @Nonnull
    public String getName() {
        return "stop";
    }

    @Override
    public String getDescription() {
        return "Stop the proxy server";
    }

    @Override
    @Nonnull
    public CommandResult execute(@Nonnull CommandSource source, @Nonnull String[] args) {
        source.sendMessage("Stopping proxy server...");

        // Stop on a separate thread to allow the command to complete
        new Thread(() -> {
            try {
                Thread.sleep(100); // Small delay to let the message be sent
            } catch (InterruptedException ignored) {}
            proxyCore.stop();
            System.exit(0);
        }, "Shutdown-Thread").start();

        return CommandResult.success();
    }
}

