package me.internalizable.numdrassl.api.event.permission;

import me.internalizable.numdrassl.api.permission.PermissionFunction;
import me.internalizable.numdrassl.api.player.Player;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Event fired when a player's permission function needs to be set up.
 *
 * <p>This event is fired during the login process, giving permission plugins
 * the opportunity to provide a custom {@link PermissionFunction} for the player.</p>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @Subscribe
 * public void onPermissionSetup(PermissionSetupEvent event) {
 *     Player player = event.getPlayer();
 *     User user = luckPerms.getUserManager().getUser(player.getUniqueId());
 *
 *     if (user != null) {
 *         event.setPermissionFunction(permission -> {
 *             return Tristate.fromBoolean(user.getCachedData()
 *                 .getPermissionData().checkPermission(permission).asBoolean());
 *         });
 *     }
 * }
 * }</pre>
 *
 * @see PermissionFunction
 * @see me.internalizable.numdrassl.api.permission.PermissionProvider
 */
public class PermissionSetupEvent {

    private final Player player;
    private PermissionFunction permissionFunction;

    public PermissionSetupEvent(@Nonnull Player player, @Nonnull PermissionFunction defaultFunction) {
        this.player = Objects.requireNonNull(player, "player");
        this.permissionFunction = Objects.requireNonNull(defaultFunction, "defaultFunction");
    }

    /**
     * Gets the player whose permissions are being set up.
     *
     * @return the player
     */
    @Nonnull
    public Player getPlayer() {
        return player;
    }

    /**
     * Gets the current permission function that will be used.
     *
     * @return the permission function
     */
    @Nonnull
    public PermissionFunction getPermissionFunction() {
        return permissionFunction;
    }

    /**
     * Sets the permission function to use for this player.
     *
     * <p>Permission plugins should call this to install their own
     * permission checking logic.</p>
     *
     * @param function the permission function
     */
    public void setPermissionFunction(@Nonnull PermissionFunction function) {
        this.permissionFunction = Objects.requireNonNull(function, "function");
    }
}

