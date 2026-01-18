package me.internalizable.numdrassl.plugin.permission;

import me.internalizable.numdrassl.api.permission.PermissionFunction;
import me.internalizable.numdrassl.api.permission.PermissionManager;
import me.internalizable.numdrassl.api.permission.PermissionProvider;
import me.internalizable.numdrassl.api.permission.Tristate;
import me.internalizable.numdrassl.api.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of the {@link PermissionManager} interface.
 *
 * <p>Manages permission providers and default permission behavior for the proxy.
 * By default, uses a file-based permission provider that stores permissions on disk.</p>
 *
 * @see FilePermissionProvider
 */
public final class NumdrasslPermissionManager implements PermissionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NumdrasslPermissionManager.class);

    private final AtomicReference<PermissionProvider> provider = new AtomicReference<>();
    private final AtomicReference<PermissionFunction> defaultFunction = new AtomicReference<>(
        PermissionFunction.ALWAYS_UNDEFINED
    );
    private final Map<String, Tristate> defaultPermissions = new ConcurrentHashMap<>();
    private final FilePermissionProvider fileProvider;

    // ==================== Construction ====================

    /**
     * Creates a new permission manager with the default file-based provider.
     */
    public NumdrasslPermissionManager() {
        this(Paths.get("data"));
    }

    /**
     * Creates a new permission manager with a custom data directory.
     *
     * @param dataDirectory the directory to store permission files
     */
    public NumdrasslPermissionManager(@Nonnull Path dataDirectory) {
        this.fileProvider = new FilePermissionProvider(dataDirectory);
        // Register the default file-based provider
        setProvider(fileProvider);
    }

    /**
     * Gets the default file-based permission provider.
     *
     * <p>This can be used to directly manage permissions via the file provider's API.</p>
     *
     * @return the default file permission provider
     */
    @Nonnull
    public FilePermissionProvider getFileProvider() {
        return fileProvider;
    }

    // ==================== Provider Management ====================

    @Override
    @Nonnull
    public Optional<PermissionProvider> getProvider() {
        return Optional.ofNullable(provider.get());
    }

    @Override
    public void setProvider(@Nonnull PermissionProvider newProvider) {
        Objects.requireNonNull(newProvider, "provider");

        PermissionProvider old = provider.getAndSet(newProvider);
        if (old != null) {
            LOGGER.info("Replacing permission provider {} with {}",
                old.getClass().getSimpleName(),
                newProvider.getClass().getSimpleName());
            old.onUnregister();
        } else {
            LOGGER.info("Registered permission provider: {}",
                newProvider.getClass().getSimpleName());
        }

        newProvider.onRegister();
    }

    @Override
    public void clearProvider() {
        PermissionProvider old = provider.getAndSet(null);
        if (old != null) {
            LOGGER.info("Unregistered permission provider: {}",
                old.getClass().getSimpleName());
            old.onUnregister();
        }
    }

    // ==================== Permission Function Creation ====================

    @Override
    @Nonnull
    public PermissionFunction createFunction(@Nonnull Player player) {
        Objects.requireNonNull(player, "player");

        PermissionProvider currentProvider = provider.get();
        PermissionFunction baseFunction;

        if (currentProvider != null) {
            baseFunction = currentProvider.createFunction(player);
        } else {
            baseFunction = PermissionFunction.ALWAYS_UNDEFINED;
        }

        // Chain with defaults
        return baseFunction.withFallback(this::resolveDefault);
    }

    private Tristate resolveDefault(String permission) {
        // Check registered defaults
        Tristate registered = defaultPermissions.get(permission);
        if (registered != null) {
            return registered;
        }

        // Fall back to default function
        return defaultFunction.get().getPermissionValue(permission);
    }

    // ==================== Default Permission Management ====================

    @Override
    @Nonnull
    public PermissionFunction getDefaultFunction() {
        return defaultFunction.get();
    }

    @Override
    public void setDefaultFunction(@Nonnull PermissionFunction function) {
        Objects.requireNonNull(function, "function");
        defaultFunction.set(function);
    }

    @Override
    public void registerDefaultPermission(@Nonnull String permission, @Nonnull Tristate defaultValue) {
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(defaultValue, "defaultValue");

        if (defaultValue == Tristate.UNDEFINED) {
            defaultPermissions.remove(permission);
        } else {
            defaultPermissions.put(permission, defaultValue);
        }
    }

    @Override
    @Nullable
    public Tristate getDefaultPermission(@Nonnull String permission) {
        Objects.requireNonNull(permission, "permission");
        return defaultPermissions.get(permission);
    }

    // ==================== Lifecycle ====================

    /**
     * Shuts down the permission manager and clears the provider.
     */
    public void shutdown() {
        clearProvider();
        defaultPermissions.clear();
    }
}

