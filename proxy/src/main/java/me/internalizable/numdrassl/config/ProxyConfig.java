package me.internalizable.numdrassl.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Configuration for the Numdrassl Proxy.
 */
public class ProxyConfig {

    // Network configuration
    private String bindAddress = "0.0.0.0";
    private int bindPort = 24322;
    private String publicAddress = null;
    private int publicPort = 0;

    // TLS configuration
    private String certificatePath = "certs/server.crt";
    private String privateKeyPath = "certs/server.key";

    // Connection limits
    private int maxConnections = 1000;
    private int connectionTimeoutSeconds = 30;

    // Debug options
    private Boolean debugMode = false;
    private Boolean passthroughMode = false;

    // Backend authentication
    private String proxySecret = null;

    // Backend servers
    private List<BackendServer> backends = new ArrayList<>();

    // Cluster/Redis configuration
    private Boolean clusterEnabled = false;
    private String proxyId = null;
    private String proxyRegion = "default";
    private String redisHost = "localhost";
    private int redisPort = 6379;
    private String redisPassword = null;
    private Boolean redisSsl = false;
    private int redisDatabase = 0;

    // Metrics configuration
    private boolean metricsEnabled = true;
    private int metricsPort = 9090;
    private int metricsLogIntervalSeconds = 60;
    private static SecureRandom SECRET_RANDOM = new SecureRandom();

    public ProxyConfig() {
        // Default backend
        backends.add(new BackendServer("lobby", "127.0.0.1", 5520, true));
    }

    // ==================== Load / Save ====================

