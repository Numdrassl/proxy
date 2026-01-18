package me.internalizable.numdrassl.api.permission;

import javax.annotation.Nonnull;

/**
 * Represents an object that can have permissions.
 *
 * <p>This interface is implemented by {@link me.internalizable.numdrassl.api.player.Player}
 * and {@link me.internalizable.numdrassl.api.command.CommandSource} to provide
 * a unified permission checking API.</p>
 *
 * <h2>Permission Checking</h2>
 * <p>Permissions are checked through a {@link PermissionFunction} which can be
 * provided by external plugins like LuckPerms. The function returns a {@link Tristate}
 * value:</p>
 * <ul>
 *   <li>{@link Tristate#TRUE} - Permission explicitly granted</li>
 *   <li>{@link Tristate#FALSE} - Permission explicitly denied</li>
 *   <li>{@link Tristate#UNDEFINED} - Not set, uses default behavior</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * if (player.hasPermission("numdrassl.command.server")) {
 *     // Execute command
 * }
 *
 * // For more control:
 * Tristate state = player.getPermissionValue("some.permission");
 * if (state == Tristate.TRUE) {
 *     // Explicitly granted
 * } else if (state == Tristate.FALSE) {
 *     // Explicitly denied
 * } else {
 *     // Undefined - use default
 * }
 * }</pre>
 *
 * @see PermissionFunction
 * @see PermissionProvider
 */
public interface PermissionSubject {

    /**
     * Gets the tristate permission value for the given permission.
     *
     * <p>This method returns the raw permission value without any
     * default behavior applied.</p>
     *
     * @param permission the permission to check
     * @return the tristate value
     */
    @Nonnull
    Tristate getPermissionValue(@Nonnull String permission);

    /**
     * Checks if this subject has the given permission.
     *
     * <p>This is a convenience method that converts the {@link Tristate}
     * result to a boolean. {@link Tristate#UNDEFINED} is treated as
     * the default value (typically false for players, true for console).</p>
     *
     * @param permission the permission to check
     * @return true if the subject has the permission
     */
    default boolean hasPermission(@Nonnull String permission) {
        return getPermissionValue(permission).asBoolean();
    }

    /**
     * Gets the permission function for this subject.
     *
     * <p>The permission function is used to resolve all permission checks.
     * It may be provided by an external permission plugin.</p>
     *
     * @return the permission function
     */
    @Nonnull
    PermissionFunction getPermissionFunction();

    /**
     * Sets a new permission function for this subject.
     *
     * <p>This is typically called by permission plugins to install their
     * permission checking logic.</p>
     *
     * @param function the new permission function
     */
    void setPermissionFunction(@Nonnull PermissionFunction function);
}

