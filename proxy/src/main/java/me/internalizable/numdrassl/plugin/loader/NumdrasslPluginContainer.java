package me.internalizable.numdrassl.plugin.loader;

import me.internalizable.numdrassl.api.plugin.PluginContainer;
import me.internalizable.numdrassl.api.plugin.PluginDescription;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of {@link PluginContainer}.
 *
 * <p>Holds a loaded plugin instance along with its metadata and classloader.
 * Implements {@link Closeable} to properly release the classloader on unload.</p>
 */
public final class NumdrasslPluginContainer implements PluginContainer, Closeable {

    private final PluginDescription description;
    private final Object instance;
    private final Path dataDirectory;
    private final URLClassLoader classLoader;

    public NumdrasslPluginContainer(
            @Nonnull PluginDescription description,
            @Nonnull Object instance,
            @Nonnull Path dataDirectory,
            @Nullable URLClassLoader classLoader) {

        this.description = Objects.requireNonNull(description, "description");
        this.instance = Objects.requireNonNull(instance, "instance");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.classLoader = classLoader; // May be null for built-in plugins
    }

    @Override
    @Nonnull
    public PluginDescription getDescription() {
        return description;
    }

    @Override
    @Nonnull
    public Optional<Object> getInstance() {
        return Optional.of(instance);
    }

    @Override
    @Nonnull
    public Path getDataDirectory() {
        return dataDirectory;
    }

    /**
     * Gets the classloader used to load this plugin.
     *
     * @return the classloader, or null for built-in plugins
     */
    @Nullable
    public URLClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Closes the plugin's classloader, releasing resources.
     *
     * <p>This should be called when the plugin is unloaded to prevent
     * classloader leaks.</p>
     */
    @Override
    public void close() throws IOException {
        if (classLoader != null) {
            classLoader.close();
        }
    }

    @Override
    public String toString() {
        return String.format("PluginContainer{id=%s, version=%s}",
            description.getId(),
            description.getVersion().orElse("unknown"));
    }
}

