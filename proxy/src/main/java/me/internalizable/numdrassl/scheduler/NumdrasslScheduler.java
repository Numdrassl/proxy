package me.internalizable.numdrassl.scheduler;

import me.internalizable.numdrassl.api.scheduler.ScheduledTask;
import me.internalizable.numdrassl.api.scheduler.Scheduler;
import me.internalizable.numdrassl.api.scheduler.TaskBuilder;
import me.internalizable.numdrassl.api.scheduler.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.*;

/**
 * Implementation of the API Scheduler.
 */
public class NumdrasslScheduler implements Scheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NumdrasslScheduler.class);

    private final ScheduledExecutorService executor;
    private final Map<Object, Set<NumdrasslScheduledTask>> pluginTasks = new ConcurrentHashMap<>();

    public NumdrasslScheduler() {
        this.executor = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "Numdrassl-Scheduler");
                t.setDaemon(true);
                return t;
            }
        );
    }

    public NumdrasslScheduler(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    @Override
    @Nonnull
    public TaskBuilder buildTask(@Nonnull Object plugin, @Nonnull Runnable task) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(task, "task");
        return new NumdrasslTaskBuilder(this, plugin, task);
    }

    @Override
    @Nonnull
    public ScheduledTask runAsync(@Nonnull Object plugin, @Nonnull Runnable task) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(task, "task");
        return scheduleTask(plugin, task, 0, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    @Nonnull
    public ScheduledTask runLater(@Nonnull Object plugin, @Nonnull Runnable task, long delay, @Nonnull TimeUnit unit) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(unit, "unit");
        return scheduleTask(plugin, task, delay, 0, unit);
    }

    @Override
    @Nonnull
    public ScheduledTask runRepeating(@Nonnull Object plugin, @Nonnull Runnable task,
                                       long initialDelay, long period, @Nonnull TimeUnit unit) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(unit, "unit");
        return scheduleTask(plugin, task, initialDelay, period, unit);
    }

    ScheduledTask scheduleTask(Object plugin, Runnable task, long delay, long period, TimeUnit unit) {
        NumdrasslScheduledTask scheduledTask = new NumdrasslScheduledTask(plugin, task);

        Runnable wrapper = () -> {
            if (scheduledTask.getStatus() == TaskStatus.CANCELLED) {
                return;
            }
            scheduledTask.setStatus(TaskStatus.RUNNING);
            try {
                task.run();
            } catch (Exception e) {
                LOGGER.error("Error executing scheduled task for plugin {}",
                    plugin.getClass().getSimpleName(), e);
            } finally {
                if (period <= 0) {
                    scheduledTask.setStatus(TaskStatus.FINISHED);
                    removeTask(plugin, scheduledTask);
                } else {
                    scheduledTask.setStatus(TaskStatus.SCHEDULED);
                }
            }
        };

        ScheduledFuture<?> future;
        if (period > 0) {
            future = executor.scheduleAtFixedRate(wrapper, delay, period, unit);
        } else if (delay > 0) {
            future = executor.schedule(wrapper, delay, unit);
        } else {
            future = executor.schedule(wrapper, 0, TimeUnit.MILLISECONDS);
        }

        scheduledTask.setFuture(future);
        trackTask(plugin, scheduledTask);

        return scheduledTask;
    }

    private void trackTask(Object plugin, NumdrasslScheduledTask task) {
        pluginTasks.computeIfAbsent(plugin, k -> ConcurrentHashMap.newKeySet()).add(task);
    }

    private void removeTask(Object plugin, NumdrasslScheduledTask task) {
        Set<NumdrasslScheduledTask> tasks = pluginTasks.get(plugin);
        if (tasks != null) {
            tasks.remove(task);
        }
    }

    @Override
    public void cancelAll(@Nonnull Object plugin) {
        Objects.requireNonNull(plugin, "plugin");

        Set<NumdrasslScheduledTask> tasks = pluginTasks.remove(plugin);
        if (tasks != null) {
            for (NumdrasslScheduledTask task : tasks) {
                task.cancel();
            }
        }
    }

    /**
     * Shutdown the scheduler.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class NumdrasslScheduledTask implements ScheduledTask {
        private final Object plugin;
        private final Runnable task;
        private volatile TaskStatus status = TaskStatus.SCHEDULED;
        private volatile ScheduledFuture<?> future;

        NumdrasslScheduledTask(Object plugin, Runnable task) {
            this.plugin = plugin;
            this.task = task;
        }

        void setFuture(ScheduledFuture<?> future) {
            this.future = future;
        }

        void setStatus(TaskStatus status) {
            this.status = status;
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
            if (future != null) {
                future.cancel(false);
            }
        }
    }

    private static class NumdrasslTaskBuilder implements TaskBuilder {
        private final NumdrasslScheduler scheduler;
        private final Object plugin;
        private final Runnable task;
        private long delay = 0;
        private long period = 0;
        private TimeUnit unit = TimeUnit.MILLISECONDS;

        NumdrasslTaskBuilder(NumdrasslScheduler scheduler, Object plugin, Runnable task) {
            this.scheduler = scheduler;
            this.plugin = plugin;
            this.task = task;
        }

        @Override
        @Nonnull
        public TaskBuilder delay(long delay, @Nonnull TimeUnit unit) {
            this.delay = delay;
            this.unit = unit;
            return this;
        }

        @Override
        @Nonnull
        public TaskBuilder repeat(long period, @Nonnull TimeUnit unit) {
            this.period = period;
            this.unit = unit;
            return this;
        }

        @Override
        @Nonnull
        public TaskBuilder clearRepeat() {
            this.period = 0;
            return this;
        }

        @Override
        @Nonnull
        public ScheduledTask schedule() {
            return scheduler.scheduleTask(plugin, task, delay, period, unit);
        }
    }
}

