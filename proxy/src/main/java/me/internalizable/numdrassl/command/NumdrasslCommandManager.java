package me.internalizable.numdrassl.command;

import me.internalizable.numdrassl.api.command.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the API CommandManager.
 */
public class NumdrasslCommandManager implements CommandManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NumdrasslCommandManager.class);

    private final Map<String, RegisteredCommand> commands = new ConcurrentHashMap<>();
    private final Map<Object, Set<String>> pluginCommands = new ConcurrentHashMap<>();

    @Override
    public void register(@Nonnull Object plugin, @Nonnull Command command, @Nonnull String... aliases) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(command, "command");

        String name = command.getName().toLowerCase();
        RegisteredCommand registered = new RegisteredCommand(plugin, command);

        // Register primary name
        commands.put(name, registered);
        trackPluginCommand(plugin, name);

        // Register aliases
        for (String alias : aliases) {
            String lowerAlias = alias.toLowerCase();
            commands.put(lowerAlias, registered);
            trackPluginCommand(plugin, lowerAlias);
        }

        LOGGER.info("Registered command: /{} (plugin: {})", name, plugin.getClass().getSimpleName());
    }

    @Override
    public void register(@Nonnull Object plugin, @Nonnull String name,
                         @Nonnull CommandHandler handler, @Nonnull String... aliases) {
        Command command = new SimpleCommand(name, handler);
        register(plugin, command, aliases);
    }

    private void trackPluginCommand(Object plugin, String name) {
        pluginCommands.computeIfAbsent(plugin, k -> ConcurrentHashMap.newKeySet()).add(name);
    }

    @Override
    public void unregister(@Nonnull String name) {
        Objects.requireNonNull(name, "name");
        commands.remove(name.toLowerCase());
    }

    @Override
    public void unregisterAll(@Nonnull Object plugin) {
        Objects.requireNonNull(plugin, "plugin");

        Set<String> names = pluginCommands.remove(plugin);
        if (names != null) {
            for (String name : names) {
                commands.remove(name);
            }
        }
    }

    @Override
    public boolean hasCommand(@Nonnull String name) {
        Objects.requireNonNull(name, "name");
        return commands.containsKey(name.toLowerCase());
    }

    @Override
    @Nonnull
    public Collection<String> getCommands() {
        return Collections.unmodifiableSet(commands.keySet());
    }

    /**
     * Get a command by name.
     *
     * @param name the command name
     * @return the command, or null if not found
     */
    public Command getCommand(@Nonnull String name) {
        RegisteredCommand registered = commands.get(name.toLowerCase());
        return registered != null ? registered.command : null;
    }

    @Override
    @Nonnull
    public CommandResult execute(@Nonnull CommandSource source, @Nonnull String commandLine) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(commandLine, "commandLine");

        // Parse command line
        String[] parts = commandLine.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return CommandResult.failure("No command specified");
        }

        String name = parts[0].toLowerCase();
        String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];

        // Find command
        RegisteredCommand registered = commands.get(name);
        if (registered == null) {
            return CommandResult.notFound();
        }

        Command command = registered.command;

        // Check permission
        String permission = command.getPermission();
        if (permission != null && !source.hasPermission(permission)) {
            return CommandResult.noPermission();
        }

        // Execute
        try {
            return command.execute(source, args);
        } catch (Exception e) {
            LOGGER.error("Error executing command: /{}", name, e);
            return CommandResult.failure("An error occurred while executing the command");
        }
    }

    @Override
    @Nonnull
    public CompletableFuture<CommandResult> executeAsync(@Nonnull CommandSource source, @Nonnull String commandLine) {
        return CompletableFuture.supplyAsync(() -> execute(source, commandLine));
    }

    private static class RegisteredCommand {
        final Object plugin;
        final Command command;

        RegisteredCommand(Object plugin, Command command) {
            this.plugin = plugin;
            this.command = command;
        }
    }

    private static class SimpleCommand implements Command {
        private final String name;
        private final CommandHandler handler;

        SimpleCommand(String name, CommandHandler handler) {
            this.name = name;
            this.handler = handler;
        }

        @Override
        @Nonnull
        public String getName() {
            return name;
        }

        @Override
        @Nonnull
        public CommandResult execute(@Nonnull CommandSource source, @Nonnull String[] args) {
            return handler.handle(source, args);
        }
    }
}

