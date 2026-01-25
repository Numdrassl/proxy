package me.internalizable.numdrassl.profiling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks metrics history over time for historical analysis with tiered retention.
 *
 * <p>Uses a tiered storage approach to balance granularity with memory efficiency:</p>
 * <ul>
 *   <li><b>Tier 1 (High-res)</b>: 10-second snapshots for the last 1 hour (360 points)</li>
 *   <li><b>Tier 2 (Medium-res)</b>: 1-minute aggregates for the last 24 hours (1440 points)</li>
 *   <li><b>Tier 3 (Low-res)</b>: 10-minute aggregates for the last 7 days (1008 points)</li>
 *   <li><b>Tier 4 (Daily)</b>: Daily summaries for the last 90 days (90 points)</li>
 * </ul>
 *
 * <p>This allows administrators to:</p>
 * <ul>
 *   <li>View detailed data for recent events (last hour)</li>
 *   <li>Analyze trends over the past day with minute-level granularity</li>
 *   <li>Review weekly patterns</li>
 *   <li>Track monthly/quarterly trends</li>
 *   <li>Identify peak loads and their exact timing</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * MetricsHistory history = MetricsHistory.getInstance();
 *
 * // Get high-res data from last hour
 * List<HistoricalSnapshot> recent = history.getRecentHistory();
 *
 * // Get data from last 24 hours (minute resolution)
 * List<PeriodSummary> day = history.getHourlySummaries();
 *
 * // Get data from last 7 days (10-min resolution)
 * List<PeriodSummary> week = history.getDailySummaries();
 *
 * // Get peak values (all-time since start)
 * HistoricalSnapshot peak = history.getPeakSessions();
 * }</pre>
 */
