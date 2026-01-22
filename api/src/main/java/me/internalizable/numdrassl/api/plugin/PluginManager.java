package me.internalizable.numdrassl.api.plugin;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

/**
 * Manages loading and retrieving plugins.
 */
public interface PluginManager {

    /**
     * Get a plugin by its ID.
     *
     * @param id the plugin ID
     * @return the plugin container, or empty if not found
     */
    @Nonnull
    Optional<PluginContainer> getPlugin(@Nonnull String id);

    /**
     * Get a plugin container from a plugin instance.
     *
     * <p>This is useful when you have a reference to a plugin's main class instance
     * and need to get its container metadata.</p>
     *
     * @param instance the plugin instance
     * @return the plugin container, or empty if not found
     */
    @Nonnull
    Optional<PluginContainer> fromInstance(@Nonnull Object instance);

    /**
     * Get all loaded plugins.
     *
     * @return an unmodifiable collection of all plugins
     */
    @Nonnull
    Collection<PluginContainer> getPlugins();

    /**
     * Check if a plugin is loaded.
     *
     * @param id the plugin ID
     * @return true if the plugin is loaded
     */
    boolean isLoaded(@Nonnull String id);

    /**
     * Get the plugin directory where plugins are loaded from.
     *
     * @return the plugins directory
     */
    @Nonnull
    Path getPluginsDirectory();

    /**
     * Add a path to scan for plugins.
     *
     * @param path the path to add
     */
    void addPluginPath(@Nonnull Path path);

    /**
     * Add a JAR file to a plugin's classpath at runtime.
     *
     * <p>This allows plugins to dynamically load additional dependencies
     * after initialization, such as database drivers or other libraries.</p>
     *
     * @param plugin the plugin instance requesting the classpath addition
     * @param file the path to the JAR file to add
     */
    void addToClasspath(@Nonnull Object plugin, @Nonnull Path file);
}

