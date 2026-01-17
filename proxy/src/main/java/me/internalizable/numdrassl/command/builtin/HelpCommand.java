package me.internalizable.numdrassl.command.builtin;

import me.internalizable.numdrassl.api.command.Command;
import me.internalizable.numdrassl.api.command.CommandResult;
import me.internalizable.numdrassl.api.command.CommandSource;
import me.internalizable.numdrassl.command.NumdrasslCommandManager;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * Built-in help command for listing all available commands.
 */
public class HelpCommand implements Command {

    private final NumdrasslCommandManager commandManager;

    public HelpCommand(NumdrasslCommandManager commandManager) {
        this.commandManager = commandManager;
    }

    @Override
    @Nonnull
    public String getName() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "Show available commands";
    }

    @Override
    @Nonnull
    public CommandResult execute(@Nonnull CommandSource source, @Nonnull String[] args) {
        source.sendMessage("Available commands:");

        Collection<String> commands = commandManager.getCommands();
        for (String cmd : commands) {
            Command command = commandManager.getCommand(cmd);
            if (command != null) {
                String desc = command.getDescription();
                if (desc != null && !desc.isEmpty()) {
                    source.sendMessage("  " + cmd + " - " + desc);
                } else {
                    source.sendMessage("  " + cmd);
                }
            } else {
                source.sendMessage("  " + cmd);
            }
        }

        return CommandResult.success();
    }
}