public final class MetricsHistory {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsHistory.class);

    private static volatile MetricsHistory instance;

    // Tier 1: High-resolution (10-second snapshots, 1 hour retention)
    private static final int TIER1_INTERVAL_SECONDS = 10;
    private static final int TIER1_MAX_SIZE = 360; // 1 hour

    // Tier 2: Medium-resolution (1-minute aggregates, 24 hour retention)
    private static final int TIER2_INTERVAL_MINUTES = 1;
    private static final int TIER2_MAX_SIZE = 1440; // 24 hours

    // Tier 3: Low-resolution (10-minute aggregates, 7 day retention)
    private static final int TIER3_INTERVAL_MINUTES = 10;
    private static final int TIER3_MAX_SIZE = 1008; // 7 days

    // Tier 4: Daily summaries (90 day retention)
    private static final int TIER4_MAX_SIZE = 90; // 90 days

    // Storage for each tier
    private final CopyOnWriteArrayList<HistoricalSnapshot> tier1History = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<PeriodSummary> tier2History = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<PeriodSummary> tier3History = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<DailySummary> tier4History = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService scheduler;

    // Peak tracking (all-time since proxy start)
    private volatile HistoricalSnapshot peakSessions = null;
    private volatile HistoricalSnapshot peakPacketRate = null;
    private volatile HistoricalSnapshot peakByteRate = null;
    private volatile HistoricalSnapshot peakResponseTime = null;

    // Aggregation buffers for tier rollups
    private final AggregationBuffer tier2Buffer = new AggregationBuffer();
    private final AggregationBuffer tier3Buffer = new AggregationBuffer();
    private final AggregationBuffer tier4Buffer = new AggregationBuffer();

    // ==================== Construction ====================

    private MetricsHistory() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MetricsHistory");
            t.setDaemon(true);
            return t;
        });

        // Tier 1: Record snapshots every 10 seconds
        scheduler.scheduleAtFixedRate(
            this::recordTier1Snapshot,
            TIER1_INTERVAL_SECONDS,
            TIER1_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );

        // Tier 2: Aggregate to 1-minute summaries
        scheduler.scheduleAtFixedRate(
            this::rollupToTier2,
            TIER2_INTERVAL_MINUTES,
            TIER2_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );

        // Tier 3: Aggregate to 10-minute summaries
        scheduler.scheduleAtFixedRate(
            this::rollupToTier3,
            TIER3_INTERVAL_MINUTES,
            TIER3_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );

        // Tier 4: Aggregate daily summaries at midnight (check every hour)
        scheduler.scheduleAtFixedRate(
            this::rollupToTier4,
            1,
            1,
            TimeUnit.HOURS
        );

        LOGGER.info("Metrics history initialized with tiered retention:");
        LOGGER.info("  Tier 1: 10s snapshots, 1 hour retention ({} points)", TIER1_MAX_SIZE);
        LOGGER.info("  Tier 2: 1min aggregates, 24 hour retention ({} points)", TIER2_MAX_SIZE);
        LOGGER.info("  Tier 3: 10min aggregates, 7 day retention ({} points)", TIER3_MAX_SIZE);
        LOGGER.info("  Tier 4: Daily summaries, 90 day retention ({} points)", TIER4_MAX_SIZE);
    }

    // ==================== Singleton Access ====================

    @Nonnull
    public static MetricsHistory getInstance() {
        if (instance == null) {
            synchronized (MetricsHistory.class) {
                if (instance == null) {
                    instance = new MetricsHistory();
                }
            }
        }
        return instance;
    }

    // ==================== Tier 1: High-Resolution Snapshots ====================

    private void recordTier1Snapshot() {
        try {
            ProxyMetrics metrics = ProxyMetrics.getInstance();
            metrics.updateThroughput();
            metrics.checkHangingRequests();

            ProxyMetrics.MetricsSnapshot current = metrics.createSnapshot();
            HistoricalSnapshot snapshot = new HistoricalSnapshot(
                System.currentTimeMillis(),
                current.activeSessions(),
                current.connectionsAccepted(),
                current.connectionsClosed(),
                current.packetsFromClient(),
                current.packetsToClient(),
                current.packetsPerSecIn(),
                current.packetsPerSecOut(),
                current.bytesFromClient(),
                current.bytesToClient(),
                current.bytesPerSecIn(),
                current.bytesPerSecOut(),
                current.avgResponseTimeMs(),
                current.hangingRequests(),
                current.authFailures(),
                current.backendFailures()
            );

            // Add to tier 1 history
            tier1History.add(snapshot);
            trimList(tier1History, TIER1_MAX_SIZE);

            // Update peaks
            updatePeaks(snapshot);

            // Add to aggregation buffers
            tier2Buffer.add(snapshot);
            tier3Buffer.add(snapshot);
            tier4Buffer.add(snapshot);

        } catch (Exception e) {
            LOGGER.warn("Failed to record tier 1 snapshot", e);
        }
    }

    // ==================== Tier 2: 1-Minute Aggregates ====================

    private void rollupToTier2() {
        try {
            PeriodSummary summary = tier2Buffer.createSummaryAndReset();
            if (summary != null) {
                tier2History.add(summary);
                trimList(tier2History, TIER2_MAX_SIZE);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to rollup to tier 2", e);
        }
    }

    // ==================== Tier 3: 10-Minute Aggregates ====================

    private void rollupToTier3() {
        try {
            PeriodSummary summary = tier3Buffer.createSummaryAndReset();
            if (summary != null) {
                tier3History.add(summary);
                trimList(tier3History, TIER3_MAX_SIZE);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to rollup to tier 3", e);
        }
    }

    // ==================== Tier 4: Daily Summaries ====================

    private volatile int lastRollupDay = -1;

    private void rollupToTier4() {
        try {
            int currentDay = LocalDateTime.now().getDayOfYear();
            if (lastRollupDay == -1) {
                lastRollupDay = currentDay;
                return;
            }

            // Only rollup when day changes
            if (currentDay != lastRollupDay) {
                DailySummary summary = tier4Buffer.createDailySummaryAndReset(lastRollupDay);
                if (summary != null) {
                    tier4History.add(summary);
                    trimList(tier4History, TIER4_MAX_SIZE);
                }
                lastRollupDay = currentDay;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to rollup to tier 4", e);
        }
    }

    // ==================== Peak Tracking ====================

    private void updatePeaks(HistoricalSnapshot snapshot) {
        if (peakSessions == null || snapshot.activeSessions > peakSessions.activeSessions) {
            peakSessions = snapshot;
        }

        double packetRate = snapshot.packetsPerSecIn + snapshot.packetsPerSecOut;
        if (peakPacketRate == null ||
            packetRate > (peakPacketRate.packetsPerSecIn + peakPacketRate.packetsPerSecOut)) {
            peakPacketRate = snapshot;
        }

        double byteRate = snapshot.bytesPerSecIn + snapshot.bytesPerSecOut;
        if (peakByteRate == null ||
            byteRate > (peakByteRate.bytesPerSecIn + peakByteRate.bytesPerSecOut)) {
            peakByteRate = snapshot;
        }

        if (peakResponseTime == null || snapshot.avgResponseTimeMs > peakResponseTime.avgResponseTimeMs) {
            peakResponseTime = snapshot;
        }
    }

    private <T> void trimList(CopyOnWriteArrayList<T> list, int maxSize) {
        while (list.size() > maxSize) {
            list.remove(0);
        }
    }

    // ==================== Data Access - Tier 1 (Last Hour) ====================

    /**
     * Gets high-resolution snapshots from the last hour (10-second intervals).
     */
    @Nonnull
    public List<HistoricalSnapshot> getRecentHistory() {
        return new ArrayList<>(tier1History);
    }

    /**
     * Gets high-resolution snapshots from the last N time units.
     */
    @Nonnull
    public List<HistoricalSnapshot> getHistorySince(long amount, TimeUnit unit) {
        long cutoff = System.currentTimeMillis() - unit.toMillis(amount);
        List<HistoricalSnapshot> result = new ArrayList<>();
        for (HistoricalSnapshot snapshot : tier1History) {
            if (snapshot.timestamp >= cutoff) {
                result.add(snapshot);
            }
        }
        return result;
    }

    // ==================== Data Access - Tier 2 (Last 24 Hours) ====================

    /**
     * Gets 1-minute aggregates from the last 24 hours.
     */
    @Nonnull
    public List<PeriodSummary> getMinuteSummaries() {
        return new ArrayList<>(tier2History);
    }

    /**
     * Gets 1-minute summaries from the last N hours.
     */
    @Nonnull
    public List<PeriodSummary> getMinuteSummariesSince(int hours) {
        long cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(hours);
        List<PeriodSummary> result = new ArrayList<>();
        for (PeriodSummary summary : tier2History) {
            if (summary.endTime >= cutoff) {
                result.add(summary);
            }
        }
        return result;
    }

    // ==================== Data Access - Tier 3 (Last 7 Days) ====================

    /**
     * Gets 10-minute aggregates from the last 7 days.
     */
    @Nonnull
    public List<PeriodSummary> getTenMinuteSummaries() {
        return new ArrayList<>(tier3History);
    }

    /**
     * Gets 10-minute summaries from the last N days.
     */
    @Nonnull
    public List<PeriodSummary> getTenMinuteSummariesSince(int days) {
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
        List<PeriodSummary> result = new ArrayList<>();
        for (PeriodSummary summary : tier3History) {
            if (summary.endTime >= cutoff) {
                result.add(summary);
            }
        }
        return result;
    }

    // ==================== Data Access - Tier 4 (Last 90 Days) ====================

    /**
     * Gets daily summaries from the last 90 days.
     */
    @Nonnull
    public List<DailySummary> getDailySummaries() {
        return new ArrayList<>(tier4History);
    }

    /**
     * Gets daily summaries from the last N days.
     */
    @Nonnull
    public List<DailySummary> getDailySummariesSince(int days) {
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
        List<DailySummary> result = new ArrayList<>();
        for (DailySummary summary : tier4History) {
            if (summary.timestamp >= cutoff) {
                result.add(summary);
            }
        }
        return result;
    }

    // ==================== Peak Access ====================

    public HistoricalSnapshot getPeakSessions() {
        return peakSessions;
    }

    public HistoricalSnapshot getPeakPacketRate() {
        return peakPacketRate;
    }

    public HistoricalSnapshot getPeakByteRate() {
        return peakByteRate;
    }

    public HistoricalSnapshot getPeakResponseTime() {
        return peakResponseTime;
    }

    // ==================== Statistics ====================

    public int getHistorySize() {
        return tier1History.size();
    }

    public int getTier2Size() {
        return tier2History.size();
    }

    public int getTier3Size() {
        return tier3History.size();
    }

    public int getTier4Size() {
        return tier4History.size();
    }

    public HistoricalSnapshot getLatest() {
        if (tier1History.isEmpty()) return null;
        return tier1History.get(tier1History.size() - 1);
    }

    /**
     * Calculates average values over the specified time period (from tier 1 data).
     */
    @Nonnull
    public AverageStats getAveragesSince(long amount, TimeUnit unit) {
        List<HistoricalSnapshot> snapshots = getHistorySince(amount, unit);
        if (snapshots.isEmpty()) {
            return new AverageStats(0, 0, 0, 0, 0, 0, 0);
        }

        double totalSessions = 0;
        double totalPacketsIn = 0;
        double totalPacketsOut = 0;
        double totalBytesIn = 0;
        double totalBytesOut = 0;
        double totalResponseTime = 0;
        long maxSessions = 0;

        for (HistoricalSnapshot s : snapshots) {
            totalSessions += s.activeSessions;
            totalPacketsIn += s.packetsPerSecIn;
            totalPacketsOut += s.packetsPerSecOut;
            totalBytesIn += s.bytesPerSecIn;
            totalBytesOut += s.bytesPerSecOut;
            totalResponseTime += s.avgResponseTimeMs;
            if (s.activeSessions > maxSessions) {
                maxSessions = s.activeSessions;
            }
        }

        int count = snapshots.size();
        return new AverageStats(
            totalSessions / count,
            totalPacketsIn / count,
            totalPacketsOut / count,
            totalBytesIn / count,
            totalBytesOut / count,
            totalResponseTime / count,
            maxSessions
        );
    }

    // ==================== Report Generation ====================

    /**
     * Creates a comprehensive historical report covering all tiers.
     */
    @Nonnull
    public String createHistoricalReport() {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        sb.append("================================================================================\n");
        sb.append("                    NUMDRASSL PROXY HISTORICAL REPORT\n");
        sb.append("================================================================================\n");
        sb.append("Generated: ").append(LocalDateTime.now().format(dtf)).append("\n\n");

        sb.append("--- DATA RETENTION ---\n");
        sb.append(String.format("Tier 1 (10s):   %d points (last ~%d min)\n",
            tier1History.size(), tier1History.size() * 10 / 60));
        sb.append(String.format("Tier 2 (1min):  %d points (last ~%d hours)\n",
            tier2History.size(), tier2History.size() / 60));
        sb.append(String.format("Tier 3 (10min): %d points (last ~%.1f days)\n",
            tier3History.size(), tier3History.size() * 10.0 / 60 / 24));
        sb.append(String.format("Tier 4 (daily): %d points (last ~%d days)\n\n",
            tier4History.size(), tier4History.size()));

        // Current state
        HistoricalSnapshot latest = getLatest();
        if (latest != null) {
            sb.append("--- CURRENT STATE ---\n");
            sb.append(String.format("Active Sessions:     %d\n", latest.activeSessions));
            sb.append(String.format("Packets/sec:         %.1f in, %.1f out\n",
                latest.packetsPerSecIn, latest.packetsPerSecOut));
            sb.append(String.format("Bytes/sec:           %s in, %s out\n",
                formatBytesRate(latest.bytesPerSecIn), formatBytesRate(latest.bytesPerSecOut)));
            sb.append(String.format("Avg Response Time:   %.2f ms\n", latest.avgResponseTimeMs));
            sb.append(String.format("Hanging Requests:    %d\n\n", latest.hangingRequests));
        }

        // All-time peaks
        sb.append("--- ALL-TIME PEAKS (since proxy start) ---\n");
        if (peakSessions != null) {
            sb.append(String.format("Peak Sessions:       %d (at %s)\n",
                peakSessions.activeSessions, formatTimestamp(peakSessions.timestamp)));
        }
        if (peakPacketRate != null) {
            sb.append(String.format("Peak Packet Rate:    %.1f/sec (at %s)\n",
                peakPacketRate.packetsPerSecIn + peakPacketRate.packetsPerSecOut,
                formatTimestamp(peakPacketRate.timestamp)));
        }
        if (peakByteRate != null) {
            sb.append(String.format("Peak Byte Rate:      %s/sec (at %s)\n",
                formatBytesRate(peakByteRate.bytesPerSecIn + peakByteRate.bytesPerSecOut),
                formatTimestamp(peakByteRate.timestamp)));
        }
        if (peakResponseTime != null) {
            sb.append(String.format("Peak Response Time:  %.2f ms (at %s)\n",
                peakResponseTime.avgResponseTimeMs, formatTimestamp(peakResponseTime.timestamp)));
        }
        sb.append("\n");

        // Hourly averages (last 24h from tier 2)
        sb.append("--- HOURLY AVERAGES (last 24 hours) ---\n");
        appendHourlyAverages(sb);
        sb.append("\n");

        // Daily summaries (from tier 4)
        if (!tier4History.isEmpty()) {
            sb.append("--- DAILY SUMMARIES ---\n");
            sb.append(String.format("%-12s %8s %8s %12s %12s %10s\n",
                "Date", "Max Sess", "Avg Sess", "Avg Pkt/s", "Avg Byte/s", "Avg Resp"));
            sb.append("-".repeat(70)).append("\n");

            List<DailySummary> dailies = new ArrayList<>(tier4History);
            Collections.reverse(dailies);
            int shown = 0;
            for (DailySummary d : dailies) {
                if (shown >= 14) break; // Last 2 weeks
                sb.append(String.format("%-12s %8d %8.1f %12.1f %12s %10.1fms\n",
                    d.getFormattedDate(),
                    d.maxSessions,
                    d.avgSessions,
                    d.avgPacketsPerSec,
                    formatBytesRate(d.avgBytesPerSec),
                    d.avgResponseTimeMs));
                shown++;
            }
            sb.append("\n");
        }

        sb.append("================================================================================\n");
        sb.append("Share this report at: https://github.com/Numdrassl/proxy/issues\n");
        sb.append("================================================================================\n");

        return sb.toString();
    }

    private void appendHourlyAverages(StringBuilder sb) {
        // Group tier 2 data into hourly buckets
        if (tier2History.isEmpty()) {
            sb.append("  No data yet\n");
            return;
        }

        long now = System.currentTimeMillis();
        for (int hoursAgo = 0; hoursAgo < 24; hoursAgo++) {
            long bucketEnd = now - TimeUnit.HOURS.toMillis(hoursAgo);
            long bucketStart = bucketEnd - TimeUnit.HOURS.toMillis(1);

            double totalSessions = 0;
            double totalPackets = 0;
            double totalBytes = 0;
            double totalResponse = 0;
            long maxSessions = 0;
            int count = 0;

            for (PeriodSummary s : tier2History) {
                if (s.endTime >= bucketStart && s.endTime < bucketEnd) {
                    totalSessions += s.avgSessions;
                    totalPackets += s.avgPacketsPerSecIn + s.avgPacketsPerSecOut;
                    totalBytes += s.avgBytesPerSecIn + s.avgBytesPerSecOut;
                    totalResponse += s.avgResponseTimeMs;
                    if (s.maxSessions > maxSessions) maxSessions = s.maxSessions;
                    count++;
                }
            }

            if (count > 0) {
                String timeLabel = hoursAgo == 0 ? "This hour" : String.format("%dh ago", hoursAgo);
                sb.append(String.format("  %-10s: avg=%.0f peak=%d sess, %.0f pkt/s, %s/s, %.1fms resp\n",
                    timeLabel,
                    totalSessions / count,
                    maxSessions,
                    totalPackets / count,
                    formatBytesRate(totalBytes / count),
                    totalResponse / count));
            }
        }
    }

    // ==================== Lifecycle ====================

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("Metrics history shut down");
    }

    public void clear() {
        tier1History.clear();
        tier2History.clear();
        tier3History.clear();
        tier4History.clear();
        peakSessions = null;
        peakPacketRate = null;
        peakByteRate = null;
        peakResponseTime = null;
        LOGGER.info("Metrics history cleared");
    }

    // ==================== Utility ====================

    private static String formatTimestamp(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static String formatBytesRate(double bytesPerSec) {
        if (bytesPerSec < 1024) return String.format("%.0f B", bytesPerSec);
        if (bytesPerSec < 1024 * 1024) return String.format("%.1f KB", bytesPerSec / 1024.0);
        if (bytesPerSec < 1024 * 1024 * 1024) return String.format("%.1f MB", bytesPerSec / (1024.0 * 1024));
        return String.format("%.2f GB", bytesPerSec / (1024.0 * 1024 * 1024));
    }

    // ==================== Aggregation Buffer ====================

    /**
     * Thread-safe buffer for aggregating snapshots into period summaries.
     */
    private static class AggregationBuffer {
        private long startTime = System.currentTimeMillis();
        private int count = 0;
        private double totalSessions = 0;
        private long maxSessions = 0;
        private double totalPacketsIn = 0;
        private double totalPacketsOut = 0;
        private double totalBytesIn = 0;
        private double totalBytesOut = 0;
        private double totalResponseTime = 0;
        private long maxHangingRequests = 0;

        synchronized void add(HistoricalSnapshot snapshot) {
            count++;
            totalSessions += snapshot.activeSessions;
            if (snapshot.activeSessions > maxSessions) maxSessions = snapshot.activeSessions;
            totalPacketsIn += snapshot.packetsPerSecIn;
            totalPacketsOut += snapshot.packetsPerSecOut;
            totalBytesIn += snapshot.bytesPerSecIn;
            totalBytesOut += snapshot.bytesPerSecOut;
            totalResponseTime += snapshot.avgResponseTimeMs;
            if (snapshot.hangingRequests > maxHangingRequests) maxHangingRequests = snapshot.hangingRequests;
        }

        synchronized PeriodSummary createSummaryAndReset() {
            if (count == 0) return null;

            PeriodSummary summary = new PeriodSummary(
                startTime,
                System.currentTimeMillis(),
                maxSessions,
                totalSessions / count,
                totalPacketsIn / count,
                totalPacketsOut / count,
                totalBytesIn / count,
                totalBytesOut / count,
                totalResponseTime / count,
                maxHangingRequests
            );

            // Reset
            startTime = System.currentTimeMillis();
            count = 0;
            totalSessions = 0;
            maxSessions = 0;
            totalPacketsIn = 0;
            totalPacketsOut = 0;
            totalBytesIn = 0;
            totalBytesOut = 0;
            totalResponseTime = 0;
            maxHangingRequests = 0;

            return summary;
        }

        synchronized DailySummary createDailySummaryAndReset(int dayOfYear) {
            if (count == 0) return null;

            DailySummary summary = new DailySummary(
                startTime,
                dayOfYear,
                maxSessions,
                totalSessions / count,
                (totalPacketsIn + totalPacketsOut) / count,
                (totalBytesIn + totalBytesOut) / count,
                totalResponseTime / count,
                count
            );

            // Reset
            startTime = System.currentTimeMillis();
            count = 0;
            totalSessions = 0;
            maxSessions = 0;
            totalPacketsIn = 0;
            totalPacketsOut = 0;
            totalBytesIn = 0;
            totalBytesOut = 0;
            totalResponseTime = 0;
            maxHangingRequests = 0;

            return summary;
        }
    }

    // ==================== Data Classes ====================

    /**
     * A point-in-time snapshot of metrics (Tier 1).
     */
    public record HistoricalSnapshot(
        long timestamp,
        long activeSessions,
        long connectionsAccepted,
        long connectionsClosed,
        long totalPacketsIn,
        long totalPacketsOut,
        double packetsPerSecIn,
        double packetsPerSecOut,
        long totalBytesIn,
        long totalBytesOut,
        double bytesPerSecIn,
        double bytesPerSecOut,
        double avgResponseTimeMs,
        long hangingRequests,
        long authFailures,
        long backendFailures
    ) {
        public String getFormattedTime() {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }
    }

    /**
     * Aggregated summary for a time period (Tier 2/3).
     */
    public record PeriodSummary(
        long startTime,
        long endTime,
        long maxSessions,
        double avgSessions,
        double avgPacketsPerSecIn,
        double avgPacketsPerSecOut,
        double avgBytesPerSecIn,
        double avgBytesPerSecOut,
        double avgResponseTimeMs,
        long maxHangingRequests
    ) {
        public String getFormattedTime() {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"));
        }
    }

    /**
     * Daily summary (Tier 4).
     */
    public record DailySummary(
        long timestamp,
        int dayOfYear,
        long maxSessions,
        double avgSessions,
        double avgPacketsPerSec,
        double avgBytesPerSec,
        double avgResponseTimeMs,
        int dataPointCount
    ) {
        public String getFormattedDate() {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
    }

    /**
     * Calculated average statistics.
     */
    public record AverageStats(
        double avgSessions,
        double avgPacketsIn,
        double avgPacketsOut,
        double avgBytesIn,
        double avgBytesOut,
        double avgResponseTime,
        long peakSessions
    ) {}
}

