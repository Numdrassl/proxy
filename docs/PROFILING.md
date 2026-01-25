# Profiling and Monitoring Guide

Numdrassl Proxy includes a comprehensive profiling and monitoring system to help you track performance, identify bottlenecks, and diagnose issues.

## Quick Start

By default, metrics are enabled when the proxy starts. Access them at:

| Endpoint | URL | Description |
|----------|-----|-------------|
| Dashboard | http://localhost:9090/stats | Real-time HTML dashboard |
| History | http://localhost:9090/history | Historical data & peaks |
| Prometheus | http://localhost:9090/metrics | Prometheus scrape endpoint |
| Report | http://localhost:9090/report | Shareable text report |
| Health | http://localhost:9090/health | Health check (JSON) |

## Historical Profiling

The proxy continuously records metrics over time using a **tiered retention** system, allowing you to see what happened during peak loads even days later.

### Tiered Retention Model

| Tier | Resolution | Retention | Data Points | Use Case |
|------|------------|-----------|-------------|----------|
| **Tier 1** | 10 seconds | 1 hour | 360 | Detailed recent analysis |
| **Tier 2** | 1 minute | 24 hours | 1,440 | Hourly patterns |
| **Tier 3** | 10 minutes | 7 days | 1,008 | Weekly trends |
| **Tier 4** | Daily | 90 days | 90 | Monthly/quarterly reports |

This approach balances granularity with memory efficiency:
- **Recent events** (last hour): Full 10-second resolution
- **Today's data** (last 24h): Minute-level aggregates
- **This week** (last 7d): 10-minute aggregates
- **Long-term** (last 90d): Daily summaries

### What Gets Recorded

Each tier captures:
- **Sessions**: Active count, peak count, average
- **Throughput**: Packets/sec, bytes/sec (in/out)
- **Response Times**: Average, peak
- **Errors**: Auth failures, backend failures
- **Hanging Requests**: Requests pending >30 seconds

### Viewing Historical Data

**Web Dashboard:**
```
http://localhost:9090/history
```

**Console Commands:**
```bash
# Show averages for different time periods
metrics history

# Show all-time peak values
metrics peaks
```

**Text Report (comprehensive):**
```
http://localhost:9090/history?format=text
```

### All-Time Peaks

The system tracks all-time peak values since proxy start:

| Peak Metric | Description |
|-------------|-------------|
| Peak Sessions | Maximum concurrent sessions and exact timestamp |
| Peak Packet Rate | Highest packets/second and when it occurred |
| Peak Byte Rate | Highest bytes/second throughput |
| Peak Response Time | Slowest average response time |

These peaks persist until proxy restart, allowing you to identify maximum loads even days later.

## Configuration

Add these settings to your `config.yml`:

```yaml
# Enable/disable the metrics system
metricsEnabled: true

# HTTP port for metrics endpoints
metricsPort: 9090

# Interval (in seconds) for logging metrics to console (0 to disable)
metricsLogIntervalSeconds: 60
```

## Console Commands

Use the `metrics` command in the proxy console:

```bash
# Show current statistics
metrics

# Show historical averages (5min, 15min, 30min, 1hr)
metrics history

# Show all-time peak values
metrics peaks

# Show detailed memory info
metrics memory

# Trigger garbage collection
metrics gc

# Generate shareable report
metrics report

# Show help
metrics help
```

Aliases: `stats`, `perf`, `performance`

## Available Metrics

### Session Metrics
| Metric | Description |
|--------|-------------|
| `proxy_sessions_active` | Currently active sessions |
| `proxy_sessions_count` | Total registered sessions |
| `proxy_connections_accepted_total` | Total accepted connections |
| `proxy_connections_rejected_total` | Total rejected connections |
| `proxy_connections_closed_total` | Total closed connections |

### Packet Metrics
| Metric | Description |
|--------|-------------|
| `proxy_packets_total{direction}` | Total packets by direction |
| `proxy_packets_by_type_total{type,direction}` | Packets by type |
| `proxy_bytes_total{direction}` | Total bytes transferred |
| `proxy_packet_size_bytes{direction}` | Packet size distribution |

### Throughput Metrics
| Metric | Description |
|--------|-------------|
| `proxy_throughput_packets_per_sec{direction}` | Real-time packet rate |
| `proxy_throughput_bytes_per_sec{direction}` | Real-time byte rate |

### Response Time Metrics
| Metric | Description |
|--------|-------------|
| `proxy_backend_response_duration` | Backend response time histogram |
| `proxy_hanging_requests` | Requests pending >30 seconds |
| `proxy_packet_processing_duration` | Packet processing time |

### Backend Metrics
| Metric | Description |
|--------|-------------|
| `proxy_backend_connections_total{backend}` | Connections per backend |
| `proxy_backend_failures_total{backend}` | Failures per backend |
| `proxy_backend_active_connections{backend}` | Active connections per backend |
| `proxy_backend_connect_duration` | Backend connection time |

### Error Metrics
| Metric | Description |
|--------|-------------|
| `proxy_errors_total{type=packet_decode}` | Packet decode errors |
| `proxy_errors_total{type=packet_encode}` | Packet encode errors |
| `proxy_errors_total{type=authentication}` | Authentication failures |
| `proxy_errors_total{type=backend_connection}` | Backend connection failures |

### JVM Metrics
The following JVM metrics are automatically collected:

- Memory usage (heap, non-heap, pools)
- Garbage collection stats
- Thread counts
- CPU usage
- Class loading
- Uptime

## Prometheus Integration

Add this to your `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'numdrassl-proxy'
    scrape_interval: 15s
    static_configs:
      - targets: ['localhost:9090']
```

