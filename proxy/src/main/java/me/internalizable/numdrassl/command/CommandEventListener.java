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

            // Create a command source from the player
            CommandSource source = new PlayerCommandSource(event.getPlayer());

            // Execute the command
            String fullCommand = event.getCommand();
            if (event.getArgs().length > 0) {
                fullCommand += " " + String.join(" ", event.getArgs());
            }

            CommandResult result = commandManager.execute(source, fullCommand);

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

    /**
     * Command source implementation for players.
     * Delegates permission checks to the player's permission function.
     */
    private static class PlayerCommandSource implements CommandSource {
        private final Player player;

        PlayerCommandSource(Player player) {
            this.player = player;
        }

        @Override
        public void sendMessage(@Nonnull String message) {
            player.sendMessage(message);
        }

        @Override
        @Nonnull
        public Tristate getPermissionValue(@Nonnull String permission) {
            return player.getPermissionValue(permission);
        }

        @Override
        public boolean hasPermission(@Nonnull String permission) {
            return player.hasPermission(permission);
        }

        @Override
        @Nonnull
        public PermissionFunction getPermissionFunction() {
            return player.getPermissionFunction();
        }

        @Override
        public void setPermissionFunction(@Nonnull PermissionFunction function) {
            player.setPermissionFunction(function);
        }

        @Override
        @Nonnull
        public java.util.Optional<Player> asPlayer() {
            return java.util.Optional.of(player);
        }

        @Override
        public boolean isConsole() {
            return false;
        }
    }
}

