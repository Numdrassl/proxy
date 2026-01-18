package me.internalizable.numdrassl.api.permission;

import me.internalizable.numdrassl.api.player.Player;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Manages permission providers and default permission behavior.
 *
 * <p>The permission manager is the central point for permission plugin
 * integration. External plugins like LuckPerms register their
 * {@link PermissionProvider} here.</p>
 *
 * <h2>Default Behavior</h2>
 * <p>When no permission provider is registered, the default behavior is:</p>
 * <ul>
 *   <li>Console/RCON: All permissions granted</li>
 *   <li>Players: All permissions denied (or based on configured defaults)</li>
 * </ul>
 *
 * <h2>Plugin Integration</h2>
 * <pre>{@code
 * // In your permission plugin's onEnable:
 * PermissionManager manager = Numdrassl.getProxy().getPermissionManager();
 * manager.setProvider(new MyPermissionProvider());
 *
 * // In your permission plugin's onDisable:
 * manager.clearProvider();
 * }</pre>
 *
 * @see PermissionProvider
 * @see PermissionSubject
 */
public interface PermissionManager {

    /**
     * Gets the currently registered permission provider.
     *
     * @return the permission provider, or empty if none registered
     */
    @Nonnull
    Optional<PermissionProvider> getProvider();

    /**
     * Sets the permission provider.
     *
     * <p>Only one provider can be active at a time. Setting a new provider
     * will replace the existing one.</p>
     *
     * @param provider the permission provider
     */
    void setProvider(@Nonnull PermissionProvider provider);

    /**
     * Clears the current permission provider.
     *
     * <p>After calling this, the default permission behavior will be used.</p>
     */
    void clearProvider();

    /**
     * Checks if a permission provider is registered.
     *
     * @return true if a provider is registered
     */
    default boolean hasProvider() {
        return getProvider().isPresent();
    }

    /**
     * Creates a permission function for the given player.
     *
     * <p>If a provider is registered, delegates to it. Otherwise,
     * returns the default permission function.</p>
     *
     * @param player the player
     * @return the permission function for this player
     */
    @Nonnull
    PermissionFunction createFunction(@Nonnull Player player);

    /**
     * Gets the default permission function used when no provider is registered.
     *
     * @return the default permission function
     */
    @Nonnull
    PermissionFunction getDefaultFunction();

    /**
     * Sets the default permission function.
     *
     * <p>This function is used when no permission provider is registered,
     * or when the provider returns {@link Tristate#UNDEFINED}.</p>
     *
     * @param function the default permission function
     */
    void setDefaultFunction(@Nonnull PermissionFunction function);

    /**
     * Registers a default permission value.
     *
     * <p>This sets the default value for a specific permission when no
     * provider is registered or the provider returns UNDEFINED.</p>
     *
     * @param permission the permission string
     * @param defaultValue the default value
     */
    void registerDefaultPermission(@Nonnull String permission, @Nonnull Tristate defaultValue);

    /**
     * Gets the default value for a permission.
     *
     * @param permission the permission string
     * @return the default value, or null if not set
     */
    @Nullable
    Tristate getDefaultPermission(@Nonnull String permission);
}

