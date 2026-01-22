package me.internalizable.numdrassl.api.event.permission;

import me.internalizable.numdrassl.api.permission.PermissionFunction;
import me.internalizable.numdrassl.api.permission.PermissionProvider;
import me.internalizable.numdrassl.api.permission.PermissionSubject;
import me.internalizable.numdrassl.api.player.Player;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Event fired when a permission subject's permission function needs to be set up.
 *
 * <p>This event is fired during the login process for players and when the console
 * is initialized, giving permission plugins the opportunity to provide a custom
 * {@link PermissionFunction} or {@link PermissionProvider} for the subject.</p>
 *
 * <p>The subject can be either a {@link Player} or the console command source.
 * Permission plugins should handle both cases appropriately.</p>
 *
 * <h2>Example Usage with LuckPerms</h2>
 * <pre>{@code
 * @Subscribe
 * public void onPermissionSetup(PermissionSetupEvent event) {
 *     // Players are handled separately
 *     if (event.getSubject() instanceof Player) {
 *         return;
 *     }
 *
 *     // Wrap the existing provider to monitor permission checks
 *     event.setProvider(new MonitoredPermissionProvider(event.getProvider()));
 * }
 *
 * @Subscribe(priority = EventPriority.LOW)
 * public void onPlayerPermissionSetup(PermissionSetupEvent event) {
 *     if (!(event.getSubject() instanceof Player player)) {
 *         return;
 *     }
 *
 *     User user = luckPerms.getUserManager().getUser(player.getUniqueId());
 *     if (user != null) {
 *         event.setProvider(subject -> permission -> {
 *             return Tristate.fromBoolean(user.getCachedData()
 *                 .getPermissionData().checkPermission(permission).asBoolean());
 *         });
 *     }
 * }
 * }</pre>
 *
 * @see PermissionFunction
 * @see PermissionProvider
 * @see PermissionSubject
 */
public class PermissionSetupEvent {

    private final PermissionSubject subject;
    private PermissionProvider provider;

    /**
     * Creates a new permission setup event.
     *
     * @param subject the permission subject whose permissions are being set up
     * @param defaultProvider the default permission provider
     */
    public PermissionSetupEvent(@Nonnull PermissionSubject subject, @Nonnull PermissionProvider defaultProvider) {
        this.subject = Objects.requireNonNull(subject, "subject");
        this.provider = Objects.requireNonNull(defaultProvider, "defaultProvider");
    }

    /**
     * Gets the permission subject whose permissions are being set up.
     *
     * <p>This can be a {@link Player} or the console command source.</p>
     *
     * @return the permission subject
     */
    @Nonnull
    public PermissionSubject getSubject() {
        return subject;
    }

    /**
     * Gets the current permission provider that will be used.
     *
     * @return the permission provider
     */
    @Nonnull
    public PermissionProvider getProvider() {
        return provider;
    }

    /**
     * Sets the permission provider to use for this subject.
     *
     * <p>Permission plugins should call this to install their own
     * permission checking logic.</p>
     *
     * @param provider the permission provider
     */
    public void setProvider(@Nonnull PermissionProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    /**
     * Creates the permission function using the current provider and subject.
     *
     * <p>This is a convenience method that calls {@link PermissionProvider#createFunction(PermissionSubject)}
     * with this event's subject.</p>
     *
     * @return the created permission function
     */
    @Nonnull
    public PermissionFunction createFunction() {
        return provider.createFunction(subject);
    }
}
