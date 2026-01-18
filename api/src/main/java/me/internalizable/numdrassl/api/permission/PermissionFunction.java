package me.internalizable.numdrassl.api.permission;

import javax.annotation.Nonnull;

/**
 * Functional interface for checking permissions.
 *
 * <p>This is the core hook point for permission plugins like LuckPerms.
 * A permission function takes a permission string and returns a {@link Tristate}
 * indicating whether the permission is granted, denied, or undefined.</p>
 *
 * <p>Example implementation:</p>
 * <pre>{@code
 * PermissionFunction func = permission -> {
 *     if (permission.startsWith("admin.")) {
 *         return Tristate.FALSE; // Deny all admin permissions
 *     }
 *     return Tristate.UNDEFINED; // Defer to default
 * };
 * }</pre>
 *
 * @see PermissionProvider
 * @see PermissionSubject
 */
@FunctionalInterface
public interface PermissionFunction {

    /**
     * A permission function that always returns {@link Tristate#UNDEFINED}.
     */
    PermissionFunction ALWAYS_UNDEFINED = permission -> Tristate.UNDEFINED;

    /**
     * A permission function that always returns {@link Tristate#TRUE}.
     * Useful for console or admin bypass.
     */
    PermissionFunction ALWAYS_TRUE = permission -> Tristate.TRUE;

    /**
     * A permission function that always returns {@link Tristate#FALSE}.
     */
    PermissionFunction ALWAYS_FALSE = permission -> Tristate.FALSE;

    /**
     * Gets the permission value for the given permission string.
     *
     * @param permission the permission to check (e.g., "numdrassl.command.server")
     * @return the tristate permission value
     */
    @Nonnull
    Tristate getPermissionValue(@Nonnull String permission);

    /**
     * Creates a new permission function that checks this function first,
     * and falls back to the other function if this returns {@link Tristate#UNDEFINED}.
     *
     * @param other the fallback function
     * @return a combined permission function
     */
    @Nonnull
    default PermissionFunction withFallback(@Nonnull PermissionFunction other) {
        return permission -> {
            Tristate result = this.getPermissionValue(permission);
            if (result == Tristate.UNDEFINED) {
                return other.getPermissionValue(permission);
            }
            return result;
        };
    }
}

