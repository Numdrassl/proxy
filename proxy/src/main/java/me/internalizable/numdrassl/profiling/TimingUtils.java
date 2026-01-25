package me.internalizable.numdrassl.profiling;

import io.micrometer.core.instrument.Timer;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * Utility class for timing operations.
 *
 * <p>Provides convenient wrappers around metrics timing:</p>
 * <pre>{@code
 * // Time a void operation
 * TimingUtils.time("backend_query", () -> {
 *     doExpensiveOperation();
 * });
 *
 * // Time and return a result
 * String result = TimingUtils.timeAndReturn("data_fetch", () -> {
 *     return fetchData();
 * });
 *
 * // Manual timing
 * OperationTimer timer = TimingUtils.start();
 * // ... do work ...
 * timer.stopAndRecord("my_operation");
 * }</pre>
 */
public final class TimingUtils {

    private TimingUtils() {
        // Utility class
    }

    /**
     * Starts a timer for manual timing.
     *
     * @return an OperationTimer that can be stopped later
     */
    @Nonnull
    public static OperationTimer start() {
        return new OperationTimer(System.nanoTime());
    }

    /**
     * Times a void operation and records it under the given name.
     *
     * @param operationName the metric name
     * @param operation     the operation to time
     */
    public static void time(@Nonnull String operationName, @Nonnull Runnable operation) {
        long startNanos = System.nanoTime();
        try {
            operation.run();
        } finally {
            long durationNanos = System.nanoTime() - startNanos;
            ProxyMetrics.getInstance().recordDuration(operationName, durationNanos);
        }
    }

    /**
     * Times an operation that returns a value.
     *
     * @param operationName the metric name
     * @param operation     the operation to time
     * @param <T>           the return type
     * @return the result of the operation
     */
    public static <T> T timeAndReturn(@Nonnull String operationName, @Nonnull Supplier<T> operation) {
        long startNanos = System.nanoTime();
        try {
            return operation.get();
        } finally {
            long durationNanos = System.nanoTime() - startNanos;
            ProxyMetrics.getInstance().recordDuration(operationName, durationNanos);
        }
    }

    /**
     * Creates a timing context that auto-records when closed.
     * For use with try-with-resources.
     *
     * @param operationName the metric name
     * @return a closeable timing context
     */
    @Nonnull
    public static TimingContext startContext(@Nonnull String operationName) {
        return new TimingContext(operationName, System.nanoTime());
    }

    /**
     * Timer handle for manual timing control.
     */
    public static final class OperationTimer {
        private final long startNanos;

        private OperationTimer(long startNanos) {
            this.startNanos = startNanos;
        }

        /**
         * Gets elapsed time in nanoseconds since timer was started.
         */
        public long elapsedNanos() {
            return System.nanoTime() - startNanos;
        }

        /**
         * Gets elapsed time in milliseconds since timer was started.
         */
        public long elapsedMillis() {
            return elapsedNanos() / 1_000_000;
        }

        /**
         * Stops the timer and records the duration.
         *
         * @param operationName the metric name
         */
        public void stopAndRecord(@Nonnull String operationName) {
            ProxyMetrics.getInstance().recordDuration(operationName, elapsedNanos());
        }

        /**
         * Stops the timer and records using the Micrometer sample API.
         *
         * @param sample the timer sample
         * @param timer  the timer to record to
         */
        public void stopAndRecord(@Nonnull Timer.Sample sample, @Nonnull Timer timer) {
            sample.stop(timer);
        }
    }

    /**
     * Auto-closeable timing context for try-with-resources.
     */
    public static final class TimingContext implements AutoCloseable {
        private final String operationName;
        private final long startNanos;

        private TimingContext(String operationName, long startNanos) {
            this.operationName = operationName;
            this.startNanos = startNanos;
        }

        @Override
        public void close() {
            long durationNanos = System.nanoTime() - startNanos;
            ProxyMetrics.getInstance().recordDuration(operationName, durationNanos);
        }

        /**
         * Gets elapsed time so far in milliseconds.
         */
        public long elapsedMillis() {
            return (System.nanoTime() - startNanos) / 1_000_000;
        }
    }
}

