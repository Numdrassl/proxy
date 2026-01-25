package me.internalizable.numdrassl.command.builtin;

import me.internalizable.numdrassl.api.command.Command;
import me.internalizable.numdrassl.api.command.CommandResult;
import me.internalizable.numdrassl.api.command.CommandSource;
import me.internalizable.numdrassl.profiling.MetricsHistory;
import me.internalizable.numdrassl.profiling.ProxyMetrics;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

/**
 * Built-in command for viewing proxy metrics and performance statistics.
 *
 * <p>Usage:</p>
 * <ul>
 *   <li>{@code metrics} - Show summary statistics</li>
 *   <li>{@code metrics history} - Show historical data and peaks</li>
 *   <li>{@code metrics peaks} - Show all-time peak values</li>
 *   <li>{@code metrics memory} - Show detailed memory info</li>
 *   <li>{@code metrics gc} - Trigger garbage collection and show memory</li>
 *   <li>{@code metrics report} - Generate shareable report</li>
 * </ul>
 */
public class MetricsCommand implements Command {

    @Override
    @Nonnull
    public String getName() {
        return "metrics";
    }

    @Override
    public String getDescription() {
        return "Show proxy performance metrics";
    }

    @Override
    @Nonnull
    public CommandResult execute(@Nonnull CommandSource source, @Nonnull String[] args) {
        if (args.length > 0) {
            return switch (args[0].toLowerCase()) {
                case "memory", "mem" -> showMemoryStats(source);
                case "gc" -> triggerGcAndShowMemory(source);
                case "report" -> showReport(source);
                case "history", "hist" -> showHistory(source);
                case "peaks", "peak" -> showPeaks(source);
                case "help" -> showHelp(source);
                default -> showSummary(source);
            };
        }
        return showSummary(source);
    }

    private CommandResult showSummary(CommandSource source) {
        ProxyMetrics metrics = ProxyMetrics.getInstance();
        metrics.updateThroughput();
        metrics.checkHangingRequests();

        ProxyMetrics.MetricsSnapshot snapshot = metrics.createSnapshot();
        Runtime runtime = Runtime.getRuntime();

        long maxMem = runtime.maxMemory();
        long usedMem = runtime.totalMemory() - runtime.freeMemory();

        source.sendMessage("");
        source.sendMessage("========== Proxy Metrics ==========");
        source.sendMessage("Uptime: " + formatDuration(snapshot.uptimeSeconds()));
        source.sendMessage("");
        source.sendMessage("Sessions:");
        source.sendMessage("  Active: " + snapshot.activeSessions());
        source.sendMessage("  Accepted: " + snapshot.connectionsAccepted());
        source.sendMessage("  Closed: " + snapshot.connectionsClosed());
        source.sendMessage("");
        source.sendMessage("Throughput (real-time):");
        source.sendMessage("  Packets/sec IN:  " + String.format("%.1f", snapshot.packetsPerSecIn()));
        source.sendMessage("  Packets/sec OUT: " + String.format("%.1f", snapshot.packetsPerSecOut()));
        source.sendMessage("  Bytes/sec IN:    " + formatBytesRate(snapshot.bytesPerSecIn()));
        source.sendMessage("  Bytes/sec OUT:   " + formatBytesRate(snapshot.bytesPerSecOut()));
        source.sendMessage("");
        source.sendMessage("Response Times:");
        source.sendMessage("  Average:  " + String.format("%.2f ms", snapshot.avgResponseTimeMs()));
        source.sendMessage("  Hanging:  " + snapshot.hangingRequests() + " (>30s pending)");
        source.sendMessage("");
        source.sendMessage("Total Packets:");
        source.sendMessage("  Client → Proxy:  " + formatNumber(snapshot.packetsFromClient()));
        source.sendMessage("  Proxy → Backend: " + formatNumber(snapshot.packetsToBackend()));
        source.sendMessage("  Backend → Proxy: " + formatNumber(snapshot.packetsFromBackend()));
        source.sendMessage("  Proxy → Client:  " + formatNumber(snapshot.packetsToClient()));
        source.sendMessage("");
        source.sendMessage("Network:");
        source.sendMessage("  Bytes received: " + formatBytes(snapshot.bytesFromClient()));
        source.sendMessage("  Bytes sent:     " + formatBytes(snapshot.bytesToClient()));
        source.sendMessage("");
        source.sendMessage("Memory:");
        source.sendMessage("  Used: " + formatBytes(usedMem) + " / " + formatBytes(maxMem) +
                           " (" + percent(usedMem, maxMem) + "%)");
        source.sendMessage("");
        source.sendMessage("Errors:");
        source.sendMessage("  Auth failures:    " + snapshot.authFailures());
        source.sendMessage("  Backend failures: " + snapshot.backendFailures());
        source.sendMessage("");
        source.sendMessage("===================================");
        source.sendMessage("Dashboard: http://localhost:" + getMetricsPort() + "/stats");
        source.sendMessage("Report:    http://localhost:" + getMetricsPort() + "/report");
        source.sendMessage("");

        return CommandResult.success();
    }

