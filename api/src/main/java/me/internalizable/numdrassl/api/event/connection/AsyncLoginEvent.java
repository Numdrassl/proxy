package me.internalizable.numdrassl.api.event.connection;

import me.internalizable.numdrassl.api.event.ResultedEvent;
import me.internalizable.numdrassl.api.player.Player;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Event fired during the authentication phase, specifically designed for asynchronous operations.
 *
 * <p>This event is triggered <b>before</b> the player is fully admitted to the server (before {@link LoginEvent}).
 * It provides a mechanism for plugins to load blocking data (e.g., SQL databases, Redis, Web APIs)
 * without freezing the main proxy thread.</p>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @Subscribe
 * public void onAsyncLogin(AsyncLoginEvent event) {
 *      // 1. Create a future running on a separate thread (IO)
 *      CompletableFuture<Void> dbTask = CompletableFuture.runAsync(() -> {
 *          User user = database.load(event.getPlayer().getUniqueId());
 *
 *          if (user.isBanned()) {
 *              event.setResult(AsyncLoginEvent.AsyncLoginResult.denied("You are banned"));
 *          }
 *      });
 *
 *      // 2. Register the task so the proxy waits for completion
 *      event.registerTask(dbTask);
 * }
 * }</pre>
 */
public class AsyncLoginEvent implements ResultedEvent<AsyncLoginEvent.AsyncLoginResult> {

    private final Player player;
    private AsyncLoginResult result;

    // Stores futures that serve as a synchronization barrier for the login process.
    private final List<CompletableFuture<?>> loginTasks = new ArrayList<>();

    public AsyncLoginEvent(@Nonnull Player player) {
        this.player = Objects.requireNonNull(player, "player");
        this.result = AsyncLoginResult.allowed();
    }

    /**
     * Registers a synchronization task.
     *
     * <p>The proxy will defer the finalization of the login process until the provided
     * {@link CompletableFuture} is completed. If the future completes exceptionally,
     * the login may be aborted.</p>
     *
     * @param task A future representing the asynchronous work (e.g., database loading).
     */
    public void registerTask(CompletableFuture<?> task) {
        if (task != null) {
            this.loginTasks.add(task);
        }
    }

    /**
     * Retrieves all registered synchronization tasks.
     *
     * @return A list of futures that must complete before login proceeds.
     */
    public List<CompletableFuture<?>> getLoginTasks() {
        return loginTasks;
    }

    @Nonnull
    public Player getPlayer() {
        return player;
    }

    @Override
    public AsyncLoginResult getResult() {
        return result;
    }

    @Override
    public void setResult(AsyncLoginResult result) {
        this.result = result;
    }

    /**
     * Represents the result of the asynchronous pre-login attempt.
     */
    public static final class AsyncLoginResult implements Result {

        private static final AsyncLoginResult ALLOWED = new AsyncLoginResult(true, null);

        private final boolean allowed;
        private final String denyReason;

        private AsyncLoginResult(boolean allowed, @Nullable String denyReason) {
            this.allowed = allowed;
            this.denyReason = denyReason;
        }

        @Override
        public boolean isAllowed() {
            return allowed;
        }

        /**
         * Gets the disconnect reason if the login was denied.
         *
         * @return The deny reason, or null if allowed.
         */
        @Nullable
        public String getDenyReason() {
            return denyReason;
        }

        /**
         * Indicates that the player is allowed to proceed to the next login stage.
         *
         * @return An allowed result.
         */
        public static AsyncLoginResult allowed() {
            return ALLOWED;
        }

        /**
         * Denies the login attempt and disconnects the player with the specified reason.
         *
         * @param reason The kick message displayed to the client.
         * @return A denied result.
         */
        public static AsyncLoginResult denied(@Nonnull String reason) {
            return new AsyncLoginResult(false, reason);
        }
    }
}