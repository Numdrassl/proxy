package me.internalizable.numdrassl.api.event.permission;

import me.internalizable.numdrassl.api.permission.PermissionFunction;
import me.internalizable.numdrassl.api.permission.PermissionProvider;
import me.internalizable.numdrassl.api.permission.PermissionSubject;
import me.internalizable.numdrassl.api.player.Player;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

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
 * <h2>Async Support</h2>
 * <p>If your permission plugin needs to load data asynchronously (e.g., from a database),
 * use {@link #registerAsyncTask(CompletableFuture)} to register the task. The proxy will
 * wait for all registered async tasks to complete before proceeding with the login.</p>
 *
 * <h2>Example Usage with LuckPerms</h2>
 * <pre>{@code
 * @Subscribe
 * public void onPermissionSetup(PermissionSetupEvent e) {
 *     if (!(e.getSubject() instanceof Player player)) {
 *         return;
 *     }
 *
 *     // Register an async task that loads user data
 *     CompletableFuture<Void> loadTask = CompletableFuture.runAsync(() -> {
 *         User user = loadUser(player.getUniqueId(), player.getUsername());
 *         e.setProvider(new MyPermissionProvider(user));
 *     }, asyncExecutor);
 *
 *     e.registerAsyncTask(loadTask);
 * }
 * }</pre>
 *
 * @see PermissionFunction
 * @see PermissionProvider
 * @see PermissionSubject
 */
public class PermissionSetupEvent {

    private final PermissionSubject subject;
    private final AtomicReference<PermissionProvider> provider;
    private final AtomicReference<CompletableFuture<Void>> asyncTask;

    /**
     * Creates a new permission setup event.
     *
     * @param subject the permission subject whose permissions are being set up
     * @param defaultProvider the default permission provider
     */
    public PermissionSetupEvent(@Nonnull PermissionSubject subject, @Nonnull PermissionProvider defaultProvider) {
        this.subject = Objects.requireNonNull(subject, "subject");
        this.provider = new AtomicReference<>(Objects.requireNonNull(defaultProvider, "defaultProvider"));
        this.asyncTask = new AtomicReference<>(CompletableFuture.completedFuture(null));
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
        return provider.get();
    }

    /**
     * Sets the permission provider to use for this subject.
     *
     * <p>Permission plugins should call this to install their own
     * permission checking logic. This method is thread-safe and can
     * be called from async tasks.</p>
     *
     * @param provider the permission provider
     */
    public void setProvider(@Nonnull PermissionProvider provider) {
        this.provider.set(Objects.requireNonNull(provider, "provider"));
    }

    /**
     * Registers an async task that must complete before the login proceeds.
     *
     * <p>Use this method when you need to load permission data asynchronously.
     * The proxy will wait for all registered tasks to complete before
     * firing the LoginEvent.</p>
     *
     * <p>If multiple tasks are registered, they are combined with
     * {@link CompletableFuture#allOf(CompletableFuture[])}.</p>
     *
     * @param task the async task to register
     */
    public void registerAsyncTask(@Nonnull CompletableFuture<Void> task) {
        Objects.requireNonNull(task, "task");
        asyncTask.updateAndGet(existing ->
            existing.isDone() ? task : CompletableFuture.allOf(existing, task)
        );
    }

    /**
     * Gets the combined async task that must complete before proceeding.
     *
     * @return the async task, never null (completes immediately if no async work)
     */
    @Nonnull
    public CompletableFuture<Void> getAsyncTask() {
        return asyncTask.get();
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
        return provider.get().createFunction(subject);
    }
}
