package me.internalizable.numdrassl.scheduler;

import me.internalizable.numdrassl.api.scheduler.ScheduledTask;
import me.internalizable.numdrassl.api.scheduler.Scheduler;
import me.internalizable.numdrassl.api.scheduler.TaskBuilder;
import me.internalizable.numdrassl.api.scheduler.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of the API Scheduler.
 *
 * <p>Provides task scheduling capabilities for plugins with support for:
 * <ul>
 *   <li>Immediate async execution</li>
 *   <li>Delayed execution</li>
 *   <li>Repeating tasks at fixed intervals</li>
 * </ul>
 *
 * <p>Tasks are tracked per-plugin for bulk cancellation during plugin unload.</p>
 */
public final class NumdrasslScheduler implements Scheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NumdrasslScheduler.class);
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final ScheduledExecutorService executor;
    private final Map<Object, Set<NumdrasslScheduledTask>> pluginTasks = new ConcurrentHashMap<>();

    // ==================== Construction ====================

    public NumdrasslScheduler() {
        this.executor = createDefaultExecutor();
    }

    public NumdrasslScheduler(@Nonnull ScheduledExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    private ScheduledExecutorService createDefaultExecutor() {
        int threads = Runtime.getRuntime().availableProcessors();
        AtomicInteger threadCounter = new AtomicInteger(0);

        return Executors.newScheduledThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "Numdrassl-Scheduler-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    // ==================== Scheduler API ====================

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
    public ScheduledTask runRepeating(
            @Nonnull Object plugin,
            @Nonnull Runnable task,
            long initialDelay,
            long period,
            @Nonnull TimeUnit unit) {

        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(unit, "unit");
        return scheduleTask(plugin, task, initialDelay, period, unit);
    }

    @Override
    public void cancelAll(@Nonnull Object plugin) {
        Objects.requireNonNull(plugin, "plugin");

        Set<NumdrasslScheduledTask> tasks = pluginTasks.remove(plugin);
        if (tasks != null) {
            tasks.forEach(NumdrasslScheduledTask::cancel);
            LOGGER.debug("Cancelled {} task(s) for plugin {}", tasks.size(), plugin.getClass().getSimpleName());
        }
    }

    // ==================== Internal Scheduling ====================

    @Nonnull
    ScheduledTask scheduleTask(Object plugin, Runnable task, long delay, long period, TimeUnit unit) {
        NumdrasslScheduledTask scheduledTask = new NumdrasslScheduledTask(plugin, task);
        Runnable wrapper = createTaskWrapper(scheduledTask, period > 0);

        ScheduledFuture<?> future = submitTask(wrapper, delay, period, unit);
        scheduledTask.setFuture(future);
        trackTask(plugin, scheduledTask);

        return scheduledTask;
    }

    private Runnable createTaskWrapper(NumdrasslScheduledTask scheduledTask, boolean repeating) {
        return () -> {
            if (scheduledTask.getStatus() == TaskStatus.CANCELLED) {
                return;
            }

            scheduledTask.setStatus(TaskStatus.RUNNING);
            try {
                scheduledTask.getTask().run();
            } catch (Exception e) {
                LOGGER.error("Error executing scheduled task for plugin {}",
                    scheduledTask.getPlugin().getClass().getSimpleName(), e);
            } finally {
                if (repeating) {
                    scheduledTask.setStatus(TaskStatus.SCHEDULED);
                } else {
                    scheduledTask.setStatus(TaskStatus.FINISHED);
                    removeTask(scheduledTask.getPlugin(), scheduledTask);
                }
            }
        };
    }

    private ScheduledFuture<?> submitTask(Runnable wrapper, long delay, long period, TimeUnit unit) {
        if (period > 0) {
            return executor.scheduleAtFixedRate(wrapper, delay, period, unit);
        } else if (delay > 0) {
            return executor.schedule(wrapper, delay, unit);
        } else {
            return executor.schedule(wrapper, 0, TimeUnit.MILLISECONDS);
        }
    }

    // ==================== Task Tracking ====================

    private void trackTask(Object plugin, NumdrasslScheduledTask task) {
        pluginTasks.computeIfAbsent(plugin, k -> ConcurrentHashMap.newKeySet()).add(task);
    }

    private void removeTask(Object plugin, NumdrasslScheduledTask task) {
        Set<NumdrasslScheduledTask> tasks = pluginTasks.get(plugin);
        if (tasks != null) {
            tasks.remove(task);
        }
    }

    // ==================== Lifecycle ====================

    /**
     * Shuts down the scheduler, waiting for tasks to complete.
     */
    public void shutdown() {
        LOGGER.debug("Shutting down scheduler...");
        executor.shutdown();

        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOGGER.warn("Scheduler did not terminate gracefully, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        pluginTasks.clear();
        LOGGER.debug("Scheduler shut down");
    }

    /**
     * Returns the number of plugins with active tasks.
     */
    public int getActivePluginCount() {
        return pluginTasks.size();
    }

    /**
     * Returns the total number of tracked tasks.
     */
    public int getTotalTaskCount() {
        return pluginTasks.values().stream()
            .mapToInt(Set::size)
            .sum();
    }
}