    private CommandResult showReport(CommandSource source) {
        ProxyMetrics metrics = ProxyMetrics.getInstance();
        metrics.updateThroughput();
        metrics.checkHangingRequests();

        String report = metrics.createShareableReport();
        source.sendMessage(report);
        source.sendMessage("");
        source.sendMessage("Copy the above report and share it at:");
        source.sendMessage("https://github.com/Numdrassl/proxy/issues");
        return CommandResult.success();
    }

    private CommandResult showHistory(CommandSource source) {
        MetricsHistory history = MetricsHistory.getInstance();

        source.sendMessage("");
        source.sendMessage("========== Historical Metrics ==========");
        source.sendMessage("Data points: " + history.getHistorySize());
        source.sendMessage("");

        // Averages for different periods
        source.sendMessage("--- Averages by Time Period ---");
        showPeriodAverages(source, "Last 5 min", history.getAveragesSince(5, TimeUnit.MINUTES));
        showPeriodAverages(source, "Last 15 min", history.getAveragesSince(15, TimeUnit.MINUTES));
        showPeriodAverages(source, "Last 30 min", history.getAveragesSince(30, TimeUnit.MINUTES));
        showPeriodAverages(source, "Last 1 hour", history.getAveragesSince(1, TimeUnit.HOURS));
        source.sendMessage("");

        source.sendMessage("Use 'metrics peaks' for all-time peak values");
        source.sendMessage("Dashboard: http://localhost:" + getMetricsPort() + "/history");
        source.sendMessage("");

        return CommandResult.success();
    }

    private void showPeriodAverages(CommandSource source, String period, MetricsHistory.AverageStats avg) {
        source.sendMessage(String.format("  %s:", period));
        source.sendMessage(String.format("    Sessions: %.1f avg, %d peak", avg.avgSessions(), avg.peakSessions()));
        source.sendMessage(String.format("    Throughput: %.1f pkt/s, %s/s",
            avg.avgPacketsIn() + avg.avgPacketsOut(),
            formatBytesRate(avg.avgBytesIn() + avg.avgBytesOut())));
        source.sendMessage(String.format("    Response: %.1f ms avg", avg.avgResponseTime()));
    }

    private CommandResult showPeaks(CommandSource source) {
        MetricsHistory history = MetricsHistory.getInstance();

        source.sendMessage("");
        source.sendMessage("========== All-Time Peaks ==========");
        source.sendMessage("(Since proxy start)");
        source.sendMessage("");

        var peakSessions = history.getPeakSessions();
        if (peakSessions != null) {
            source.sendMessage(String.format("Peak Sessions:      %d (at %s)",
                peakSessions.activeSessions(), peakSessions.getFormattedTime()));
        }

        var peakPacketRate = history.getPeakPacketRate();
        if (peakPacketRate != null) {
            source.sendMessage(String.format("Peak Packet Rate:   %.1f/sec (at %s)",
                peakPacketRate.packetsPerSecIn() + peakPacketRate.packetsPerSecOut(),
                peakPacketRate.getFormattedTime()));
        }

        var peakByteRate = history.getPeakByteRate();
        if (peakByteRate != null) {
            source.sendMessage(String.format("Peak Byte Rate:     %s/sec (at %s)",
                formatBytesRate(peakByteRate.bytesPerSecIn() + peakByteRate.bytesPerSecOut()),
                peakByteRate.getFormattedTime()));
        }

        var peakResponseTime = history.getPeakResponseTime();
        if (peakResponseTime != null) {
            source.sendMessage(String.format("Peak Response Time: %.2f ms (at %s)",
                peakResponseTime.avgResponseTimeMs(), peakResponseTime.getFormattedTime()));
        }

        source.sendMessage("");
        source.sendMessage("Full historical report: http://localhost:" + getMetricsPort() + "/history?format=text");
        source.sendMessage("");

        return CommandResult.success();
    }

