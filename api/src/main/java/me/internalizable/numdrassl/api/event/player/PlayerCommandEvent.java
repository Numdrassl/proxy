package me.internalizable.numdrassl.api.event.player;

import me.internalizable.numdrassl.api.event.Cancellable;
import me.internalizable.numdrassl.api.player.Player;

import javax.annotation.Nonnull;

/**
 * Event fired when a player executes a command (chat message starting with /).
 * This is fired BEFORE the command is processed.
 *
 * <p>This event is cancellable. If cancelled, the command will not be executed.</p>
 * <p>If the command is handled by a plugin, set cancelled to true to prevent
 * the command from being sent to the backend server.</p>
 */
public class PlayerCommandEvent implements Cancellable {

    private final Player player;
    private String command;
    private String[] args;
    private boolean cancelled;
    private boolean forwardToServer;

    public PlayerCommandEvent(@Nonnull Player player, @Nonnull String commandLine) {
        this.player = player;
        parseCommand(commandLine);
        this.cancelled = false;
        this.forwardToServer = true;
    }

    private void parseCommand(String commandLine) {
        // Remove leading slash if present
        if (commandLine.startsWith("/")) {
            commandLine = commandLine.substring(1);
        }

        String[] parts = commandLine.split(" ", 2);
        this.command = parts[0].toLowerCase();

        if (parts.length > 1) {
            this.args = parts[1].split(" ");
        } else {
            this.args = new String[0];
        }
    }

    /**
     * Get the player who executed the command.
     *
     * @return the player
     */
    @Nonnull
    public Player getPlayer() {
        return player;
    }

    /**
     * Get the command name (without leading slash).
     *
     * @return the command name
     */
    @Nonnull
    public String getCommand() {
        return command;
    }

    /**
     * Get the command arguments.
     *
     * @return the arguments array
     */
    @Nonnull
    public String[] getArgs() {
        return args;
    }

    /**
     * Get the full command line (with leading slash).
     *
     * @return the full command line
     */
    @Nonnull
    public String getCommandLine() {
        if (args.length == 0) {
            return "/" + command;
        }
        return "/" + command + " " + String.join(" ", args);
    }

    /**
     * Check if this command should be forwarded to the backend server.
     * Set to false if the proxy handles this command.
     *
     * @return true if should forward to server
     */
    public boolean shouldForwardToServer() {
        return forwardToServer;
    }

    /**
     * Set whether this command should be forwarded to the backend server.
     *
     * @param forward true to forward, false to handle at proxy level
     */
    public void setForwardToServer(boolean forward) {
        this.forwardToServer = forward;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}

