package me.internalizable.numdrassl.command;

import me.internalizable.numdrassl.api.command.CommandResult;
import me.internalizable.numdrassl.api.command.CommandSource;
import me.internalizable.numdrassl.api.event.EventPriority;
import me.internalizable.numdrassl.api.event.Subscribe;
import me.internalizable.numdrassl.api.event.player.PlayerCommandEvent;
import me.internalizable.numdrassl.api.permission.PermissionFunction;
import me.internalizable.numdrassl.api.permission.Tristate;
import me.internalizable.numdrassl.api.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * Listens for PlayerCommandEvent and executes proxy commands.
 * This bridges the event system with the command manager.
 */
public class CommandEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandEventListener.class);

    private final NumdrasslCommandManager commandManager;

    public CommandEventListener(NumdrasslCommandManager commandManager) {
        this.commandManager = commandManager;
    }

    /**
     * Handle player commands at EARLY priority to give proxy commands
     * precedence over backend commands.
     */
    @Subscribe(priority = EventPriority.EARLY)
    public void onPlayerCommand(PlayerCommandEvent event) {
        String command = event.getCommand();

        // Check if this is a registered proxy command
        if (commandManager.hasCommand(command)) {
            LOGGER.debug("Executing proxy command: /{} for player {}",
                command, event.getPlayer().getUsername());

            // Execute the command
            String fullCommand = event.getCommand();
            if (event.getArgs().length > 0) {
                fullCommand += " " + String.join(" ", event.getArgs());
            }

            CommandResult result = commandManager.execute(event.getPlayer(), fullCommand);

            // Send result message if any
            if (result.getMessage() != null) {
                event.getPlayer().sendMessage(result.getMessage());
            }

            // Don't forward proxy commands to the backend
            event.setForwardToServer(false);

            // If the command failed, log it
            if (!result.isSuccess()) {
                LOGGER.debug("Command /{} failed with status: {}", command, result.getStatus());
            }
        }
    }
}
