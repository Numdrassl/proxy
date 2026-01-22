package me.internalizable.numdrassl.api.plugin.messaging;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Simple implementation of {@link ChannelIdentifier}.
 */
final class SimpleChannelIdentifier implements ChannelIdentifier {

    private final String namespace;
    private final String name;
    private final String id;

    SimpleChannelIdentifier(@Nonnull String namespace, @Nonnull String name) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.name = Objects.requireNonNull(name, "name");
        this.id = namespace + ":" + name;
    }

    @Override
    @Nonnull
    public String getNamespace() {
        return namespace;
    }

    @Override
    @Nonnull
    public String getName() {
        return name;
    }

    @Override
    @Nonnull
    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChannelIdentifier that)) return false;
        return id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}

