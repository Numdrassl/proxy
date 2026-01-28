package me.internalizable.numdrassl.config;

/**
 * Represents a backend Hytale server configuration
 */
public class BackendServer {

    private String name;
    private String host;
    private int port;
    private boolean defaultServer;
    private String hostname; // Optional: hostname/SNI to route to this backend

    public BackendServer() {
    }

    public BackendServer(String name, String host, int port, boolean defaultServer) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.defaultServer = defaultServer;
    }

    public BackendServer(String name, String host, int port, boolean defaultServer, String hostname) {
        this(name, host, port, defaultServer);
        this.hostname = hostname;
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

    /**
     * Gets the hostname/SNI that routes to this backend.
     * If set, connections with this hostname will be routed to this backend.
     *
     * @return the hostname, or null if not set
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * Sets the hostname/SNI that routes to this backend.
     *
     * @param hostname the hostname (e.g., "play.example.com"), or null to disable host-based routing
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    /**
     * Checks if this backend has hostname-based routing configured.
     *
     * @return true if hostname is set and not empty
     */
    public boolean hasHostname() {
        return hostname != null && !hostname.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("BackendServer{");
        sb.append("name='").append(name).append('\'');
        sb.append(", host='").append(host).append('\'');
        sb.append(", port=").append(port);
        sb.append(", default=").append(defaultServer);
        if (hostname != null) {
            sb.append(", hostname='").append(hostname).append('\'');
        }
        sb.append('}');
        return sb.toString();
    }
}

