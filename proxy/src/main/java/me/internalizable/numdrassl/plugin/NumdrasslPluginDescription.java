package me.internalizable.numdrassl.plugin;

import me.internalizable.numdrassl.api.plugin.Plugin;
import me.internalizable.numdrassl.api.plugin.PluginDependency;
import me.internalizable.numdrassl.api.plugin.PluginDescription;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of PluginDescription based on @Plugin annotation.
 */
public class NumdrasslPluginDescription implements PluginDescription {

    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final List<String> authors;
    private final List<PluginDependency> dependencies;
    private final String mainClass;

    public NumdrasslPluginDescription(Plugin annotation, String mainClass) {
        this.id = annotation.id().toLowerCase();
        this.name = annotation.name().isEmpty() ? annotation.id() : annotation.name();
        this.version = annotation.version().isEmpty() ? null : annotation.version();
        this.description = annotation.description().isEmpty() ? null : annotation.description();
        this.authors = Arrays.asList(annotation.authors());
        this.mainClass = mainClass;

        // Parse dependencies from the string array
        this.dependencies = new ArrayList<>();
        for (String depId : annotation.dependencies()) {
            // Hard dependencies
            dependencies.add(new PluginDependency(depId, false));
        }
        for (String depId : annotation.softDependencies()) {
            // Soft (optional) dependencies
            dependencies.add(new PluginDependency(depId, true));
        }
    }

    @Override
    @Nonnull
    public String getId() {
        return id;
    }

    @Override
    @Nonnull
    public String getName() {
        return name;
    }

    @Override
    @Nonnull
    public Optional<String> getVersion() {
        return Optional.ofNullable(version);
    }

    @Override
    @Nonnull
    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    @Override
    @Nonnull
    public List<String> getAuthors() {
        return authors;
    }

    @Override
    @Nonnull
    public List<PluginDependency> getDependencies() {
        return dependencies;
    }

    @Override
    @Nonnull
    public Optional<String> getMainClass() {
        return Optional.of(mainClass);
    }
}

