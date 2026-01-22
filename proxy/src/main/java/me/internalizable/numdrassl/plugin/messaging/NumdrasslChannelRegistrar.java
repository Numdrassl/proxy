package me.internalizable.numdrassl.plugin.messaging;

import me.internalizable.numdrassl.api.plugin.messaging.ChannelIdentifier;
import me.internalizable.numdrassl.api.plugin.messaging.ChannelRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link ChannelRegistrar} for managing plugin message channels.
 */
public final class NumdrasslChannelRegistrar implements ChannelRegistrar {

    private static final Logger LOGGER = LoggerFactory.getLogger(NumdrasslChannelRegistrar.class);

    private final Set<ChannelIdentifier> channels = ConcurrentHashMap.newKeySet();

    @Override
    public void register(@Nonnull ChannelIdentifier channel) {
        Objects.requireNonNull(channel, "channel");
        if (channels.add(channel)) {
            LOGGER.debug("Registered plugin message channel: {}", channel.getId());
        }
    }

    @Override
    public void register(@Nonnull ChannelIdentifier... channels) {
        for (ChannelIdentifier channel : channels) {
            register(channel);
        }
    }

    @Override
    public void unregister(@Nonnull ChannelIdentifier channel) {
        Objects.requireNonNull(channel, "channel");
        if (channels.remove(channel)) {
            LOGGER.debug("Unregistered plugin message channel: {}", channel.getId());
        }
    }

    @Override
    public void unregister(@Nonnull ChannelIdentifier... channels) {
        for (ChannelIdentifier channel : channels) {
            unregister(channel);
        }
    }

    @Override
    public boolean isRegistered(@Nonnull ChannelIdentifier channel) {
        Objects.requireNonNull(channel, "channel");
        return channels.contains(channel);
    }

    @Override
    @Nonnull
    public Collection<ChannelIdentifier> getRegisteredChannels() {
        return Collections.unmodifiableSet(channels);
    }
}

