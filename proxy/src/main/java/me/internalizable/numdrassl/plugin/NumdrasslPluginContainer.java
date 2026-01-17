package me.internalizable.numdrassl.plugin;

import me.internalizable.numdrassl.api.plugin.PluginContainer;
import me.internalizable.numdrassl.api.plugin.PluginDescription;

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Implementation of PluginContainer.
 */
public class NumdrasslPluginContainer implements PluginContainer, Closeable {

    private final PluginDescription description;
    private final Object instance;
    private final Path dataDirectory;
    private final URLClassLoader classLoader;

    public NumdrasslPluginContainer(PluginDescription description, Object instance,
                                     Path dataDirectory, URLClassLoader classLoader) {
        this.description = description;
        this.instance = instance;
        this.dataDirectory = dataDirectory;
        this.classLoader = classLoader;
    }

    @Override
    @Nonnull
    public PluginDescription getDescription() {
        return description;
    }

    @Override
    @Nonnull
    public Optional<Object> getInstance() {
        return Optional.ofNullable(instance);
    }

    @Override
    @Nonnull
    public Path getDataDirectory() {
        return dataDirectory;
    }

    @Override
    public void close() throws IOException {
        if (classLoader != null) {
            classLoader.close();
        }
    }
}

