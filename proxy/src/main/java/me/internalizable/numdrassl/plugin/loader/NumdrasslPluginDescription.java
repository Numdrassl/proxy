package me.internalizable.numdrassl.plugin.loader;

import me.internalizable.numdrassl.api.plugin.Plugin;
import me.internalizable.numdrassl.api.plugin.PluginDependency;
import me.internalizable.numdrassl.api.plugin.PluginDescription;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Implementation of {@link PluginDescription} based on {@link Plugin} annotation.
 *
 * <p>All collections are immutable to prevent external modification.</p>
 */
public final class NumdrasslPluginDescription implements PluginDescription {

    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final List<String> authors;
    private final List<PluginDependency> dependencies;
    private final String mainClass;

    public NumdrasslPluginDescription(@Nonnull Plugin annotation, @Nonnull String mainClass) {
        Objects.requireNonNull(annotation, "annotation");
        Objects.requireNonNull(mainClass, "mainClass");

        this.id = annotation.id().toLowerCase();
        this.name = annotation.name().isEmpty() ? annotation.id() : annotation.name();
        this.version = emptyToNull(annotation.version());
        this.description = emptyToNull(annotation.description());
        this.authors = List.copyOf(Arrays.asList(annotation.authors()));
        this.mainClass = mainClass;
        this.dependencies = buildDependencies(annotation);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static List<PluginDependency> buildDependencies(Plugin annotation) {
        // Combine hard and soft dependencies
        List<PluginDependency> hard = Arrays.stream(annotation.dependencies())
            .map(id -> new PluginDependency(id, false))
            .toList();

        List<PluginDependency> soft = Arrays.stream(annotation.softDependencies())
            .map(id -> new PluginDependency(id, true))
            .toList();

        return Stream.concat(hard.stream(), soft.stream())
            .toList();
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
        return authors; // Already immutable
    }

    @Override
    @Nonnull
    public List<PluginDependency> getDependencies() {
        return dependencies; // Already immutable
    }

    @Override
    @Nonnull
    public Optional<String> getMainClass() {
        return Optional.of(mainClass);
    }

    @Override
    public String toString() {
        return String.format("PluginDescription{id=%s, name=%s, version=%s}",
            id, name, version);
    }
}

