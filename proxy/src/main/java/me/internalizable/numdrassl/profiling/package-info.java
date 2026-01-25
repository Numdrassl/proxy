/**
 * Profiling and metrics subsystem for the Numdrassl proxy.
 *
 * <p>This package provides comprehensive performance monitoring capabilities:</p>
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.profiling.ProxyMetrics} - Central metrics registry</li>
 *   <li>{@link me.internalizable.numdrassl.profiling.MetricsHttpServer} - HTTP endpoint for Prometheus</li>
 *   <li>{@link me.internalizable.numdrassl.profiling.MetricsLogger} - Periodic logging of metrics</li>
 *   <li>{@link me.internalizable.numdrassl.profiling.TimingUtils} - Utilities for timing operations</li>
 * </ul>
 *
 * <h2>Available Metrics</h2>
 * <table>
 *   <tr><th>Category</th><th>Metrics</th></tr>
 *   <tr><td>JVM</td><td>Memory, GC, threads, CPU</td></tr>
 *   <tr><td>Sessions</td><td>Active, accepted, rejected, closed</td></tr>
 *   <tr><td>Packets</td><td>Count, bytes, by type, by direction</td></tr>
 *   <tr><td>Throughput</td><td>Real-time packets/sec, bytes/sec</td></tr>
 *   <tr><td>Response Times</td><td>Average response, hanging detection</td></tr>
 *   <tr><td>Backends</td><td>Connections, failures, latency</td></tr>
 *   <tr><td>Timing</td><td>Packet processing, auth, transfers</td></tr>
 * </table>
 *
 * <h2>Access Points</h2>
 * <ul>
 *   <li><b>HTTP</b>: {@code http://localhost:9090/metrics} (Prometheus format)</li>
 *   <li><b>Dashboard</b>: {@code http://localhost:9090/stats} (HTML)</li>
 *   <li><b>Report</b>: {@code http://localhost:9090/report} (Shareable text)</li>
 *   <li><b>Console</b>: {@code metrics} command</li>
 *   <li><b>Logs</b>: Periodic summary via {@link me.internalizable.numdrassl.profiling.MetricsLogger}</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * # In config.yml
 * metricsEnabled: true
 * metricsPort: 9090
 * metricsLogIntervalSeconds: 60
 * }</pre>
 *
 * <h2>Prometheus Integration</h2>
 * <pre>{@code
 * # prometheus.yml
 * scrape_configs:
 *   - job_name: 'numdrassl-proxy'
 *     static_configs:
 *       - targets: ['localhost:9090']
 * }</pre>
 *
 * <h2>Shareable Reports</h2>
 * <p>Users can generate and share diagnostic reports via:</p>
 * <ul>
 *   <li>HTTP: Visit {@code /report} endpoint</li>
 *   <li>Console: Run {@code metrics report}</li>
 *   <li>Dashboard: Click "Generate Shareable Report" button</li>
 * </ul>
 *
 * @since 1.0.2
 */
package me.internalizable.numdrassl.profiling;

