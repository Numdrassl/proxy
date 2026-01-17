/**
 * Task scheduling infrastructure for plugins.
 *
 * <p>This package provides a thread-safe task scheduler that allows plugins to
 * execute code asynchronously, with delays, or on repeating intervals.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.scheduler.NumdrasslScheduler} - Main scheduler
 *       implementation. Manages a thread pool and tracks tasks per-plugin for bulk cancellation.</li>
 *   <li>{@link me.internalizable.numdrassl.scheduler.NumdrasslScheduledTask} - Represents
 *       a scheduled task with status tracking and cancellation support.</li>
 *   <li>{@link me.internalizable.numdrassl.scheduler.NumdrasslTaskBuilder} - Fluent builder
 *       for configuring tasks with delays and repeat intervals before scheduling.</li>
 * </ul>
 *
 * <h2>Task Types</h2>
 * <ul>
 *   <li><b>Async</b>: Executes immediately on the scheduler thread pool</li>
 *   <li><b>Delayed</b>: Executes once after a specified delay</li>
 *   <li><b>Repeating</b>: Executes at fixed intervals until cancelled</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Immediate async execution
 * scheduler.runAsync(plugin, () -> doWork());
 *
 * // Delayed execution
 * scheduler.runLater(plugin, () -> doWork(), 5, TimeUnit.SECONDS);
 *
 * // Repeating task
 * ScheduledTask task = scheduler.runRepeating(plugin, () -> tick(), 0, 1, TimeUnit.SECONDS);
 * task.cancel(); // Stop the repeating task
 *
 * // Fluent builder
 * scheduler.buildTask(plugin, () -> doWork())
 *     .delay(1, TimeUnit.SECONDS)
 *     .repeat(5, TimeUnit.SECONDS)
 *     .schedule();
 * }</pre>
 *
 * <h2>Task Lifecycle</h2>
 * <pre>
 * SCHEDULED ──► RUNNING ──► FINISHED (one-shot)
 *     │             │
 *     │             └──► SCHEDULED (repeating)
 *     │
 *     └──► CANCELLED
 * </pre>
 *
 * <h2>Plugin Cleanup</h2>
 * <p>When a plugin is unloaded, call {@code scheduler.cancelAll(plugin)} to cancel
 * all tasks registered by that plugin. This prevents orphaned tasks from running
 * after the plugin is disabled.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>All scheduler operations are thread-safe. Tasks execute on daemon threads
 * from a bounded thread pool sized to the number of available processors.</p>
 *
 * @see me.internalizable.numdrassl.api.scheduler.Scheduler
 */
package me.internalizable.numdrassl.scheduler;

