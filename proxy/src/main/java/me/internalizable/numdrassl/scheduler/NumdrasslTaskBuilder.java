package me.internalizable.numdrassl.scheduler;

import me.internalizable.numdrassl.api.scheduler.ScheduledTask;
import me.internalizable.numdrassl.api.scheduler.TaskBuilder;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Fluent builder for constructing scheduled tasks.
 *
 * <p>Allows configuring delay and repeat intervals before scheduling.</p>
 */
final class NumdrasslTaskBuilder implements TaskBuilder {

    private final NumdrasslScheduler scheduler;
    private final Object plugin;
    private final Runnable task;

    private long delay = 0;
    private long period = 0;
    private TimeUnit unit = TimeUnit.MILLISECONDS;

    NumdrasslTaskBuilder(
            @Nonnull NumdrasslScheduler scheduler,
            @Nonnull Object plugin,
            @Nonnull Runnable task) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.task = Objects.requireNonNull(task, "task");
    }

    @Override
    @Nonnull
    public TaskBuilder delay(long delay, @Nonnull TimeUnit unit) {
        Objects.requireNonNull(unit, "unit");
        if (delay < 0) {
            throw new IllegalArgumentException("delay must be non-negative");
        }
        this.delay = delay;
        this.unit = unit;
        return this;
    }

    @Override
    @Nonnull
    public TaskBuilder repeat(long period, @Nonnull TimeUnit unit) {
        Objects.requireNonNull(unit, "unit");
        if (period < 0) {
            throw new IllegalArgumentException("period must be non-negative");
        }
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

