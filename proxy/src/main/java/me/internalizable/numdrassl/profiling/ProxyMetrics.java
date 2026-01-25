package me.internalizable.numdrassl.profiling;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.jvm.*;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import me.internalizable.numdrassl.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Central metrics registry for the Numdrassl proxy.
 *
 * <p>Provides comprehensive profiling capabilities:</p>
 * <ul>
 *   <li><b>JVM Metrics</b>: Memory, GC, threads, CPU usage</li>
 *   <li><b>Connection Metrics</b>: Active sessions, connections per second</li>
 *   <li><b>Packet Metrics</b>: Throughput, latency, packet types</li>
 *   <li><b>Backend Metrics</b>: Connection pool status, response times</li>
 *   <li><b>Network I/O</b>: Bytes sent/received per direction</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // Record packet sent to client
 * ProxyMetrics.getInstance().recordPacketToClient("Connect", 256);
 *
 * // Time an operation
 * Timer.Sample sample = ProxyMetrics.getInstance().startTimer();
 * // ... do work ...
 * ProxyMetrics.getInstance().stopTimer(sample, "backend_connect");
 * }</pre>
 *
 * @see MetricsHttpServer for accessing metrics via HTTP
 */
public final class ProxyMetrics {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyMetrics.class);

    private static volatile ProxyMetrics instance;

    private final PrometheusMeterRegistry registry;
    private final MeterRegistry compositeRegistry;

    // ==================== Counters ====================

    // Connection counters
    private final Counter connectionsAccepted;
    private final Counter connectionsRejected;
    private final Counter connectionsClosed;

    // Packet counters (direction-aware)
    private final Counter packetsFromClient;
    private final Counter packetsToClient;
    private final Counter packetsFromBackend;
    private final Counter packetsToBackend;

    // Byte counters
    private final Counter bytesFromClient;
    private final Counter bytesToClient;
    private final Counter bytesFromBackend;
    private final Counter bytesToBackend;

    // Error counters
    private final Counter packetDecodeErrors;
    private final Counter packetEncodeErrors;
    private final Counter authenticationFailures;
    private final Counter backendConnectionFailures;

    // Transfer counters
    private final Counter serverTransfersInitiated;
    private final Counter serverTransfersCompleted;
    private final Counter serverTransfersFailed;

    // ==================== Gauges ====================

    private final AtomicLong activeSessionsGauge = new AtomicLong(0);
    private final AtomicLong pendingBackendConnectionsGauge = new AtomicLong(0);

    // ==================== Timers ====================

    private final Timer packetProcessingTimer;
    private final Timer backendConnectTimer;
    private final Timer authenticationTimer;
    private final Timer serverTransferTimer;

    // ==================== Distribution Summaries ====================

    private final DistributionSummary packetSizeFromClient;
    private final DistributionSummary packetSizeToClient;
    private final DistributionSummary packetSizeFromBackend;
    private final DistributionSummary packetSizeToBackend;

    // ==================== Per-packet-type tracking ====================

    private final ConcurrentHashMap<String, Counter> packetTypeCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> packetTypeTimers = new ConcurrentHashMap<>();

    // ==================== Per-backend tracking ====================

    private final ConcurrentHashMap<String, Counter> backendConnectionCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> backendActiveConnections = new ConcurrentHashMap<>();

    // ==================== Rate tracking (for throughput) ====================

    private final LongAdder packetsPerSecondClient = new LongAdder();
    private final LongAdder packetsPerSecondBackend = new LongAdder();
    private final LongAdder bytesPerSecondIn = new LongAdder();
    private final LongAdder bytesPerSecondOut = new LongAdder();

    // ==================== Throughput tracking ====================

    private final AtomicLong lastThroughputUpdate = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastPacketsFromClient = new AtomicLong(0);
    private final AtomicLong lastPacketsToClient = new AtomicLong(0);
    private final AtomicLong lastBytesIn = new AtomicLong(0);
    private final AtomicLong lastBytesOut = new AtomicLong(0);

    // Throughput gauges (packets/sec, bytes/sec)
    private volatile double packetsPerSecIn = 0;
    private volatile double packetsPerSecOut = 0;
    private volatile double bytesPerSecIn = 0;
    private volatile double bytesPerSecOut = 0;

    // ==================== Response time tracking ====================

    private final Timer backendResponseTimer;
    private final ConcurrentHashMap<Long, Long> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicLong hangingRequestsCount = new AtomicLong(0);
    private static final long HANGING_THRESHOLD_MS = 30_000; // 30 seconds

    // ==================== Uptime tracking ====================

    private final long startTimeMillis = System.currentTimeMillis();

    // ==================== Construction ====================

    private ProxyMetrics() {
        this.registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        this.compositeRegistry = registry;

        // Register JVM metrics
        registerJvmMetrics();

        // Initialize connection counters
        this.connectionsAccepted = Counter.builder("proxy_connections_accepted_total")
            .description("Total number of client connections accepted")
            .register(registry);

        this.connectionsRejected = Counter.builder("proxy_connections_rejected_total")
            .description("Total number of client connections rejected")
            .register(registry);

        this.connectionsClosed = Counter.builder("proxy_connections_closed_total")
            .description("Total number of connections closed")
            .register(registry);

        // Initialize packet counters
        this.packetsFromClient = Counter.builder("proxy_packets_total")
            .tag("direction", "from_client")
            .description("Total packets received from clients")
            .register(registry);

        this.packetsToClient = Counter.builder("proxy_packets_total")
            .tag("direction", "to_client")
            .description("Total packets sent to clients")
            .register(registry);

        this.packetsFromBackend = Counter.builder("proxy_packets_total")
            .tag("direction", "from_backend")
            .description("Total packets received from backends")
            .register(registry);

        this.packetsToBackend = Counter.builder("proxy_packets_total")
            .tag("direction", "to_backend")
            .description("Total packets sent to backends")
            .register(registry);

        // Initialize byte counters
        this.bytesFromClient = Counter.builder("proxy_bytes_total")
            .tag("direction", "from_client")
            .description("Total bytes received from clients")
            .baseUnit("bytes")
            .register(registry);

        this.bytesToClient = Counter.builder("proxy_bytes_total")
            .tag("direction", "to_client")
            .description("Total bytes sent to clients")
            .baseUnit("bytes")
            .register(registry);

        this.bytesFromBackend = Counter.builder("proxy_bytes_total")
            .tag("direction", "from_backend")
            .description("Total bytes received from backends")
            .baseUnit("bytes")
            .register(registry);

        this.bytesToBackend = Counter.builder("proxy_bytes_total")
            .tag("direction", "to_backend")
            .description("Total bytes sent to backends")
            .baseUnit("bytes")
            .register(registry);

        // Initialize error counters
        this.packetDecodeErrors = Counter.builder("proxy_errors_total")
            .tag("type", "packet_decode")
            .description("Total packet decode errors")
            .register(registry);

        this.packetEncodeErrors = Counter.builder("proxy_errors_total")
            .tag("type", "packet_encode")
            .description("Total packet encode errors")
            .register(registry);

        this.authenticationFailures = Counter.builder("proxy_errors_total")
            .tag("type", "authentication")
            .description("Total authentication failures")
            .register(registry);

        this.backendConnectionFailures = Counter.builder("proxy_errors_total")
            .tag("type", "backend_connection")
            .description("Total backend connection failures")
            .register(registry);

        // Initialize transfer counters
        this.serverTransfersInitiated = Counter.builder("proxy_transfers_total")
            .tag("status", "initiated")
            .description("Total server transfers initiated")
            .register(registry);

        this.serverTransfersCompleted = Counter.builder("proxy_transfers_total")
            .tag("status", "completed")
            .description("Total server transfers completed")
            .register(registry);

        this.serverTransfersFailed = Counter.builder("proxy_transfers_total")
            .tag("status", "failed")
            .description("Total server transfers failed")
            .register(registry);

        // Initialize gauges
        Gauge.builder("proxy_sessions_active", activeSessionsGauge, AtomicLong::get)
            .description("Number of currently active sessions")
            .register(registry);

        Gauge.builder("proxy_backend_connections_pending", pendingBackendConnectionsGauge, AtomicLong::get)
            .description("Number of pending backend connections")
            .register(registry);

        // Initialize timers
        this.packetProcessingTimer = Timer.builder("proxy_packet_processing_duration")
            .description("Time spent processing packets")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);

        this.backendConnectTimer = Timer.builder("proxy_backend_connect_duration")
            .description("Time to establish backend connections")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);

        this.authenticationTimer = Timer.builder("proxy_authentication_duration")
            .description("Time spent on authentication")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);

        this.serverTransferTimer = Timer.builder("proxy_server_transfer_duration")
            .description("Time to complete server transfers")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);

        // Initialize distribution summaries for packet sizes
        this.packetSizeFromClient = DistributionSummary.builder("proxy_packet_size_bytes")
            .tag("direction", "from_client")
            .description("Size of packets from clients")
            .baseUnit("bytes")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        this.packetSizeToClient = DistributionSummary.builder("proxy_packet_size_bytes")
            .tag("direction", "to_client")
            .description("Size of packets to clients")
            .baseUnit("bytes")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        this.packetSizeFromBackend = DistributionSummary.builder("proxy_packet_size_bytes")
            .tag("direction", "from_backend")
            .description("Size of packets from backends")
            .baseUnit("bytes")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        this.packetSizeToBackend = DistributionSummary.builder("proxy_packet_size_bytes")
            .tag("direction", "to_backend")
            .description("Size of packets to backends")
            .baseUnit("bytes")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        // Backend response timer
        this.backendResponseTimer = Timer.builder("proxy_backend_response_duration")
            .description("Time for backend to respond to requests")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);

        // Throughput gauges
        Gauge.builder("proxy_throughput_packets_per_sec", () -> packetsPerSecIn)
            .tag("direction", "in")
            .description("Packets received per second")
            .register(registry);

        Gauge.builder("proxy_throughput_packets_per_sec", () -> packetsPerSecOut)
            .tag("direction", "out")
            .description("Packets sent per second")
            .register(registry);

        Gauge.builder("proxy_throughput_bytes_per_sec", () -> bytesPerSecIn)
            .tag("direction", "in")
            .description("Bytes received per second")
            .baseUnit("bytes")
            .register(registry);

        Gauge.builder("proxy_throughput_bytes_per_sec", () -> bytesPerSecOut)
            .tag("direction", "out")
            .description("Bytes sent per second")
            .baseUnit("bytes")
            .register(registry);

        // Hanging requests gauge
        Gauge.builder("proxy_hanging_requests", hangingRequestsCount, AtomicLong::get)
            .description("Number of requests that have been pending for too long")
            .register(registry);

        // Uptime gauge
        Gauge.builder("proxy_uptime_seconds", () -> (System.currentTimeMillis() - startTimeMillis) / 1000.0)
            .description("Proxy uptime in seconds")
            .register(registry);

        LOGGER.info("Proxy metrics initialized");
    }

    private void registerJvmMetrics() {
        // Memory metrics
        new JvmMemoryMetrics().bindTo(registry);

        // GC metrics
        new JvmGcMetrics().bindTo(registry);

        // Thread metrics
        new JvmThreadMetrics().bindTo(registry);

        // Class loader metrics
        new ClassLoaderMetrics().bindTo(registry);

        // Processor metrics (CPU)
        new ProcessorMetrics().bindTo(registry);

        // Uptime
        new UptimeMetrics().bindTo(registry);

        // JVM info
        new JvmInfoMetrics().bindTo(registry);
    }

    // ==================== Singleton Access ====================

    @Nonnull
    public static ProxyMetrics getInstance() {
        if (instance == null) {
            synchronized (ProxyMetrics.class) {
                if (instance == null) {
                    instance = new ProxyMetrics();
                }
            }
        }
        return instance;
    }

    /**
     * Gets the Prometheus registry for scraping.
     */
    @Nonnull
    public PrometheusMeterRegistry getRegistry() {
        return registry;
    }

    /**
     * Returns all metrics in Prometheus text format.
     */
    @Nonnull
    public String scrape() {
        return registry.scrape();
    }

    // ==================== Session Metrics ====================

    /**
     * Binds session count to the session manager.
     */
    public void bindSessionManager(@Nonnull SessionManager sessionManager) {
        Objects.requireNonNull(sessionManager, "sessionManager");
        Gauge.builder("proxy_sessions_count", sessionManager, SessionManager::getSessionCount)
            .description("Current number of registered sessions")
            .register(registry);
    }

    public void incrementActiveSession() {
        activeSessionsGauge.incrementAndGet();
    }

    public void decrementActiveSession() {
        activeSessionsGauge.decrementAndGet();
    }

    public void setActiveSessions(long count) {
        activeSessionsGauge.set(count);
    }

    // ==================== Connection Metrics ====================

    public void recordConnectionAccepted() {
        connectionsAccepted.increment();
    }

    public void recordConnectionRejected() {
        connectionsRejected.increment();
    }

    public void recordConnectionClosed() {
        connectionsClosed.increment();
    }

    // ==================== Packet Metrics ====================

    /**
     * Records a packet received from a client.
     *
     * @param packetType simple name of the packet class
     * @param bytes      size in bytes
     */
    public void recordPacketFromClient(@Nonnull String packetType, long bytes) {
        packetsFromClient.increment();
        bytesFromClient.increment(bytes);
        packetSizeFromClient.record(bytes);
        packetsPerSecondClient.increment();
        bytesPerSecondIn.add(bytes);
        getPacketTypeCounter(packetType, "from_client").increment();
    }

    /**
     * Records a packet sent to a client.
     *
     * @param packetType simple name of the packet class
     * @param bytes      size in bytes
     */
    public void recordPacketToClient(@Nonnull String packetType, long bytes) {
        packetsToClient.increment();
        bytesToClient.increment(bytes);
        packetSizeToClient.record(bytes);
        bytesPerSecondOut.add(bytes);
        getPacketTypeCounter(packetType, "to_client").increment();
    }

    /**
     * Records a packet received from a backend server.
     *
     * @param packetType simple name of the packet class
     * @param bytes      size in bytes
     */
    public void recordPacketFromBackend(@Nonnull String packetType, long bytes) {
        packetsFromBackend.increment();
        bytesFromBackend.increment(bytes);
        packetSizeFromBackend.record(bytes);
        packetsPerSecondBackend.increment();
        bytesPerSecondIn.add(bytes);
        getPacketTypeCounter(packetType, "from_backend").increment();
    }

    /**
     * Records a packet sent to a backend server.
     *
     * @param packetType simple name of the packet class
     * @param bytes      size in bytes
     */
    public void recordPacketToBackend(@Nonnull String packetType, long bytes) {
        packetsToBackend.increment();
        bytesToBackend.increment(bytes);
        packetSizeToBackend.record(bytes);
        bytesPerSecondOut.add(bytes);
        getPacketTypeCounter(packetType, "to_backend").increment();
    }

    private Counter getPacketTypeCounter(String packetType, String direction) {
        String key = direction + "_" + packetType;
        return packetTypeCounters.computeIfAbsent(key, k ->
            Counter.builder("proxy_packets_by_type_total")
                .tag("type", packetType)
                .tag("direction", direction)
                .description("Packets by type and direction")
                .register(registry)
        );
    }

    // ==================== Error Metrics ====================

    public void recordPacketDecodeError() {
        packetDecodeErrors.increment();
    }

    public void recordPacketEncodeError() {
        packetEncodeErrors.increment();
    }

    public void recordAuthenticationFailure() {
        authenticationFailures.increment();
    }

    public void recordBackendConnectionFailure(@Nonnull String backendName) {
        backendConnectionFailures.increment();
        getBackendCounter(backendName, "failures").increment();
    }

    // ==================== Backend Metrics ====================

    public void recordBackendConnection(@Nonnull String backendName) {
        getBackendCounter(backendName, "connections").increment();
        getBackendActiveConnections(backendName).incrementAndGet();
    }

    public void recordBackendDisconnection(@Nonnull String backendName) {
        getBackendActiveConnections(backendName).decrementAndGet();
    }

    private Counter getBackendCounter(String backendName, String metric) {
        String key = backendName + "_" + metric;
        return backendConnectionCounters.computeIfAbsent(key, k ->
            Counter.builder("proxy_backend_" + metric + "_total")
                .tag("backend", backendName)
                .description("Backend " + metric + " count")
                .register(registry)
        );
    }

    private AtomicLong getBackendActiveConnections(String backendName) {
        AtomicLong gauge = backendActiveConnections.computeIfAbsent(backendName, k -> {
            AtomicLong g = new AtomicLong(0);
            Gauge.builder("proxy_backend_active_connections", g, AtomicLong::get)
                .tag("backend", backendName)
                .description("Active connections to backend")
                .register(registry);
            return g;
        });
        return gauge;
    }

    public void incrementPendingBackendConnections() {
        pendingBackendConnectionsGauge.incrementAndGet();
    }

    public void decrementPendingBackendConnections() {
        pendingBackendConnectionsGauge.decrementAndGet();
    }

    // ==================== Transfer Metrics ====================

    public void recordTransferInitiated() {
        serverTransfersInitiated.increment();
    }

    public void recordTransferCompleted() {
        serverTransfersCompleted.increment();
    }

    public void recordTransferFailed() {
        serverTransfersFailed.increment();
    }

    // ==================== Timing ====================

    /**
     * Starts a timer sample for measuring duration.
     *
     * @return a Timer.Sample to stop later
     */
    @Nonnull
    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    /**
     * Records packet processing time.
     */
    public void recordPacketProcessingTime(@Nonnull Timer.Sample sample) {
        sample.stop(packetProcessingTimer);
    }

    /**
     * Records backend connection time.
     */
    public void recordBackendConnectTime(@Nonnull Timer.Sample sample) {
        sample.stop(backendConnectTimer);
    }

    /**
     * Records authentication time.
     */
    public void recordAuthenticationTime(@Nonnull Timer.Sample sample) {
        sample.stop(authenticationTimer);
    }

    /**
     * Records server transfer time.
     */
    public void recordServerTransferTime(@Nonnull Timer.Sample sample) {
        sample.stop(serverTransferTimer);
    }

    /**
     * Records a custom timed operation.
     *
     * @param name   metric name
     * @param sample the timer sample
     */
    public void recordCustomTimer(@Nonnull String name, @Nonnull Timer.Sample sample) {
        Timer timer = packetTypeTimers.computeIfAbsent(name, k ->
            Timer.builder("proxy_" + name + "_duration")
                .description("Duration of " + name)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
        );
        sample.stop(timer);
    }

    /**
     * Records a duration directly.
     */
    public void recordDuration(@Nonnull String operation, long nanos) {
        Timer timer = packetTypeTimers.computeIfAbsent(operation, k ->
            Timer.builder("proxy_" + operation + "_duration")
                .description("Duration of " + operation)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
        );
        timer.record(nanos, TimeUnit.NANOSECONDS);
    }

    // ==================== Custom Gauges ====================

    /**
     * Registers a custom gauge with a value supplier.
     *
     * @param name        metric name
     * @param description metric description
     * @param supplier    value supplier
     */
    public void registerGauge(@Nonnull String name, @Nonnull String description,
                              @Nonnull Supplier<Number> supplier) {
        Gauge.builder("proxy_" + name, supplier)
            .description(description)
            .register(registry);
    }

    // ==================== Throughput Calculation ====================

    /**
     * Updates throughput calculations. Should be called periodically (e.g., every second).
     */
    public void updateThroughput() {
        long now = System.currentTimeMillis();
        long lastUpdate = lastThroughputUpdate.getAndSet(now);
        double elapsedSec = (now - lastUpdate) / 1000.0;

        if (elapsedSec <= 0) return;

        long currentPacketsIn = (long) packetsFromClient.count();
        long currentPacketsOut = (long) packetsToClient.count();
        long currentBytesIn = (long) bytesFromClient.count();
        long currentBytesOut = (long) bytesToClient.count();

        packetsPerSecIn = (currentPacketsIn - lastPacketsFromClient.getAndSet(currentPacketsIn)) / elapsedSec;
        packetsPerSecOut = (currentPacketsOut - lastPacketsToClient.getAndSet(currentPacketsOut)) / elapsedSec;
        bytesPerSecIn = (currentBytesIn - lastBytesIn.getAndSet(currentBytesIn)) / elapsedSec;
        bytesPerSecOut = (currentBytesOut - lastBytesOut.getAndSet(currentBytesOut)) / elapsedSec;
    }

    /**
     * Gets the current packets per second (inbound).
     */
    public double getPacketsPerSecIn() {
        return packetsPerSecIn;
    }

    /**
     * Gets the current packets per second (outbound).
     */
    public double getPacketsPerSecOut() {
        return packetsPerSecOut;
    }

    /**
     * Gets the current bytes per second (inbound).
     */
    public double getBytesPerSecIn() {
        return bytesPerSecIn;
    }

    /**
     * Gets the current bytes per second (outbound).
     */
    public double getBytesPerSecOut() {
        return bytesPerSecOut;
    }

    // ==================== Request Tracking (Hanging Detection) ====================

    /**
     * Starts tracking a request for hanging detection.
     *
     * @param requestId unique identifier for the request
     */
    public void startRequestTracking(long requestId) {
        pendingRequests.put(requestId, System.currentTimeMillis());
    }

    /**
     * Completes tracking a request and records response time.
     *
     * @param requestId the request identifier
     */
    public void completeRequestTracking(long requestId) {
        Long startTime = pendingRequests.remove(requestId);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            backendResponseTimer.record(duration, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Checks for hanging requests and updates the counter.
     * Should be called periodically.
     */
    public void checkHangingRequests() {
        long now = System.currentTimeMillis();
        long hangingCount = pendingRequests.values().stream()
            .filter(startTime -> (now - startTime) > HANGING_THRESHOLD_MS)
            .count();
        hangingRequestsCount.set(hangingCount);
    }

    /**
     * Gets the number of currently hanging requests.
     */
    public long getHangingRequestsCount() {
        return hangingRequestsCount.get();
    }

    /**
     * Gets the average backend response time in milliseconds.
     */
    public double getAverageResponseTimeMs() {
        return backendResponseTimer.mean(TimeUnit.MILLISECONDS);
    }

    /**
     * Gets the 95th percentile backend response time in milliseconds.
     */
    public double getP95ResponseTimeMs() {
        HistogramSnapshot snapshot = backendResponseTimer.takeSnapshot();
        return snapshot.percentileValues().length > 0
            ? snapshot.percentileValues()[1].value(TimeUnit.MILLISECONDS) // 95th percentile
            : 0;
    }

    /**
     * Gets the proxy uptime in seconds.
     */
    public long getUptimeSeconds() {
        return (System.currentTimeMillis() - startTimeMillis) / 1000;
    }

    // ==================== Snapshot ====================

    /**
     * Creates a snapshot of current metrics for logging/debugging.
     */
    @Nonnull
    public MetricsSnapshot createSnapshot() {
        return new MetricsSnapshot(
            activeSessionsGauge.get(),
            (long) packetsFromClient.count(),
            (long) packetsToClient.count(),
            (long) packetsFromBackend.count(),
            (long) packetsToBackend.count(),
            (long) bytesFromClient.count(),
            (long) bytesToClient.count(),
            (long) connectionsAccepted.count(),
            (long) connectionsClosed.count(),
            (long) authenticationFailures.count(),
            (long) backendConnectionFailures.count(),
            packetsPerSecIn,
            packetsPerSecOut,
            bytesPerSecIn,
            bytesPerSecOut,
            getAverageResponseTimeMs(),
            hangingRequestsCount.get(),
            getUptimeSeconds()
        );
    }

    /**
     * Creates a detailed report suitable for sharing with developers.
     */
    @Nonnull
    public String createShareableReport() {
        MetricsSnapshot snapshot = createSnapshot();
        Runtime runtime = Runtime.getRuntime();

        long maxMem = runtime.maxMemory();
        long usedMem = runtime.totalMemory() - runtime.freeMemory();

        return """
            ================================================================================
                               NUMDRASSL PROXY METRICS REPORT
            ================================================================================
            Generated: %s
            Uptime: %s
            
            --- SESSIONS & CONNECTIONS ---
            Active Sessions:      %d
            Connections Accepted: %d
            Connections Closed:   %d
            
            --- PACKET THROUGHPUT ---
            Total Packets In:     %,d
            Total Packets Out:    %,d
            Current Rate In:      %.1f packets/sec
            Current Rate Out:     %.1f packets/sec
            
            --- NETWORK I/O ---
            Total Bytes In:       %s
            Total Bytes Out:      %s
            Current Rate In:      %s/sec
            Current Rate Out:     %s/sec
            
            --- RESPONSE TIMES ---
            Average Response:     %.2f ms
            Hanging Requests:     %d (>30s pending)
            
            --- MEMORY ---
            Heap Used:            %s
            Heap Max:             %s
            Usage:                %.1f%%
            
            --- ERRORS ---
            Auth Failures:        %d
            Backend Failures:     %d
            
            --- SYSTEM ---
            Java Version:         %s
            Available CPUs:       %d
            OS:                   %s
            
            ================================================================================
            Share this report at: https://github.com/Numdrassl/proxy/issues
            ================================================================================
            """.formatted(
            java.time.LocalDateTime.now(),
            formatDuration(snapshot.uptimeSeconds()),
            snapshot.activeSessions(),
            snapshot.connectionsAccepted(),
            snapshot.connectionsClosed(),
            snapshot.packetsFromClient(),
            snapshot.packetsToClient(),
            snapshot.packetsPerSecIn(),
            snapshot.packetsPerSecOut(),
            formatBytes(snapshot.bytesFromClient()),
            formatBytes(snapshot.bytesToClient()),
            formatBytesRate(snapshot.bytesPerSecIn()),
            formatBytesRate(snapshot.bytesPerSecOut()),
            snapshot.avgResponseTimeMs(),
            snapshot.hangingRequests(),
            formatBytes(usedMem),
            formatBytes(maxMem),
            (double) usedMem / maxMem * 100,
            snapshot.authFailures(),
            snapshot.backendFailures(),
            System.getProperty("java.version"),
            runtime.availableProcessors(),
            System.getProperty("os.name") + " " + System.getProperty("os.version")
        );
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

    /**
     * Immutable snapshot of key metrics including throughput and response times.
     */
    public record MetricsSnapshot(
        long activeSessions,
        long packetsFromClient,
        long packetsToClient,
        long packetsFromBackend,
        long packetsToBackend,
        long bytesFromClient,
        long bytesToClient,
        long connectionsAccepted,
        long connectionsClosed,
        long authFailures,
        long backendFailures,
        double packetsPerSecIn,
        double packetsPerSecOut,
        double bytesPerSecIn,
        double bytesPerSecOut,
        double avgResponseTimeMs,
        long hangingRequests,
        long uptimeSeconds
    ) {
        @Override
        public String toString() {
            return String.format(
                "Sessions: %d | Packets: %.0f/s in, %.0f/s out | " +
                "Bytes: %s/s in, %s/s out | Avg Response: %.1fms | Hanging: %d",
                activeSessions,
                packetsPerSecIn, packetsPerSecOut,
                formatBytesShort(bytesPerSecIn), formatBytesShort(bytesPerSecOut),
                avgResponseTimeMs, hangingRequests
            );
        }

        private static String formatBytesShort(double bytes) {
            if (bytes < 1024) return String.format("%.0fB", bytes);
            if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.1fMB", bytes / (1024.0 * 1024));
            return String.format("%.2fGB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
