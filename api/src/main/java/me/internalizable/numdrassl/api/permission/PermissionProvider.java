package me.internalizable.numdrassl.api.permission;

import me.internalizable.numdrassl.api.player.Player;

import javax.annotation.Nonnull;

/**
 * Provider interface for external permission plugins.
 *
 * <p>Permission plugins like LuckPerms should implement this interface
 * and register it with the proxy to provide permission checking.</p>
 *
 * <h2>Registration</h2>
 * <pre>{@code
 * public class LuckPermsProvider implements PermissionProvider {
 *     @Override
 *     public PermissionFunction createFunction(Player player) {
 *         User user = luckPerms.getUserManager().getUser(player.getUniqueId());
 *         return permission -> {
 *             if (user == null) return Tristate.UNDEFINED;
 *             return Tristate.fromBoolean(user.getCachedData()
 *                 .getPermissionData().checkPermission(permission).asBoolean());
 *         };
 *     }
 * }
 *
 * // Register with proxy
 * Numdrassl.getProxy().getPermissionManager().setProvider(new LuckPermsProvider());
 * }</pre>
 *
 * <h2>Lifecycle</h2>
 * <p>The {@link #createFunction(Player)} method is called when a player
 * connects to create their permission function. The function may be
 * cached and reused for the duration of the session.</p>
 *
 * @see PermissionManager
 * @see PermissionFunction
 */
public interface PermissionProvider {

    /**
     * Creates a permission function for the given player.
     *
     * <p>This method is called when a player connects to create their
     * permission checking function. The returned function will be used
     * for all permission checks for this player.</p>
     *
     * @param player the player to create a function for
     * @return the permission function for this player
     */
    @Nonnull
    PermissionFunction createFunction(@Nonnull Player player);

    /**
     * Called when this provider is registered.
     *
     * <p>Override this to perform initialization when the provider
     * is installed.</p>
     */
    default void onRegister() {
    }

    /**
     * Called when this provider is unregistered.
     *
     * <p>Override this to perform cleanup when the provider
     * is removed.</p>
     */
    default void onUnregister() {
    }
}

