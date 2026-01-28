package me.internalizable.numdrassl.config;

import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for the HAProxy PROXY protocol support.
 *
 * <p>The PROXY protocol allows DDoS protection services and load balancers to preserve
 * the original client IP address when proxying connections.</p>
 *
 * <h2>Security</h2>
 * <p><b>WARNING:</b> Only enable proxy protocol if you're using a DDoS protection
 * service that sends PROXY protocol headers. If enabled without trusted proxies,
 * malicious clients could spoof their IP addresses.</p>
 *
 * @see <a href="https://www.haproxy.org/download/2.0/doc/proxy-protocol.txt">PROXY Protocol Specification</a>
 */
public class ProxyProtocolConfig {

    /**
     * Whether to enable PROXY protocol support.
     * When enabled, the proxy expects PROXY protocol headers from incoming connections.
     */
    private boolean enabled = false;

    /**
     * List of trusted proxy IP addresses that are allowed to send PROXY protocol headers.
     * If empty when proxy protocol is enabled, ALL sources are trusted (dangerous!).
     */
    private Set<String> trustedProxies = new HashSet<>();

    /**
     * Whether to require PROXY protocol for all connections.
     * If true, connections without PROXY protocol headers will be rejected.
     * If false, connections will fall back to using the direct connection address.
     */
    private boolean required = true;

    /**
     * Timeout in seconds to wait for PROXY protocol header.
     * Connection will be closed if header is not received within this time.
     */
    private int headerTimeoutSeconds = 5;

    // ==================== Constructors ====================

    public ProxyProtocolConfig() {
    }

    public ProxyProtocolConfig(boolean enabled, Set<String> trustedProxies) {
        this.enabled = enabled;
        this.trustedProxies = trustedProxies != null ? new HashSet<>(trustedProxies) : new HashSet<>();
    }

    // ==================== Getters/Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(Set<String> trustedProxies) {
        this.trustedProxies = trustedProxies != null ? new HashSet<>(trustedProxies) : new HashSet<>();
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public int getHeaderTimeoutSeconds() {
        return headerTimeoutSeconds;
    }

    public void setHeaderTimeoutSeconds(int headerTimeoutSeconds) {
        this.headerTimeoutSeconds = headerTimeoutSeconds;
    }

    /**
     * Adds a trusted proxy IP address.
     */
    public void addTrustedProxy(String ip) {
        if (trustedProxies == null) {
            trustedProxies = new HashSet<>();
        }
        trustedProxies.add(ip);
    }

    @Override
    public String toString() {
        return "ProxyProtocolConfig{" +
                "enabled=" + enabled +
                ", trustedProxies=" + trustedProxies +
                ", required=" + required +
                ", headerTimeoutSeconds=" + headerTimeoutSeconds +
                '}';
    }
}

