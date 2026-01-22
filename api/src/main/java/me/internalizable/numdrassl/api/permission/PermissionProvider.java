package me.internalizable.numdrassl.api.permission;

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
 *     public PermissionFunction createFunction(PermissionSubject subject) {
 *         if (!(subject instanceof Player player)) {
 *             // Return a default function for non-player subjects (e.g., console)
 *             return PermissionFunction.ALWAYS_TRUE;
 *         }
 *
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
 * <h2>Subject Types</h2>
 * <p>The subject passed to {@link #createFunction(PermissionSubject)} can be:</p>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.api.player.Player} - a connected player</li>
 *   <li>Console command source - the server console</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <p>The {@link #createFunction(PermissionSubject)} method is called when a subject
 * needs permissions set up. For players, this happens during connection. The function
 * may be cached and reused for the duration of the session.</p>
 *
 * @see PermissionManager
 * @see PermissionFunction
 * @see PermissionSubject
 */
public interface PermissionProvider {

    /**
     * Creates a permission function for the given subject.
     *
     * <p>This method is called when a subject's permissions need to be set up.
     * The returned function will be used for all permission checks for this subject.</p>
     *
     * <p>Implementations should handle both player and non-player subjects appropriately.
     * Use {@code instanceof} to distinguish between subject types:</p>
     * <pre>{@code
     * if (subject instanceof Player player) {
     *     // Player-specific logic
     * } else {
     *     // Console or other subject types
     * }
     * }</pre>
     *
     * @param subject the permission subject to create a function for
     * @return the permission function for this subject
     */
    @Nonnull
    PermissionFunction createFunction(@Nonnull PermissionSubject subject);

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
