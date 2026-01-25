package me.internalizable.numdrassl.profiling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodically logs a summary of proxy metrics.
 *
 * <p>Useful for operators who want to monitor the proxy via log aggregation
 * without setting up a full metrics stack.</p>
 *
 * <p>Example log output:</p>
 * <pre>
 * [MetricsLogger] Sessions: 42 | Packets C→P→B: 12345/12340 | B→P→C: 8765/8760 | Bytes In: 1.2MB | Out: 856KB | Conns: +50/-8 | Errors: auth=0, backend=1
 * </pre>
 */
public final class MetricsLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsLogger.class);

    private final ScheduledExecutorService scheduler;
    private final long intervalSeconds;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Creates a metrics logger with the specified interval.
     *
     * @param intervalSeconds how often to log metrics (minimum 5 seconds)
     */
    public MetricsLogger(long intervalSeconds) {
        if (intervalSeconds < 5) {
            throw new IllegalArgumentException("Interval must be at least 5 seconds");
        }
        this.intervalSeconds = intervalSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MetricsLogger");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Creates a metrics logger with default 60-second interval.
     */
    public MetricsLogger() {
        this(60);
    }

    /**
     * Starts periodic logging.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler.scheduleAtFixedRate(this::logMetrics, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
            LOGGER.info("Metrics logging started (interval: {}s)", intervalSeconds);
        }
    }

    /**
     * Stops periodic logging.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            LOGGER.info("Metrics logging stopped");
        }
    }

    /**
     * Logs current metrics immediately.
     */
    public void logMetrics() {
        try {
            ProxyMetrics.MetricsSnapshot snapshot = ProxyMetrics.getInstance().createSnapshot();
            LOGGER.info("[METRICS] {}", snapshot);
        } catch (Exception e) {
            LOGGER.warn("Failed to log metrics", e);
        }
    }

    /**
     * Logs current metrics at DEBUG level (useful for more verbose output).
     */
    public void logMetricsDebug() {
        try {
            ProxyMetrics.MetricsSnapshot snapshot = ProxyMetrics.getInstance().createSnapshot();
            LOGGER.debug("[METRICS] {}", snapshot);
        } catch (Exception e) {
            LOGGER.debug("Failed to log metrics", e);
        }
    }
}

