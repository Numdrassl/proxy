package me.internalizable.numdrassl.cluster;

import me.internalizable.numdrassl.api.cluster.ClusterManager;
import me.internalizable.numdrassl.api.cluster.ProxyInfo;
import me.internalizable.numdrassl.api.messaging.MessagingService;
import me.internalizable.numdrassl.config.ProxyConfig;
import me.internalizable.numdrassl.event.api.NumdrasslEventManager;
import me.internalizable.numdrassl.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link ClusterManager} for managing proxy instances.
 *
 * <p>In clustered mode, uses Redis messaging to track all online proxies.
 * In standalone mode, only tracks the local proxy.</p>
 */
public final class NumdrasslClusterManager implements ClusterManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NumdrasslClusterManager.class);
    private static final String VERSION = "1.0.0";

    private final String proxyId;
    private final String region;
    private final InetSocketAddress publicAddress;
    private final SessionManager sessionManager;
    private final boolean clusterMode;

    private ProxyRegistry registry;
    private HeartbeatPublisher heartbeatPublisher;

    public NumdrasslClusterManager(
            @Nonnull ProxyConfig config,
            @Nonnull SessionManager sessionManager) {
        this.proxyId = config.getProxyId() != null ? config.getProxyId() : generateProxyId();
        this.region = config.getProxyRegion();
        this.publicAddress = resolvePublicAddress(config);
        this.sessionManager = sessionManager;
        this.clusterMode = config.isClusterEnabled();

        LOGGER.info("Cluster manager initialized: id={}, region={}, clusterMode={}",
                proxyId, region, clusterMode);
    }

    /**
     * Initialize cluster services with the messaging service.
     *
     * @param messagingService the messaging service to use
     * @param eventManager the event manager for firing cluster events
     */
    public void initialize(
            @Nonnull MessagingService messagingService,
            @Nonnull NumdrasslEventManager eventManager) {
        if (!clusterMode) {
            LOGGER.info("Cluster mode disabled, skipping cluster initialization");
            return;
        }

        // Create registry to track other proxies
        this.registry = new ProxyRegistry(messagingService, eventManager, proxyId, VERSION);
        registry.start();

        // Create heartbeat publisher
        this.heartbeatPublisher = new HeartbeatPublisher(
                messagingService,
                proxyId,
                region,
                publicAddress,
                sessionManager::getSessionCount
        );
        heartbeatPublisher.start();

        LOGGER.info("Cluster services initialized");
    }

    /**
     * Shutdown cluster services.
     */
    public void shutdown() {
        if (heartbeatPublisher != null) {
            heartbeatPublisher.stop();
        }
        if (registry != null) {
            registry.stop();
        }
    }

    @Override
    public boolean isClusterMode() {
        return clusterMode && registry != null;
    }

    @Override
    @Nonnull
    public String getLocalProxyId() {
        return proxyId;
    }

    @Override
    @Nonnull
    public String getLocalRegion() {
        return region;
    }

    @Override
    @Nonnull
    public ProxyInfo getLocalProxyInfo() {
        return new ProxyInfo(
                proxyId,
                region,
                publicAddress,
                sessionManager.getSessionCount(),
                1000, // Max players - could be configurable
                System.currentTimeMillis(),
                Instant.now(),
                VERSION
        );
    }

    @Override
    @Nonnull
    public Collection<ProxyInfo> getOnlineProxies() {
        if (!isClusterMode()) {
            return Collections.singleton(getLocalProxyInfo());
        }
        return registry.getOnlineProxies();
    }

    @Override
    @Nonnull
    public Optional<ProxyInfo> getProxy(@Nonnull String proxyId) {
        if (this.proxyId.equals(proxyId)) {
            return Optional.of(getLocalProxyInfo());
        }
        if (!isClusterMode()) {
            return Optional.empty();
        }
        return registry.getProxy(proxyId);
    }

    @Override
    @Nonnull
    public Collection<ProxyInfo> getProxiesInRegion(@Nonnull String region) {
        return getOnlineProxies().stream()
                .filter(p -> p.region().equalsIgnoreCase(region))
                .toList();
    }

    @Override
    public int getGlobalPlayerCount() {
        if (!isClusterMode()) {
            return sessionManager.getSessionCount();
        }
        return registry.getGlobalPlayerCount();
    }

    @Override
    public int getProxyCount() {
        if (!isClusterMode()) {
            return 1;
        }
        return registry.getProxyCount();
    }

    @Override
    @Nonnull
    public Optional<String> findPlayerProxy(@Nonnull UUID playerUuid) {
        // First check local
        if (sessionManager.findByUuid(playerUuid).isPresent()) {
            return Optional.of(proxyId);
        }

        // TODO: In a full implementation, we'd need to track player locations
        // via Redis or another shared store. For now, return empty.
        return Optional.empty();
    }

    @Override
    public boolean isPlayerOnline(@Nonnull UUID playerUuid) {
        return findPlayerProxy(playerUuid).isPresent();
    }

    @Override
    @Nonnull
    public Optional<ProxyInfo> getLeastLoadedProxy(@Nonnull String region) {
        return getProxiesInRegion(region).stream()
                .filter(ProxyInfo::hasCapacity)
                .min(Comparator.comparingDouble(ProxyInfo::loadFactor));
    }

    @Override
    @Nonnull
    public Optional<ProxyInfo> getLeastLoadedProxy() {
        return getOnlineProxies().stream()
                .filter(ProxyInfo::hasCapacity)
                .min(Comparator.comparingDouble(ProxyInfo::loadFactor));
    }

    // ==================== Private Helpers ====================

    private static String generateProxyId() {
        return "proxy-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static InetSocketAddress resolvePublicAddress(ProxyConfig config) {
        String host = config.getPublicAddress() != null
                ? config.getPublicAddress()
                : config.getBindAddress();

        int port = config.getPublicPort() > 0
                ? config.getPublicPort()
                : config.getBindPort();

        return new InetSocketAddress(host, port);
    }
}

