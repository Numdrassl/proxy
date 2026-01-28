package me.internalizable.numdrassl.config;

/**
 * Represents a backend Hytale server configuration
 */
public class BackendServer {

    private String name;
    private String host;
    private int port;
    private boolean defaultServer;
    private String fallbackServer;

    public BackendServer() {
    }

    public BackendServer(String name, String host, int port, boolean defaultServer, String fallbackServer) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.defaultServer = defaultServer;
        this.fallbackServer = fallbackServer;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isDefaultServer() {
        return defaultServer;
    }

    public void setDefaultServer(boolean defaultServer) {
        this.defaultServer = defaultServer;
    }

    public void setFallbackServer(String fallbackServer) {
        this.fallbackServer = fallbackServer;
    }

    public String getFallbackServer() {
        return fallbackServer;
    }

    @Override
    public String toString() {
        return "BackendServer{" +
            "name='" + name + '\'' +
            ", host='" + host + '\'' +
            ", port=" + port +
            ", default=" + defaultServer +
            ", fallbackServer=" + fallbackServer +
            '}';
    }
}

