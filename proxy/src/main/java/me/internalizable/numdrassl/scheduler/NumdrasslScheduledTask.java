package me.internalizable.numdrassl.scheduler;

import me.internalizable.numdrassl.api.scheduler.ScheduledTask;
import me.internalizable.numdrassl.api.scheduler.TaskStatus;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

/**
 * Implementation of a scheduled task.
 *
 * <p>Tracks the task's status and provides cancellation support.
 * Status transitions are thread-safe via volatile fields.</p>
 */
final class NumdrasslScheduledTask implements ScheduledTask {

    private final Object plugin;
    private final Runnable task;
    private volatile TaskStatus status = TaskStatus.SCHEDULED;
    private volatile ScheduledFuture<?> future;

    NumdrasslScheduledTask(@Nonnull Object plugin, @Nonnull Runnable task) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.task = Objects.requireNonNull(task, "task");
    }

    void setFuture(@Nonnull ScheduledFuture<?> future) {
        this.future = Objects.requireNonNull(future, "future");
    }

    void setStatus(@Nonnull TaskStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    @Override
    @Nonnull
    public Object getPlugin() {
        return plugin;
    }

    @Override
    @Nonnull
    public TaskStatus getStatus() {
        return status;
    }

    @Override
    public void cancel() {
        status = TaskStatus.CANCELLED;
        ScheduledFuture<?> f = future;
        if (f != null) {
            f.cancel(false);
        }
    }

    /**
     * Gets the underlying runnable task.
     */
    @Nonnull
    Runnable getTask() {
        return task;
    }
}

