package me.internalizable.numdrassl.api.command;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Manages command registration and execution.
 */
public interface CommandManager {

    /**
     * Register a command.
     *
     * @param plugin the plugin registering the command
     * @param command the command to register
     * @param aliases additional aliases for the command
     */
    void register(@Nonnull Object plugin, @Nonnull Command command, @Nonnull String... aliases);

    /**
     * Register a simple command with a handler.
     *
     * @param plugin the plugin registering the command
     * @param name the command name
     * @param handler the handler for the command
     * @param aliases additional aliases for the command
     */
    void register(@Nonnull Object plugin, @Nonnull String name,
                  @Nonnull CommandHandler handler, @Nonnull String... aliases);

    /**
     * Unregister a command by name.
     *
     * @param name the command name
     */
    void unregister(@Nonnull String name);

    /**
     * Unregister all commands registered by a plugin.
     *
     * @param plugin the plugin whose commands should be unregistered
     */
    void unregisterAll(@Nonnull Object plugin);

    /**
     * Check if a command is registered.
     *
     * @param name the command name
     * @return true if registered
     */
    boolean hasCommand(@Nonnull String name);

    /**
     * Get all registered command names.
     *
     * @return a collection of command names
     */
    @Nonnull
    Collection<String> getCommands();

    /**
     * Execute a command as a source.
     *
     * @param source the command source
     * @param commandLine the command line to execute (without leading slash)
     * @return the result of the command
     */
    @Nonnull
    CommandResult execute(@Nonnull CommandSource source, @Nonnull String commandLine);

    /**
     * Execute a command asynchronously as a source.
     *
     * @param source the command source
     * @param commandLine the command line to execute (without leading slash)
     * @return a future that completes with the command result
     */
    @Nonnull
    CompletableFuture<CommandResult> executeAsync(@Nonnull CommandSource source, @Nonnull String commandLine);
}