### Example Prometheus Queries

```promql
# Active sessions over time
proxy_sessions_active

# Packet rate (packets/second)
rate(proxy_packets_total{direction="from_client"}[1m])

# Average response time
proxy_backend_response_duration_seconds_mean

# 95th percentile response time
histogram_quantile(0.95, rate(proxy_backend_response_duration_seconds_bucket[5m]))

# Memory usage percentage
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100

# Error rate
rate(proxy_errors_total[5m])
```

## Grafana Dashboard

You can import our pre-built Grafana dashboard or create your own using these panels:

### Recommended Panels

1. **Session Overview**
   - Active sessions (gauge)
   - Connections accepted/closed (counter)

2. **Throughput**
   - Packets per second IN/OUT (graph)
   - Bytes per second IN/OUT (graph)

3. **Response Times**
   - Average response time (gauge)
   - Response time histogram (heatmap)
   - Hanging requests (stat)

4. **Memory**
   - Heap usage (gauge with threshold)
   - GC pause times (graph)

5. **Errors**
   - Error rate by type (graph)
   - Total errors (stat)

## Sharing Reports

When reporting issues or asking for help, generate a report to share:

### Option 1: HTTP Endpoint
Visit `http://localhost:9090/report` and copy the text.

### Option 2: Console Command
```bash
metrics report
```

### Option 3: Dashboard Button
Click "Generate Shareable Report" on the `/stats` dashboard.

### Report Contents

The report includes:
- Uptime and timestamp
- Session/connection counts
- Packet throughput (total and real-time)
- Response times
- Memory usage
- Error counts
- System info (Java version, OS, CPUs)

**Example Report:**
```
================================================================================
                       NUMDRASSL PROXY METRICS REPORT
================================================================================
Generated: 2026-01-25T12:34:56
Uptime: 2d 5h 30m 45s

--- SESSIONS & CONNECTIONS ---
Active Sessions:      42
Connections Accepted: 1,234
Connections Closed:   1,192

--- PACKET THROUGHPUT ---
Total Packets In:     5,678,901
Total Packets Out:    5,432,100
Current Rate In:      1,234.5 packets/sec
Current Rate Out:     1,200.0 packets/sec

--- NETWORK I/O ---
Total Bytes In:       1.2 GB
Total Bytes Out:      856.3 MB
Current Rate In:      2.5 MB/sec
Current Rate Out:     2.1 MB/sec

--- RESPONSE TIMES ---
Average Response:     12.34 ms
Hanging Requests:     0 (>30s pending)

--- MEMORY ---
Heap Used:            256.3 MB
Heap Max:             1.0 GB
Usage:                25.6%

--- ERRORS ---
Auth Failures:        0
Backend Failures:     2

--- SYSTEM ---
Java Version:         21.0.1
Available CPUs:       8
OS:                   Linux 5.15.0

================================================================================
Share this report at: https://github.com/Numdrassl/proxy/issues
================================================================================
```

## Programmatic Access

Plugins can access metrics programmatically:

```java
import me.internalizable.numdrassl.profiling.ProxyMetrics;
import me.internalizable.numdrassl.profiling.TimingUtils;

// Get the metrics instance
ProxyMetrics metrics = ProxyMetrics.getInstance();

// Create a snapshot
ProxyMetrics.MetricsSnapshot snapshot = metrics.createSnapshot();
System.out.println("Active sessions: " + snapshot.activeSessions());
System.out.println("Packets/sec IN: " + snapshot.packetsPerSecIn());

// Time an operation
TimingUtils.time("my_operation", () -> {
    // Do something
});

// Or with return value
String result = TimingUtils.timeAndReturn("fetch_data", () -> {
    return fetchData();
});

// Manual timing
TimingUtils.OperationTimer timer = TimingUtils.start();
// ... do work ...
timer.stopAndRecord("my_operation");
```

## Performance Tips

### Monitoring Recommendations

1. **Set up alerts** for:
   - Memory usage > 80%
   - Hanging requests > 0
   - Error rate spikes
   - Response time > 100ms

2. **Regular health checks**:
   - Monitor `/health` endpoint
   - Track uptime
   - Watch for memory leaks

3. **Capacity planning**:
   - Track session counts over time
   - Monitor packet throughput trends
   - Plan for peak loads

### Troubleshooting High Resource Usage

**High Memory:**
1. Check for memory leaks using `metrics memory`
2. Run `metrics gc` to trigger GC
3. Monitor GC metrics in Prometheus
4. Consider increasing heap size

**Slow Response Times:**
1. Check `proxy_backend_response_duration`
2. Look for hanging requests
3. Verify backend server health
4. Check network latency

**High CPU:**
1. Monitor packet processing times
2. Check for excessive logging
3. Review plugin performance
4. Consider horizontal scaling

## Security Considerations

⚠️ **Important**: The metrics endpoint exposes detailed system information.

- Bind metrics only to localhost in production
- Use a reverse proxy with authentication for remote access
- Don't expose the metrics port to the public internet
- Consider disabling metrics in sensitive environments

## Troubleshooting

### Metrics Not Available

1. Check `metricsEnabled: true` in config
2. Verify port 9090 is not in use
3. Check firewall rules
4. Look for errors in proxy logs

### Dashboard Not Loading

1. Ensure proxy is running
2. Check browser console for errors
3. Verify correct URL
4. Try a different browser

### Prometheus Not Scraping

1. Verify scrape config in prometheus.yml
2. Check target is reachable
3. Look for scrape errors in Prometheus
4. Ensure metrics endpoint returns 200 OK