    public static ProxyConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            ProxyConfig config = new ProxyConfig();
            config.applyDefaults();
            config.save(path);
            return config;
        }

        LoaderOptions options = new LoaderOptions();
        Constructor constructor = new Constructor(ProxyConfig.class, options);

        PropertyUtils propertyUtils = new PropertyUtils();
        propertyUtils.setSkipMissingProperties(true);
        constructor.setPropertyUtils(propertyUtils);

        Yaml yaml = new Yaml(constructor);

        try (InputStream is = Files.newInputStream(path)) {
            ProxyConfig config = yaml.load(is);
            // Ensure defaults for fields that might not exist in older configs
            if (config == null) {
                config = new ProxyConfig();
            }

            if (config.applyDefaults()) {
                config.save(path);
            }

            return config;
        }
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());

        try (Writer writer = Files.newBufferedWriter(path)) {
            writer.write("# Numdrassl Proxy Configuration\n");
            writer.write("# https://github.com/Numdrassl/proxy\n\n");

            // Network configuration
            writer.write("# ==================== Network Configuration ====================\n\n");
            writer.write("# Address to bind the proxy server to\n");
            writer.write("bindAddress: \"" + bindAddress + "\"\n");
            writer.write("# Port to listen on\n");
            writer.write("bindPort: " + bindPort + "\n\n");

            writer.write("# Public address for player transfers (sent in ClientReferral packets)\n");
            writer.write("# Set this to your server's public domain/IP if behind NAT\n");
            writer.write("publicAddress: " + formatValue(publicAddress) + "\n");
            writer.write("publicPort: " + publicPort + "\n\n");

            // TLS configuration
            writer.write("# ==================== TLS Configuration ====================\n\n");
            writer.write("# TLS certificates (auto-generated if missing)\n");
            writer.write("certificatePath: \"" + certificatePath + "\"\n");
            writer.write("privateKeyPath: \"" + privateKeyPath + "\"\n\n");

            // Connection limits
            writer.write("# ==================== Connection Limits ====================\n\n");
            writer.write("# Maximum concurrent connections\n");
            writer.write("maxConnections: " + maxConnections + "\n");
            writer.write("# Connection timeout in seconds\n");
            writer.write("connectionTimeoutSeconds: " + connectionTimeoutSeconds + "\n\n");

            // Debug options
            writer.write("# ==================== Debug Options ====================\n\n");
            writer.write("# Enable verbose logging for debugging\n");
            writer.write("debugMode: " + debugMode + "\n");
            writer.write("# Passthrough mode (forward packets without inspection)\n");
            writer.write("passthroughMode: " + passthroughMode + "\n\n");

            // Backend authentication
            writer.write("# ==================== Backend Authentication ====================\n\n");
            writer.write("# Shared secret for backend authentication (HMAC signing)\n");
            writer.write("# Must match the secret in your Bridge plugin config\n");
            writer.write("# If null, a random secret is generated on first run\n");
            writer.write("proxySecret: " + formatValue(proxySecret) + "\n\n");

            // Backend servers
            writer.write("# ==================== Backend Servers ====================\n\n");
            writer.write("# List of backend servers players can connect to\n");
            writer.write("backends:\n");
            for (BackendServer backend : backends) {
                writer.write("  - name: \"" + backend.getName() + "\"\n");
                writer.write("    host: \"" + backend.getHost() + "\"\n");
                writer.write("    port: " + backend.getPort() + "\n");
                writer.write("    defaultServer: " + backend.isDefaultServer() + "\n");
            }
            writer.write("\n");

            // Cluster configuration
            writer.write("# ==================== Cluster Configuration ====================\n\n");
            writer.write("# Enable cluster mode for multi-proxy deployments\n");
            writer.write("# Requires Redis for cross-proxy communication\n");
            writer.write("clusterEnabled: " + clusterEnabled + "\n\n");

            writer.write("# Unique identifier for this proxy instance (auto-generated if null)\n");
            writer.write("proxyId: " + formatValue(proxyId) + "\n");
            writer.write("# Region identifier for load balancing (e.g., \"eu-west\", \"us-east\")\n");
            writer.write("proxyRegion: \"" + proxyRegion + "\"\n\n");

            // Redis configuration
            writer.write("# ==================== Redis Configuration ====================\n\n");
            writer.write("# Redis connection settings (only used when clusterEnabled: true)\n");
            writer.write("redisHost: \"" + redisHost + "\"\n");
            writer.write("redisPort: " + redisPort + "\n");
            writer.write("# Redis password (null if no authentication required)\n");
            writer.write("redisPassword: " + formatValue(redisPassword) + "\n");
            writer.write("# Enable SSL/TLS for Redis connection\n");
            writer.write("redisSsl: " + redisSsl + "\n");
            writer.write("# Redis database index (0-15)\n");
            writer.write("redisDatabase: " + redisDatabase + "\n\n");

            // Metrics configuration
            writer.write("# ==================== Metrics Configuration ====================\n\n");
            writer.write("# Enable metrics collection and HTTP endpoint\n");
            writer.write("metricsEnabled: " + metricsEnabled + "\n");
            writer.write("# Port for metrics HTTP server (Prometheus scrape endpoint)\n");
            writer.write("metricsPort: " + metricsPort + "\n");
            writer.write("# Interval for logging metrics summary (0 to disable)\n");
            writer.write("metricsLogIntervalSeconds: " + metricsLogIntervalSeconds + "\n");
        }
    }

    private boolean applyDefaults() {
        boolean changed = false;

        if (bindAddress == null) {
            bindAddress = "0.0.0.0";
            changed = true;
        }
        if (bindPort == 0) {
            bindPort = 24322;
            changed = true;
        }

        if (certificatePath == null) {
            certificatePath = "certs/server.crt";
            changed = true;
        }
        if (privateKeyPath == null) {
            privateKeyPath = "certs/server.key";
            changed = true;
        }

        if (maxConnections <= 0) {
            maxConnections = 1000;
            changed = true;
        }
        if (connectionTimeoutSeconds <= 0) {
            connectionTimeoutSeconds = 30;
            changed = true;
        }

        if (debugMode == null) {
            debugMode = false;
            changed = true;
        }

        if (passthroughMode == null) {
            passthroughMode = false;
            changed = true;
        }

        if (proxySecret == null || proxySecret.isBlank()) {
            proxySecret = generateProxySecret();
            changed = true;
        }

        if (backends == null) backends = new ArrayList<>();
        if (backends.isEmpty()) {
            backends.add(new BackendServer("lobby", "127.0.0.1", 5520, true));
            changed = true;
        }

        if (clusterEnabled == null) {
            clusterEnabled = false;
            changed = true;
        }

        if (proxyId == null) {
            proxyId = UUID.randomUUID().toString();
            changed = true;
        }

        if (proxyRegion == null || proxyRegion.isBlank()) {
            proxyRegion = "default";
            changed = true;
        }
        if (redisHost == null || redisHost.isBlank()) {
            redisHost = "localhost";
            changed = true;
        }

        if (redisSsl == null) {
            redisSsl = false;
            changed = true;
        }

        if (redisPort == 0) {
            redisPort = 6379;
            changed = true;
        }

        return changed;
    }

    private static String generateProxySecret() {
        byte[] bytes = new byte[32]; // 256-bit

        SECRET_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Formats a value for YAML output.
     * Returns "null" for null values, or quoted string otherwise.
     */
    private String formatValue(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    // ==================== Network Getters/Setters ====================

    public String getBindAddress() {
        return bindAddress;
    }

    public void setBindAddress(String bindAddress) {
        this.bindAddress = bindAddress;
    }

    public int getBindPort() {
        return bindPort;
    }

    public void setBindPort(int bindPort) {
        this.bindPort = bindPort;
    }

    public String getPublicAddress() {
        return publicAddress;
    }

    public void setPublicAddress(String publicAddress) {
        this.publicAddress = publicAddress;
    }

    public int getPublicPort() {
        return publicPort;
    }

    public void setPublicPort(int publicPort) {
        this.publicPort = publicPort;
    }

    // ==================== TLS Getters/Setters ====================

    public String getCertificatePath() {
        return certificatePath;
    }

    public void setCertificatePath(String certificatePath) {
        this.certificatePath = certificatePath;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    // ==================== Connection Getters/Setters ====================

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }

    // ==================== Debug Getters/Setters ====================

    public Boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(Boolean debugMode) {
        this.debugMode = debugMode;
    }

    public Boolean isPassthroughMode() {
        return passthroughMode;
    }

    public void setPassthroughMode(Boolean passthroughMode) {
        this.passthroughMode = passthroughMode;
    }

    // ==================== Backend Auth Getters/Setters ====================

    public String getProxySecret() {
        return proxySecret;
    }

    public void setProxySecret(String proxySecret) {
        this.proxySecret = proxySecret;
    }

    // ==================== Backend Server Getters/Setters ====================

    public List<BackendServer> getBackends() {
        return backends;
    }

    public void setBackends(List<BackendServer> backends) {
        this.backends = backends;
    }

    public BackendServer getDefaultBackend() {
        return backends.stream()
                .filter(BackendServer::isDefaultServer)
                .findFirst()
                .orElse(backends.isEmpty() ? null : backends.get(0));
    }

    public BackendServer getBackendByName(String name) {
        return backends.stream()
                .filter(b -> b.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    // ==================== Cluster Getters/Setters ====================

    public Boolean isClusterEnabled() {
        return clusterEnabled;
    }

    public void setClusterEnabled(Boolean clusterEnabled) {
        this.clusterEnabled = clusterEnabled;
    }

    public String getProxyId() {
        return proxyId;
    }

    public void setProxyId(String proxyId) {
        this.proxyId = proxyId;
    }

    public String getProxyRegion() {
        return proxyRegion;
    }

    public void setProxyRegion(String proxyRegion) {
        this.proxyRegion = proxyRegion;
    }

    // ==================== Redis Getters/Setters ====================

    public String getRedisHost() {
        return redisHost;
    }

    public void setRedisHost(String redisHost) {
        this.redisHost = redisHost;
    }

    public int getRedisPort() {
        return redisPort;
    }

    public void setRedisPort(int redisPort) {
        this.redisPort = redisPort;
    }

    public String getRedisPassword() {
        return redisPassword;
    }

    public void setRedisPassword(String redisPassword) {
        this.redisPassword = redisPassword;
    }

    public Boolean isRedisSsl() {
        return redisSsl;
    }

    public void setRedisSsl(Boolean redisSsl) {
        this.redisSsl = redisSsl;
    }

    public int getRedisDatabase() { return redisDatabase; }
    public void setRedisDatabase(int redisDatabase) { this.redisDatabase = redisDatabase; }

    // ==================== Metrics Getters/Setters ====================

    public boolean isMetricsEnabled() { return metricsEnabled; }
    public void setMetricsEnabled(boolean metricsEnabled) { this.metricsEnabled = metricsEnabled; }

    public int getMetricsPort() { return metricsPort; }
    public void setMetricsPort(int metricsPort) { this.metricsPort = metricsPort; }

    public int getMetricsLogIntervalSeconds() { return metricsLogIntervalSeconds; }
    public void setMetricsLogIntervalSeconds(int metricsLogIntervalSeconds) { this.metricsLogIntervalSeconds = metricsLogIntervalSeconds; }

}

