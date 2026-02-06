package me.internalizable.numdrassl.server.health;

import me.internalizable.numdrassl.config.BackendServer;
import me.internalizable.numdrassl.server.BackendConnector;
import me.internalizable.numdrassl.server.ProxyCore;
import me.internalizable.numdrassl.session.ProxySession;
import me.internalizable.numdrassl.session.SessionManager;
import me.internalizable.numdrassl.session.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.*;

public class BackendWatchdog {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackendWatchdog.class);

    private final ProxyCore proxyCore;
    
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "backend-watchdog");
        t.setDaemon(true);
        return t;
    });

    private ScheduledFuture<?> task;

    public BackendWatchdog(ProxyCore proxyCore) {
        this.proxyCore = proxyCore;
    }

    public void initialize() {
        if (task != null) return;

        task = scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);

        LOGGER.info("Watchdog initialized");
    }

    public void shutdown() {
        if (task != null) {
            task.cancel(true);
            task = null;
        }
        scheduler.shutdownNow();

        LOGGER.info("Watchdog shut down");
    }

    private void tick() {
        long now = System.currentTimeMillis();

        if (proxyCore.getBackendHealthManager() == null) return;

        for (Map.Entry<String, BackendHealth> entry : proxyCore.getBackendHealthManager().getAll().entrySet()) {
            String backendName = entry.getKey();
            BackendHealth health = entry.getValue();

            handleBackendHealth(now, backendName, health);
        }
    }

    public List<ProxySession> getSessionsForBackend(BackendServer backend) {
        List<ProxySession> result = new ArrayList<>();

        for (ProxySession session : proxyCore.getSessionManager().getAllSessions()) {
            if (backend.equals(session.getCurrentBackend())) {
                if (session.isServerTransfer() || session.getState() != SessionState.CONNECTED || !session.isBackendAvailable()) continue;
                result.add(session);
            }
        }

        return result;
    }

    private void handleBackendHealth(long now, String backendName, @Nonnull BackendHealth health) {
        if (proxyCore.getBackendHealthManager() == null) return;

        BackendServer backend = proxyCore.getConfig().getBackendByName(backendName);
        if (backend == null) {
            return;
        }

        if (health.isInConnectGrace(now, 10_000)) {
            return;
        }

        List<ProxySession> sessions = getSessionsForBackend(backend);
        if (sessions.isEmpty()) {
            return;
        }

        long passiveTimeoutMs = 5000;
        long pingGraceMs = 2000;

        if (health.shouldPing(now, passiveTimeoutMs)) {
            LOGGER.debug("[Watchdog] Sending health check to backend {}", backend.getName());
            health.markPingSent();
            proxyCore.getBackendHealthManager().sendPingAsync(backend, 1500)
                    .thenAccept(ok -> {
                        if (!ok) {
                            health.markPingFailed();
                        }
                    });
        }

        if (health.getState() == BackendHealth.BackendState.HEALTHY && health.isDead(now, passiveTimeoutMs, pingGraceMs)) {
            health.markDead();
            handleBackendDownAsync(backend, sessions);
        }
    }

    private void handleBackendDownAsync(BackendServer backend, List<ProxySession> sessions) {
        LOGGER.warn("[Watchdog] backend {} is DEAD", backend.getName());
        CompletableFuture.runAsync(() -> {

            LOGGER.warn("[Watchdog] backend={} DOWN -> transferring {} session(s)", backend.getName(), sessions.size());

            for (ProxySession session : sessions) {
                if (!session.isActive()) {
                    continue;
                }

                if (session.isServerTransfer()) {
                    continue;
                }

                BackendServer current = session.getCurrentBackend();
                if (current == null || !current.equals(backend)) {
                    continue;
                }

                session.markBackendDown();

                LOGGER.debug("[Watchdog] Handling backend disconnect for {} from {}", session.getSessionId(), backend.getName());

                proxyCore.getBackendConnector().handleBackendDisconnect(session, backend);
            }
        }, scheduler);
    }


}
