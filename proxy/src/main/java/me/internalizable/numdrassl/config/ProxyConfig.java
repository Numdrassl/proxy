package me.internalizable.numdrassl.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
            return yaml.load(is);
        }
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());

        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);
        dumperOptions.setIndent(2);
        dumperOptions.setIndicatorIndent(2);
        dumperOptions.setIndentWithIndicator(true);

        Representer representer = new Representer(dumperOptions) {
            @Override
            protected NodeTuple representJavaBeanProperty(Object javaBean, Property property,
                                                          Object propertyValue, Tag customTag) {
                if (propertyValue == null) {
                    return null;
                }
                return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
            }

            @Override
            protected Set<Property> getProperties(Class<?> type) {
                Set<Property> props = super.getProperties(type);
                if (type == ProxyConfig.class) {
                    return orderProperties(props,
                        "bindAddress", "bindPort", "publicAddress", "publicPort",
                        "certificatePath", "privateKeyPath",
                        "maxConnections", "connectionTimeoutSeconds",
                        "debugMode", "passthroughMode", "proxySecret",
                        "backends",
                        "clusterEnabled", "proxyId", "proxyRegion",
                        "redisHost", "redisPort", "redisPassword", "redisSsl", "redisDatabase"
                    );
                }
                if (type == BackendServer.class) {
                    return orderProperties(props, "name", "host", "port", "defaultServer");
                }
                return props;
            }

            private Set<Property> orderProperties(Set<Property> props, String... order) {
                Set<Property> ordered = new LinkedHashSet<>();
                for (String name : order) {
                    for (Property p : props) {
                        if (p.getName().equals(name)) {
                            ordered.add(p);
                            break;
                        }
                    }
                }
                for (Property p : props) {
                    if (!ordered.contains(p)) {
                        ordered.add(p);
                    }
                }
                return ordered;
            }
        };

        representer.addClassTag(ProxyConfig.class, Tag.MAP);
        representer.addClassTag(BackendServer.class, Tag.MAP);

        Yaml yaml = new Yaml(representer, dumperOptions);

        try (Writer writer = Files.newBufferedWriter(path)) {
            writer.write("# Numdrassl Proxy Configuration\n");
            writer.write("# https://github.com/Numdrassl/proxy\n\n");
            yaml.dump(this, writer);
        }
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

