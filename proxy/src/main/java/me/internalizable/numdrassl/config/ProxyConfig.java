package me.internalizable.numdrassl.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    private boolean debugMode = false;
    private boolean passthroughMode = false;

    // Backend authentication
    private String proxySecret = null;

    // Backend servers
    private List<BackendServer> backends = new ArrayList<>();

    // Cluster/Redis configuration
    private boolean clusterEnabled = false;
    private String proxyId = null;
    private String proxyRegion = "default";
    private String redisHost = "localhost";
    private int redisPort = 6379;
    private String redisPassword = null;
    private boolean redisSsl = false;
    private int redisDatabase = 0;

    public ProxyConfig() {
        // Default backend
        backends.add(new BackendServer("lobby", "127.0.0.1", 5520, true));
    }

    // ==================== Load / Save ====================

    public static ProxyConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            ProxyConfig config = new ProxyConfig();
            config.save(path);
            return config;
        }

        LoaderOptions options = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(ProxyConfig.class, options));
        try (InputStream is = Files.newInputStream(path)) {
            ProxyConfig config = yaml.load(is);
            // Ensure defaults for fields that might not exist in older configs
            if (config == null) {
                config = new ProxyConfig();
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
            writer.write("redisDatabase: " + redisDatabase + "\n");
        }
    }

    /**
     * Formats a value for YAML output.
     * Returns "null" for null values, or quoted string otherwise.
     */
    private String formatValue(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    // ==================== Network Getters/Setters ====================

    public String getBindAddress() { return bindAddress; }
    public void setBindAddress(String bindAddress) { this.bindAddress = bindAddress; }

    public int getBindPort() { return bindPort; }
    public void setBindPort(int bindPort) { this.bindPort = bindPort; }

    public String getPublicAddress() { return publicAddress; }
    public void setPublicAddress(String publicAddress) { this.publicAddress = publicAddress; }

    public int getPublicPort() { return publicPort; }
    public void setPublicPort(int publicPort) { this.publicPort = publicPort; }

    // ==================== TLS Getters/Setters ====================

    public String getCertificatePath() { return certificatePath; }
    public void setCertificatePath(String certificatePath) { this.certificatePath = certificatePath; }

    public String getPrivateKeyPath() { return privateKeyPath; }
    public void setPrivateKeyPath(String privateKeyPath) { this.privateKeyPath = privateKeyPath; }

    // ==================== Connection Getters/Setters ====================

    public int getMaxConnections() { return maxConnections; }
    public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }

    public int getConnectionTimeoutSeconds() { return connectionTimeoutSeconds; }
    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) { this.connectionTimeoutSeconds = connectionTimeoutSeconds; }

    // ==================== Debug Getters/Setters ====================

    public boolean isDebugMode() { return debugMode; }
    public void setDebugMode(boolean debugMode) { this.debugMode = debugMode; }

    public boolean isPassthroughMode() { return passthroughMode; }
    public void setPassthroughMode(boolean passthroughMode) { this.passthroughMode = passthroughMode; }

    // ==================== Backend Auth Getters/Setters ====================

    public String getProxySecret() { return proxySecret; }
    public void setProxySecret(String proxySecret) { this.proxySecret = proxySecret; }

    // ==================== Backend Server Getters/Setters ====================

    public List<BackendServer> getBackends() { return backends; }
    public void setBackends(List<BackendServer> backends) { this.backends = backends; }

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

    public boolean isClusterEnabled() { return clusterEnabled; }
    public void setClusterEnabled(boolean clusterEnabled) { this.clusterEnabled = clusterEnabled; }

    public String getProxyId() { return proxyId; }
    public void setProxyId(String proxyId) { this.proxyId = proxyId; }

    public String getProxyRegion() { return proxyRegion; }
    public void setProxyRegion(String proxyRegion) { this.proxyRegion = proxyRegion; }

    // ==================== Redis Getters/Setters ====================

    public String getRedisHost() { return redisHost; }
    public void setRedisHost(String redisHost) { this.redisHost = redisHost; }

    public int getRedisPort() { return redisPort; }
    public void setRedisPort(int redisPort) { this.redisPort = redisPort; }

    public String getRedisPassword() { return redisPassword; }
    public void setRedisPassword(String redisPassword) { this.redisPassword = redisPassword; }

    public boolean isRedisSsl() { return redisSsl; }
    public void setRedisSsl(boolean redisSsl) { this.redisSsl = redisSsl; }

    public int getRedisDatabase() { return redisDatabase; }
    public void setRedisDatabase(int redisDatabase) { this.redisDatabase = redisDatabase; }
}

