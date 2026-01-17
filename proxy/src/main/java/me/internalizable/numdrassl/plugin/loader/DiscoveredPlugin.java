package me.internalizable.numdrassl.plugin.loader;

import me.internalizable.numdrassl.api.plugin.PluginDescription;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a plugin discovered during JAR scanning.
 *
 * <p>Contains all information needed to load and initialize the plugin.</p>
 *
 * @param jarPath the path to the plugin JAR file
 * @param description the plugin description from annotation
 * @param mainClass the fully qualified main class name
 */
public record DiscoveredPlugin(
    @Nonnull Path jarPath,
    @Nonnull PluginDescription description,
    @Nonnull String mainClass
) {
    public DiscoveredPlugin {
        Objects.requireNonNull(jarPath, "jarPath");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(mainClass, "mainClass");
    }

    /**
     * Gets the plugin ID for convenience.
     */
    @Nonnull
    public String getId() {
        return description.getId();
    }
}