    private CommandResult showMemoryStats(CommandSource source) {
        Runtime runtime = Runtime.getRuntime();

        long maxMem = runtime.maxMemory();
        long totalMem = runtime.totalMemory();
        long freeMem = runtime.freeMemory();
        long usedMem = totalMem - freeMem;

        source.sendMessage("");
        source.sendMessage("========== Memory Stats ==========");
        source.sendMessage("");
        source.sendMessage("Heap Memory:");
        source.sendMessage("  Used:      " + formatBytes(usedMem));
        source.sendMessage("  Free:      " + formatBytes(freeMem));
        source.sendMessage("  Total:     " + formatBytes(totalMem));
        source.sendMessage("  Max:       " + formatBytes(maxMem));
        source.sendMessage("  Usage:     " + percent(usedMem, maxMem) + "%");
        source.sendMessage("");
        source.sendMessage("JVM:");
        source.sendMessage("  Processors: " + runtime.availableProcessors());
        source.sendMessage("  Java:       " + System.getProperty("java.version"));
        source.sendMessage("");
        source.sendMessage("Tip: Use 'metrics gc' to run garbage collection");
        source.sendMessage("");

        return CommandResult.success();
    }

    private CommandResult triggerGcAndShowMemory(CommandSource source) {
        Runtime runtime = Runtime.getRuntime();

        long beforeUsed = runtime.totalMemory() - runtime.freeMemory();
        source.sendMessage("Running garbage collection...");

        System.gc();

        // Small pause to allow GC to complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        long afterUsed = runtime.totalMemory() - runtime.freeMemory();
        long freed = beforeUsed - afterUsed;

        source.sendMessage("GC complete. Freed " + formatBytes(freed));
        source.sendMessage("Memory now: " + formatBytes(afterUsed) + " / " + formatBytes(runtime.maxMemory()));

        return CommandResult.success();
    }

    private CommandResult showHelp(CommandSource source) {
        source.sendMessage("Usage: metrics [subcommand]");
        source.sendMessage("");
        source.sendMessage("Subcommands:");
        source.sendMessage("  (none)   - Show current statistics");
        source.sendMessage("  history  - Show historical averages");
        source.sendMessage("  peaks    - Show all-time peak values");
        source.sendMessage("  memory   - Show detailed memory info");
        source.sendMessage("  gc       - Trigger GC and show memory");
        source.sendMessage("  report   - Generate shareable report");
        source.sendMessage("  help     - Show this help");
        source.sendMessage("");
        source.sendMessage("Web Dashboard:");
        source.sendMessage("  http://localhost:" + getMetricsPort() + "/stats   - Real-time");
        source.sendMessage("  http://localhost:" + getMetricsPort() + "/history - Historical");
        return CommandResult.success();
    }

    private int getMetricsPort() {
        // Default metrics port
        return 9090;
    }

    private static String formatNumber(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) return String.format("%.1fK", n / 1000.0);
        if (n < 1_000_000_000) return String.format("%.1fM", n / 1_000_000.0);
        return String.format("%.1fB", n / 1_000_000_000.0);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String formatBytesRate(double bytesPerSec) {
        if (bytesPerSec < 1024) return String.format("%.0f B", bytesPerSec);
        if (bytesPerSec < 1024 * 1024) return String.format("%.1f KB", bytesPerSec / 1024.0);
        if (bytesPerSec < 1024 * 1024 * 1024) return String.format("%.1f MB", bytesPerSec / (1024.0 * 1024));
        return String.format("%.2f GB", bytesPerSec / (1024.0 * 1024 * 1024));
    }

    private static String formatDuration(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (days > 0) {
            return String.format("%dd %dh %dm %ds", days, hours, minutes, secs);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }

    private static String percent(long value, long total) {
        if (total == 0) return "0";
        return String.format("%.1f", (value * 100.0) / total);
    }
}

