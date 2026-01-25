package me.internalizable.numdrassl.profiling;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP server for exposing metrics endpoints.
 *
 * <p>Provides the following endpoints:</p>
 * <ul>
 *   <li><b>/metrics</b> - Prometheus-compatible metrics scrape endpoint</li>
 *   <li><b>/health</b> - Simple health check (returns 200 OK)</li>
 *   <li><b>/stats</b> - Human-readable metrics summary</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * MetricsHttpServer server = new MetricsHttpServer(9090);
 * server.start();
 * // Metrics available at http://localhost:9090/metrics
 * }</pre>
 *
 * <p>Prometheus scrape config:</p>
 * <pre>{@code
 * scrape_configs:
 *   - job_name: 'numdrassl-proxy'
 *     static_configs:
 *       - targets: ['localhost:9090']
 * }</pre>
 */
public final class MetricsHttpServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsHttpServer.class);

    private static final String CONTENT_TYPE_TEXT = "text/plain; charset=utf-8";
    private static final String CONTENT_TYPE_PROMETHEUS = "text/plain; version=0.0.4; charset=utf-8";
    private static final String CONTENT_TYPE_HTML = "text/html; charset=utf-8";

    private final int port;
    private HttpServer server;
    private volatile boolean running = false;

    /**
     * Creates a new metrics HTTP server.
     *
     * @param port the port to bind to
     */
    public MetricsHttpServer(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
        this.port = port;
    }

    /**
     * Starts the HTTP server.
     *
     * @throws IOException if binding fails
     */
    public void start() throws IOException {
        if (running) {
            LOGGER.warn("Metrics server already running");
            return;
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        // Register endpoints
        server.createContext("/metrics", this::handleMetrics);
        server.createContext("/health", this::handleHealth);
        server.createContext("/stats", this::handleStats);
        server.createContext("/history", this::handleHistory);
        server.createContext("/report", this::handleReport);
        server.createContext("/", this::handleRoot);

        server.start();
        running = true;

        LOGGER.info("Metrics HTTP server started on port {}", port);
        LOGGER.info("  /metrics  - Prometheus scrape endpoint");
        LOGGER.info("  /health   - Health check endpoint");
        LOGGER.info("  /stats    - Real-time stats dashboard");
        LOGGER.info("  /history  - Historical data & peaks");
        LOGGER.info("  /report   - Shareable text report");
    }

    /**
     * Stops the HTTP server.
     */
    public void stop() {
        if (!running || server == null) {
            return;
        }

        server.stop(1);
        running = false;
        LOGGER.info("Metrics HTTP server stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    // ==================== Handlers ====================

    private void handleMetrics(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, CONTENT_TYPE_TEXT, "Method Not Allowed");
            return;
        }

        String metrics = ProxyMetrics.getInstance().scrape();
        sendResponse(exchange, 200, CONTENT_TYPE_PROMETHEUS, metrics);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, CONTENT_TYPE_TEXT, "Method Not Allowed");
            return;
        }

        String response = """
            {
              "status": "UP",
              "timestamp": %d
            }
            """.formatted(System.currentTimeMillis());

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        sendResponse(exchange, 200, "application/json; charset=utf-8", response);
    }

    private void handleStats(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, CONTENT_TYPE_TEXT, "Method Not Allowed");
            return;
        }

        ProxyMetrics metrics = ProxyMetrics.getInstance();
        metrics.updateThroughput(); // Update throughput calculations
        metrics.checkHangingRequests(); // Check for hanging requests

        ProxyMetrics.MetricsSnapshot snapshot = metrics.createSnapshot();
        Runtime runtime = Runtime.getRuntime();

        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Numdrassl Proxy Stats</title>
                <meta http-equiv="refresh" content="5">
                <style>
                    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 20px; background: #1a1a2e; color: #eee; }
                    h1 { color: #00d9ff; }
                    h2 { color: #00ff88; border-bottom: 1px solid #333; padding-bottom: 5px; }
                    .stat-group { background: #16213e; padding: 15px; margin: 10px 0; border-radius: 8px; }
                    .stat { display: inline-block; margin: 10px 20px; }
                    .stat-value { font-size: 24px; font-weight: bold; color: #00d9ff; }
                    .stat-label { font-size: 12px; color: #888; }
                    .good { color: #00ff88; }
                    .warn { color: #ffaa00; }
                    .bad { color: #ff4444; }
                    table { border-collapse: collapse; width: 100%%; }
                    th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #333; }
                    th { background: #0f3460; color: #00d9ff; }
                    tr:hover { background: #1f4068; }
                    .progress { background: #333; border-radius: 4px; height: 20px; }
                    .progress-bar { background: linear-gradient(90deg, #00d9ff, #00ff88); height: 100%%; border-radius: 4px; }
                    .btn { background: #00d9ff; color: #1a1a2e; padding: 10px 20px; border: none; border-radius: 4px; cursor: pointer; font-weight: bold; text-decoration: none; display: inline-block; margin: 5px; }
                    .btn:hover { background: #00ff88; }
                    .btn-secondary { background: #333; color: #eee; }
                    .btn-secondary:hover { background: #444; }
                </style>
            </head>
            <body>
                <h1>🚀 Numdrassl Proxy Stats</h1>
                <p>Auto-refreshes every 5 seconds | Uptime: %s</p>
                
                <div class="stat-group">
                    <h2>📊 Sessions & Connections</h2>
                    <div class="stat">
                        <div class="stat-value">%d</div>
                        <div class="stat-label">Active Sessions</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value good">%d</div>
                        <div class="stat-label">Connections Accepted</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value">%d</div>
                        <div class="stat-label">Connections Closed</div>
                    </div>
                </div>
                
                <div class="stat-group">
                    <h2>⚡ Real-time Throughput</h2>
                    <div class="stat">
                        <div class="stat-value">%.1f</div>
                        <div class="stat-label">Packets/sec IN</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value">%.1f</div>
                        <div class="stat-label">Packets/sec OUT</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value">%s</div>
                        <div class="stat-label">Bytes/sec IN</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value">%s</div>
                        <div class="stat-label">Bytes/sec OUT</div>
                    </div>
                </div>
                
                <div class="stat-group">
                    <h2>⏱️ Response Times</h2>
                    <div class="stat">
                        <div class="stat-value %s">%.1f ms</div>
                        <div class="stat-label">Avg Response Time</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value %s">%d</div>
                        <div class="stat-label">Hanging Requests (>30s)</div>
                    </div>
                </div>
                
                <div class="stat-group">
                    <h2>📦 Total Packet Throughput</h2>
                    <table>
                        <tr>
                            <th>Direction</th>
                            <th>Packets</th>
                            <th>Bytes</th>
                        </tr>
                        <tr>
                            <td>Client → Proxy</td>
                            <td>%,d</td>
                            <td>%s</td>
                        </tr>
                        <tr>
                            <td>Proxy → Backend</td>
                            <td>%,d</td>
                            <td>-</td>
                        </tr>
                        <tr>
                            <td>Backend → Proxy</td>
                            <td>%,d</td>
                            <td>-</td>
                        </tr>
                        <tr>
                            <td>Proxy → Client</td>
                            <td>%,d</td>
                            <td>%s</td>
                        </tr>
                    </table>
                </div>
                
                <div class="stat-group">
                    <h2>💾 Memory Usage</h2>
                    <div class="progress" style="margin: 10px 0;">
                        <div class="progress-bar" style="width: %.1f%%;"></div>
                    </div>
                    <p>%s used / %s max (%.1f%%)</p>
                    <table>
                        <tr><td>Heap Used</td><td>%s</td></tr>
                        <tr><td>Heap Total</td><td>%s</td></tr>
                        <tr><td>Heap Max</td><td>%s</td></tr>
                        <tr><td>Available Processors</td><td>%d</td></tr>
                    </table>
                </div>
                
                <div class="stat-group">
                    <h2>⚠️ Errors</h2>
                    <div class="stat">
                        <div class="stat-value %s">%d</div>
                        <div class="stat-label">Auth Failures</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value %s">%d</div>
                        <div class="stat-label">Backend Failures</div>
                    </div>
                </div>
                
                <div class="stat-group">
                    <h2>📤 Share Report</h2>
                    <p>Need to report an issue or share performance data?</p>
                    <a href="/report" class="btn" target="_blank">📋 Generate Shareable Report</a>
                    <a href="/metrics" class="btn btn-secondary" target="_blank">Prometheus Metrics</a>
                </div>
                
                <p style="color: #666; font-size: 12px;">
                    Generated at: %s
                </p>
            </body>
            </html>
            """.formatted(
            formatDuration(snapshot.uptimeSeconds()),
            snapshot.activeSessions(),
            snapshot.connectionsAccepted(),
            snapshot.connectionsClosed(),
            snapshot.packetsPerSecIn(),
            snapshot.packetsPerSecOut(),
            formatBytesRate(snapshot.bytesPerSecIn()),
            formatBytesRate(snapshot.bytesPerSecOut()),
            snapshot.avgResponseTimeMs() > 100 ? "warn" : "good",
            snapshot.avgResponseTimeMs(),
            snapshot.hangingRequests() > 0 ? "bad" : "good",
            snapshot.hangingRequests(),
            snapshot.packetsFromClient(),
            formatBytes(snapshot.bytesFromClient()),
            snapshot.packetsToBackend(),
            snapshot.packetsFromBackend(),
            snapshot.packetsToClient(),
            formatBytes(snapshot.bytesToClient()),
            (double) usedMemory / maxMemory * 100,
            formatBytes(usedMemory),
            formatBytes(maxMemory),
            (double) usedMemory / maxMemory * 100,
            formatBytes(usedMemory),
            formatBytes(totalMemory),
            formatBytes(maxMemory),
            runtime.availableProcessors(),
            snapshot.authFailures() > 0 ? "warn" : "good",
            snapshot.authFailures(),
            snapshot.backendFailures() > 0 ? "warn" : "good",
            snapshot.backendFailures(),
            java.time.LocalDateTime.now()
        );

        sendResponse(exchange, 200, CONTENT_TYPE_HTML, html);
    }

    private void handleReport(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, CONTENT_TYPE_TEXT, "Method Not Allowed");
            return;
        }

        ProxyMetrics metrics = ProxyMetrics.getInstance();
        metrics.updateThroughput();
        metrics.checkHangingRequests();

        String report = metrics.createShareableReport();
        sendResponse(exchange, 200, CONTENT_TYPE_TEXT, report);
    }

    private void handleHistory(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, CONTENT_TYPE_TEXT, "Method Not Allowed");
            return;
        }

        // Check for text format query param
        String query = exchange.getRequestURI().getQuery();
        if (query != null && query.contains("format=text")) {
            String report = MetricsHistory.getInstance().createHistoricalReport();
            sendResponse(exchange, 200, CONTENT_TYPE_TEXT, report);
            return;
        }

        MetricsHistory history = MetricsHistory.getInstance();
        MetricsHistory.HistoricalSnapshot peak = history.getPeakSessions();
        MetricsHistory.AverageStats avg5m = history.getAveragesSince(5, java.util.concurrent.TimeUnit.MINUTES);
        MetricsHistory.AverageStats avg30m = history.getAveragesSince(30, java.util.concurrent.TimeUnit.MINUTES);
        MetricsHistory.AverageStats avg1h = history.getAveragesSince(1, java.util.concurrent.TimeUnit.HOURS);

        // Build timeline data for chart (last 30 minutes, 10-sec resolution)
        var recentHistory = history.getHistorySince(30, java.util.concurrent.TimeUnit.MINUTES);
        StringBuilder timelineData = new StringBuilder("[");
        for (int i = 0; i < recentHistory.size(); i++) {
            var s = recentHistory.get(i);
            if (i > 0) timelineData.append(",");
            timelineData.append(String.format("{t:%d,s:%d,p:%.1f,b:%.1f,r:%.1f}",
                s.timestamp(), s.activeSessions(),
                s.packetsPerSecIn() + s.packetsPerSecOut(),
                s.bytesPerSecIn() + s.bytesPerSecOut(),
                s.avgResponseTimeMs()));
        }
        timelineData.append("]");

        // Build daily data for longer-term chart
        var dailySummaries = history.getDailySummaries();
        StringBuilder dailyData = new StringBuilder("[");
        for (int i = 0; i < dailySummaries.size(); i++) {
            var d = dailySummaries.get(i);
            if (i > 0) dailyData.append(",");
            dailyData.append(String.format("{t:%d,max:%d,avg:%.1f,pkt:%.1f}",
                d.timestamp(), d.maxSessions(), d.avgSessions(), d.avgPacketsPerSec()));
        }
        dailyData.append("]");

        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Numdrassl Proxy - History</title>
                <meta http-equiv="refresh" content="10">
                <style>
                    body { font-family: 'Segoe UI', Tahoma, sans-serif; margin: 20px; background: #1a1a2e; color: #eee; }
                    h1 { color: #00d9ff; }
                    h2 { color: #00ff88; border-bottom: 1px solid #333; padding-bottom: 5px; }
                    .stat-group { background: #16213e; padding: 15px; margin: 10px 0; border-radius: 8px; }
                    .stat { display: inline-block; margin: 10px 20px; text-align: center; }
                    .stat-value { font-size: 24px; font-weight: bold; color: #00d9ff; }
                    .stat-label { font-size: 12px; color: #888; }
                    .peak { color: #ff6b6b; }
                    .tier-info { display: inline-block; background: #0f3460; padding: 5px 10px; margin: 3px; border-radius: 4px; font-size: 12px; }
                    table { border-collapse: collapse; width: 100%%; margin-top: 10px; }
                    th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #333; }
                    th { background: #0f3460; color: #00d9ff; }
                    .btn { background: #00d9ff; color: #1a1a2e; padding: 10px 20px; border: none; border-radius: 4px; cursor: pointer; text-decoration: none; display: inline-block; margin: 5px; }
                    .btn:hover { background: #00ff88; }
                    .chart { background: #0f3460; padding: 20px; border-radius: 8px; margin: 10px 0; }
                    .chart canvas { width: 100%%; height: 200px; }
                    nav { margin-bottom: 20px; }
                    nav a { color: #00d9ff; margin-right: 20px; }
                </style>
            </head>
            <body>
                <nav>
                    <a href="/stats">📊 Real-time</a>
                    <a href="/history">📈 History</a>
                    <a href="/report">📋 Report</a>
                    <a href="/metrics">⚙️ Prometheus</a>
                </nav>
                
                <h1>📈 Historical Metrics</h1>
                <p>Auto-refreshes every 10 seconds</p>
                <div>
                    <span class="tier-info">🔴 Tier 1: %d pts (10s res, 1hr)</span>
                    <span class="tier-info">🟡 Tier 2: %d pts (1min res, 24hr)</span>
                    <span class="tier-info">🟢 Tier 3: %d pts (10min res, 7d)</span>
                    <span class="tier-info">🔵 Tier 4: %d pts (daily, 90d)</span>
                </div>
                
                <div class="stat-group">
                    <h2>🏆 All-Time Peaks (since proxy start)</h2>
                    <div class="stat">
                        <div class="stat-value peak">%d</div>
                        <div class="stat-label">Peak Sessions%s</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value peak">%.1f/s</div>
                        <div class="stat-label">Peak Packet Rate%s</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value peak">%s/s</div>
                        <div class="stat-label">Peak Byte Rate%s</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value peak">%.1f ms</div>
                        <div class="stat-label">Peak Response Time%s</div>
                    </div>
                </div>
                
                <div class="stat-group">
                    <h2>📊 Averages by Time Period</h2>
                    <table>
                        <tr>
                            <th>Period</th>
                            <th>Avg Sessions</th>
                            <th>Peak Sessions</th>
                            <th>Avg Packets/s</th>
                            <th>Avg Bytes/s</th>
                            <th>Avg Response</th>
                        </tr>
                        <tr>
                            <td>Last 5 min</td>
                            <td>%.1f</td>
                            <td>%d</td>
                            <td>%.1f</td>
                            <td>%s</td>
                            <td>%.1f ms</td>
                        </tr>
                        <tr>
                            <td>Last 30 min</td>
                            <td>%.1f</td>
                            <td>%d</td>
                            <td>%.1f</td>
                            <td>%s</td>
                            <td>%.1f ms</td>
                        </tr>
                        <tr>
                            <td>Last 1 hour</td>
                            <td>%.1f</td>
                            <td>%d</td>
                            <td>%.1f</td>
                            <td>%s</td>
                            <td>%.1f ms</td>
                        </tr>
                    </table>
                </div>
                
                <div class="stat-group">
                    <h2>📉 Session History (Last 30 min)</h2>
                    <div class="chart">
                        <canvas id="sessionChart"></canvas>
                    </div>
                </div>
                
                <div class="stat-group">
                    <h2>📤 Export</h2>
                    <a href="/history?format=text" class="btn" target="_blank">📋 Full Historical Report (Text)</a>
                    <a href="/report" class="btn" target="_blank">📊 Current Snapshot Report</a>
                </div>
                
                <script>
                const data = %s;
                const canvas = document.getElementById('sessionChart');
                const ctx = canvas.getContext('2d');
                
                function drawChart() {
                    const width = canvas.width = canvas.offsetWidth;
                    const height = canvas.height = 200;
                    ctx.clearRect(0, 0, width, height);
                    
                    if (data.length < 2) {
                        ctx.fillStyle = '#888';
                        ctx.fillText('Not enough data yet...', width/2 - 50, height/2);
                        return;
                    }
                    
                    const maxS = Math.max(...data.map(d => d.s), 1);
                    const padding = 40;
                    const chartWidth = width - padding * 2;
                    const chartHeight = height - padding * 2;
                    
                    // Draw grid
                    ctx.strokeStyle = '#333';
                    ctx.lineWidth = 1;
                    for (let i = 0; i <= 4; i++) {
                        const y = padding + (chartHeight / 4) * i;
                        ctx.beginPath();
                        ctx.moveTo(padding, y);
                        ctx.lineTo(width - padding, y);
                        ctx.stroke();
                        
                        ctx.fillStyle = '#888';
                        ctx.fillText(Math.round(maxS - (maxS/4)*i), 5, y + 4);
                    }
                    
                    // Draw session line
                    ctx.strokeStyle = '#00d9ff';
                    ctx.lineWidth = 2;
                    ctx.beginPath();
                    data.forEach((d, i) => {
                        const x = padding + (i / (data.length - 1)) * chartWidth;
                        const y = padding + chartHeight - (d.s / maxS) * chartHeight;
                        if (i === 0) ctx.moveTo(x, y);
                        else ctx.lineTo(x, y);
                    });
                    ctx.stroke();
                    
                    // Labels
                    ctx.fillStyle = '#00d9ff';
                    ctx.fillText('Sessions', width - 60, 20);
                }
                
                drawChart();
                window.addEventListener('resize', drawChart);
                </script>
            </body>
            </html>
            """.formatted(
            history.getHistorySize(),
            history.getTier2Size(),
            history.getTier3Size(),
            history.getTier4Size(),
            peak != null ? peak.activeSessions() : 0,
            peak != null ? " at " + peak.getFormattedTime() : "",
            history.getPeakPacketRate() != null ?
                history.getPeakPacketRate().packetsPerSecIn() + history.getPeakPacketRate().packetsPerSecOut() : 0,
            history.getPeakPacketRate() != null ? " at " + history.getPeakPacketRate().getFormattedTime() : "",
            history.getPeakByteRate() != null ?
                formatBytesRate(history.getPeakByteRate().bytesPerSecIn() + history.getPeakByteRate().bytesPerSecOut()) : "0 B",
            history.getPeakByteRate() != null ? " at " + history.getPeakByteRate().getFormattedTime() : "",
            history.getPeakResponseTime() != null ? history.getPeakResponseTime().avgResponseTimeMs() : 0,
            history.getPeakResponseTime() != null ? " at " + history.getPeakResponseTime().getFormattedTime() : "",
            avg5m.avgSessions(), avg5m.peakSessions(),
            avg5m.avgPacketsIn() + avg5m.avgPacketsOut(),
            formatBytesRate(avg5m.avgBytesIn() + avg5m.avgBytesOut()),
            avg5m.avgResponseTime(),
            avg30m.avgSessions(), avg30m.peakSessions(),
            avg30m.avgPacketsIn() + avg30m.avgPacketsOut(),
            formatBytesRate(avg30m.avgBytesIn() + avg30m.avgBytesOut()),
            avg30m.avgResponseTime(),
            avg1h.avgSessions(), avg1h.peakSessions(),
            avg1h.avgPacketsIn() + avg1h.avgPacketsOut(),
            formatBytesRate(avg1h.avgBytesIn() + avg1h.avgBytesOut()),
            avg1h.avgResponseTime(),
            timelineData.toString()
        );

        sendResponse(exchange, 200, CONTENT_TYPE_HTML, html);
    }

    private void handleRoot(HttpExchange exchange) throws IOException {
        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Numdrassl Proxy</title>
                <style>
                    body { font-family: sans-serif; margin: 40px; background: #1a1a2e; color: #eee; }
                    h1 { color: #00d9ff; }
                    a { color: #00ff88; text-decoration: none; }
                    a:hover { text-decoration: underline; }
                    ul { line-height: 2; }
                    .highlight { color: #00d9ff; }
                </style>
            </head>
            <body>
                <h1>🚀 Numdrassl Proxy Metrics</h1>
                <ul>
                    <li><a href="/stats">/stats</a> - <span class="highlight">Real-time dashboard</span></li>
                    <li><a href="/history">/history</a> - <span class="highlight">Historical data & peaks</span></li>
                    <li><a href="/report">/report</a> - Shareable text report</li>
                    <li><a href="/metrics">/metrics</a> - Prometheus scrape endpoint</li>
                    <li><a href="/health">/health</a> - Health check (JSON)</li>
                </ul>
            </body>
            </html>
            """;
        sendResponse(exchange, 200, CONTENT_TYPE_HTML, html);
    }

    private void sendResponse(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
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
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }
}

