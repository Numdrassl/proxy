package me.internalizable.numdrassl.server.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackendHealth {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackendHealth.class);

    private volatile BackendState state = BackendState.HEALTHY;

    private volatile long lastPassiveResponseMs = System.currentTimeMillis();
    private volatile long lastPingResponseMs = 0;
    private volatile long lastPingSentMs = 0;

    private volatile long lastBindMs = 0;

    private volatile boolean pingInProgress = false;

    public boolean shouldPing(long now, long passiveTimeoutMs) {

        if (pingInProgress) {
            return false;
        }

        if (now - lastPassiveResponseMs <= passiveTimeoutMs) {
            return false;
        }

        return lastPingSentMs == 0;
    }

    public void markPingSent() {
        pingInProgress = true;
        lastPingSentMs = System.currentTimeMillis();
    }

    public void markPingResponse() {
        pingInProgress = false;
        lastPingResponseMs = System.currentTimeMillis();
        lastPassiveResponseMs = System.currentTimeMillis();
        resetSuspicion();
    }

    public boolean isPingInProgress() {
        return pingInProgress;
    }

    public void markDead() {
        state = BackendState.DEAD;
    }

    public void markPassiveResponse() {
        lastPassiveResponseMs = System.currentTimeMillis();

        resetSuspicion();
    }

    public void markPingFailed() {
        pingInProgress = false;
    }

    public boolean isDead(long now, long passiveTimeoutMs, long pingGraceMs) {
        boolean passiveDead =
                now - lastPassiveResponseMs > passiveTimeoutMs;

        boolean pingFailed =
                lastPingSentMs > 0
                        && lastPingResponseMs < lastPingSentMs
                        && now - lastPingSentMs > pingGraceMs;

        return passiveDead && pingFailed;
    }

    public boolean isInConnectGrace(long now, long graceMs) {
        return lastBindMs > 0 && (now - lastBindMs) < graceMs;
    }

    public void markSessionBound() {
        lastBindMs = System.currentTimeMillis();
    }

    private void resetSuspicion() {
        lastPingSentMs = 0;
        lastPingResponseMs = 0;

        if (state == BackendState.DEAD) {
            state = BackendState.HEALTHY;
        }
    }

    public BackendState getState() {
        return state;
    }

    public enum BackendState {
        HEALTHY, DEAD;
    }
}


