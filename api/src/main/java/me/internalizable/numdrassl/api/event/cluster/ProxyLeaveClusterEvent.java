package me.internalizable.numdrassl.api.event.cluster;

import me.internalizable.numdrassl.api.cluster.ProxyInfo;

import javax.annotation.Nonnull;

/**
 * Event fired when a proxy instance leaves the cluster.
 *
 * <p>This event is fired when:</p>
 * <ul>
 *   <li>A proxy gracefully shuts down (sends shutdown heartbeat)</li>
 *   <li>A proxy's heartbeat times out (assumed crashed)</li>
 * </ul>
 */
public final class ProxyLeaveClusterEvent {

    private final ProxyInfo proxyInfo;
    private final LeaveReason reason;

    public ProxyLeaveClusterEvent(@Nonnull ProxyInfo proxyInfo, @Nonnull LeaveReason reason) {
        this.proxyInfo = proxyInfo;
        this.reason = reason;
    }

    /**
     * Get information about the proxy that left.
     *
     * @return the departed proxy's last known info
     */
    @Nonnull
    public ProxyInfo getProxyInfo() {
        return proxyInfo;
    }

    /**
     * Get the ID of the proxy that left.
     *
     * @return the proxy ID
     */
    @Nonnull
    public String getProxyId() {
        return proxyInfo.proxyId();
    }

    /**
     * Get the reason the proxy left the cluster.
     *
     * @return the leave reason
     */
    @Nonnull
    public LeaveReason getReason() {
        return reason;
    }

    /**
     * Reasons a proxy might leave the cluster.
     */
    public enum LeaveReason {
        /** Proxy sent a graceful shutdown heartbeat */
        GRACEFUL_SHUTDOWN,
        /** Proxy stopped sending heartbeats (timeout) */
        HEARTBEAT_TIMEOUT,
        /** Connection to messaging backend lost */
        CONNECTION_LOST
    }
}

