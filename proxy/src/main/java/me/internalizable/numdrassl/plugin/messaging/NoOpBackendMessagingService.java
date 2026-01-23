package me.internalizable.numdrassl.plugin.messaging;

import me.internalizable.numdrassl.api.messaging.backend.BackendMessagingService;

import javax.annotation.Nonnull;
import java.util.function.BiConsumer;

/**
 * No-op implementation of {@link BackendMessagingService}.
 *
 * <p>This is a placeholder until Redis-based backend messaging is implemented.</p>
 */
public final class NoOpBackendMessagingService implements BackendMessagingService {

    public static final NoOpBackendMessagingService INSTANCE = new NoOpBackendMessagingService();

    private NoOpBackendMessagingService() {
    }

    @Override
    public boolean sendToBackend(@Nonnull String serverName, @Nonnull String channel, @Nonnull byte[] data) {
        // TODO: Implement via Redis pub/sub
        return false;
    }

    @Override
    public void broadcastToBackends(@Nonnull String channel, @Nonnull byte[] data) {
        // TODO: Implement via Redis pub/sub
    }

    @Override
    public void registerHandler(@Nonnull String channel, @Nonnull BiConsumer<String, byte[]> handler) {
        // TODO: Implement via Redis pub/sub
    }

    @Override
    public void unregisterHandler(@Nonnull String channel) {
        // TODO: Implement via Redis pub/sub
    }
}

