package me.internalizable.numdrassl.config;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the Hytale QUIC Proxy
 */
public class ProxyConfig {

    private String bindAddress = "0.0.0.0";
    private int bindPort = 45585;
    private String publicAddress = null; // Public address for client referrals (player transfers)
    private int publicPort = 0; // Public port for client referrals (0 = use bindPort)
    private String certificatePath = "certs/server.crt";
    private String privateKeyPath = "certs/server.key";
    private int maxConnections = 1000;
    private int connectionTimeoutSeconds = 30;
    private boolean debugMode = false;
    private boolean passthroughMode = false;
    private String proxySecret = null; // Shared secret for backend authentication (if null, generates random)
    private List<BackendServer> backends = new ArrayList<>();

    public ProxyConfig() {
        // Default backend
        backends.add(new BackendServer("default", "127.0.0.1", 45585, true));
    }

    public static ProxyConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            // Create default config
            ProxyConfig config = new ProxyConfig();
            config.save(path);
            return config;
        }

        LoaderOptions options = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(ProxyConfig.class, options));
        try (InputStream is = Files.newInputStream(path)) {
            return yaml.load(is);
        }
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Yaml yaml = new Yaml();
        try (Writer writer = Files.newBufferedWriter(path)) {
            yaml.dump(this, writer);
        }
    }

    // Getters and Setters

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

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public boolean isPassthroughMode() {
        return passthroughMode;
    }

    public void setPassthroughMode(boolean passthroughMode) {
        this.passthroughMode = passthroughMode;
    }

    public String getProxySecret() {
        return proxySecret;
    }

    public void setProxySecret(String proxySecret) {
        this.proxySecret = proxySecret;
    }

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
}
