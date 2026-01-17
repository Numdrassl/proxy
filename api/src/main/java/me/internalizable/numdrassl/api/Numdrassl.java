package me.internalizable.numdrassl.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Static accessor for the Numdrassl API.
 *
 * <p>Use this class to obtain the {@link ProxyServer} instance from anywhere
 * in your plugin code.</p>
 *
 * <pre>{@code
 * ProxyServer proxy = Numdrassl.getProxy();
 * proxy.getEventManager().register(this, new MyListener());
 * }</pre>
 */
public final class Numdrassl {

    private static ProxyServer proxy;

    private Numdrassl() {
        // Utility class
    }

    /**
     * Get the proxy server instance.
     *
     * @return the proxy server
     * @throws IllegalStateException if the API has not been initialized
     */
    @Nonnull
    public static ProxyServer getProxy() {
        if (proxy == null) {
            throw new IllegalStateException("Numdrassl API has not been initialized. " +
                "Is the proxy running?");
        }
        return proxy;
    }

    /**
     * Check if the API has been initialized.
     *
     * @return true if the API is available
     */
    public static boolean isInitialized() {
        return proxy != null;
    }

    /**
     * Initialize the API with the proxy server instance.
     * This is called internally by the proxy and should not be called by plugins.
     *
     * @param proxyServer the proxy server instance
     */
    public static void setProxy(@Nonnull ProxyServer proxyServer) {
        if (proxy != null) {
            throw new IllegalStateException("Numdrassl API has already been initialized");
        }
        proxy = proxyServer;
    }

    /**
     * Initialize the API with the proxy server instance.
     * Alias for {@link #setProxy(ProxyServer)}.
     *
     * @param proxyServer the proxy server instance
     */
    public static void setServer(@Nonnull ProxyServer proxyServer) {
        setProxy(proxyServer);
    }
}

