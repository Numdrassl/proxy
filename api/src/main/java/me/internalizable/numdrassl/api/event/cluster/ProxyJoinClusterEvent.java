package me.internalizable.numdrassl.api.event.cluster;

import me.internalizable.numdrassl.api.cluster.ProxyInfo;

import javax.annotation.Nonnull;

/**
 * Event fired when a proxy instance joins the cluster.
 *
 * <p>This event is fired on all proxies in the cluster when a new proxy
 * sends its first heartbeat.</p>
 */
public final class ProxyJoinClusterEvent {

    private final ProxyInfo proxyInfo;

    public ProxyJoinClusterEvent(@Nonnull ProxyInfo proxyInfo) {
        this.proxyInfo = proxyInfo;
    }

    /**
     * Get information about the proxy that joined.
     *
     * @return the new proxy's info
     */
    @Nonnull
    public ProxyInfo getProxyInfo() {
        return proxyInfo;
    }

    /**
     * Get the ID of the proxy that joined.
     *
     * @return the proxy ID
     */
    @Nonnull
    public String getProxyId() {
        return proxyInfo.proxyId();
    }

    /**
     * Get the region of the proxy that joined.
     *
     * @return the proxy's region
     */
    @Nonnull
    public String getRegion() {
        return proxyInfo.region();
    }
}

