package me.internalizable.numdrassl.session;

import io.netty.incubator.codec.quic.QuicChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for managing active proxy sessions.
 *
 * <p>Provides multiple lookup strategies:</p>
 * <ul>
 *   <li>By session ID (always available)</li>
 *   <li>By QUIC channel (for network event handling)</li>
 *   <li>By player UUID (after authentication)</li>
 * </ul>
 *
 * <p>All operations are thread-safe and suitable for concurrent access
 * from multiple Netty event loop threads.</p>
 */
public final class SessionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionManager.class);

    private final Map<Long, ProxySession> sessionsById = new ConcurrentHashMap<>();
    private final Map<QuicChannel, ProxySession> sessionsByChannel = new ConcurrentHashMap<>();
    private final Map<UUID, ProxySession> sessionsByUuid = new ConcurrentHashMap<>();

    // ==================== Registration ====================

    /**
     * Registers a new session.
     *
     * @param session the session to register
     */
    public void addSession(@Nonnull ProxySession session) {
        Objects.requireNonNull(session, "session");

        sessionsById.put(session.getSessionId(), session);
        sessionsByChannel.put(session.getClientChannel(), session);

        LOGGER.info("Session registered: {}", session.getSessionId());
    }

    /**
     * Associates a player UUID with a session.
     *
     * <p>Called after the Connect packet is received but before full authentication.
     * Does not kick existing sessions with the same UUID.</p>
     *
     * @param session the session to register
     */
    public void registerPlayerUuid(@Nonnull ProxySession session) {
        registerPlayerUuid(session, false);
    }

    /**
     * Associates a player UUID with a session, optionally kicking existing sessions.
     *
     * <p>When {@code kickExisting} is true, any existing session with the same UUID
     * will be disconnected. This should only be done after the new session is fully
     * authenticated and connected to a backend.</p>
     *
     * @param session the session to register
     * @param kickExisting whether to disconnect existing sessions with the same UUID
     */
    public void registerPlayerUuid(@Nonnull ProxySession session, boolean kickExisting) {
        Objects.requireNonNull(session, "session");

        UUID uuid = session.getPlayerUuid();
        if (uuid == null) {
            return;
        }

        if (kickExisting) {
            ProxySession existing = sessionsByUuid.get(uuid);
            if (existing != null && existing != session) {
                LOGGER.info("Disconnecting existing session {} for UUID: {}",
                    existing.getSessionId(), uuid);
                existing.disconnect("Another connection with same account");
            }
        }

        sessionsByUuid.put(uuid, session);
    }

    // ==================== Removal ====================

    /**
     * Removes a session from all indexes.
     *
     * @param session the session to remove
     */
    public void removeSession(@Nonnull ProxySession session) {
        Objects.requireNonNull(session, "session");

        sessionsById.remove(session.getSessionId());
        sessionsByChannel.remove(session.getClientChannel());

        UUID uuid = session.getPlayerUuid();
        if (uuid != null) {
            // Only remove if it's still mapped to this session
            sessionsByUuid.remove(uuid, session);
        }

        LOGGER.info("Session removed: {}", session.getSessionId());
    }

    // ==================== Lookup ====================

    /**
     * Finds a session by its ID.
     *
     * @param sessionId the session ID
     * @return the session, or empty if not found
     */
    @Nonnull
    public Optional<ProxySession> findById(long sessionId) {
        return Optional.ofNullable(sessionsById.get(sessionId));
    }

    /**
     * Finds a session by its QUIC channel.
     *
     * @param channel the QUIC channel
     * @return the session, or empty if not found
     */
    @Nonnull
    public Optional<ProxySession> findByChannel(@Nonnull QuicChannel channel) {
        Objects.requireNonNull(channel, "channel");
        return Optional.ofNullable(sessionsByChannel.get(channel));
    }

    /**
     * Finds a session by player UUID.
     *
     * @param uuid the player UUID
     * @return the session, or empty if not found
     */
    @Nonnull
    public Optional<ProxySession> findByUuid(@Nonnull UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return Optional.ofNullable(sessionsByUuid.get(uuid));
    }

    // Legacy methods for backward compatibility

    /**
     * @deprecated Use {@link #findById(long)} instead
     */
    @Deprecated
    public ProxySession getSession(long sessionId) {
        return sessionsById.get(sessionId);
    }

    /**
     * @deprecated Use {@link #findByChannel(QuicChannel)} instead
     */
    @Deprecated
    public ProxySession getSession(@Nonnull QuicChannel channel) {
        return sessionsByChannel.get(channel);
    }

    /**
     * @deprecated Use {@link #findByUuid(UUID)} instead
     */
    @Deprecated
    public ProxySession getSession(@Nonnull UUID uuid) {
        return sessionsByUuid.get(uuid);
    }

    // ==================== Bulk Operations ====================

    /**
     * Returns an unmodifiable view of all active sessions.
     *
     * @return collection of all sessions
     */
    @Nonnull
    public Collection<ProxySession> getAllSessions() {
        return Collections.unmodifiableCollection(sessionsById.values());
    }

    /**
     * Returns the number of active sessions.
     *
     * @return session count
     */
    public int getSessionCount() {
        return sessionsById.size();
    }

    /**
     * Checks if there are any active sessions.
     *
     * @return true if at least one session exists
     */
    public boolean hasActiveSessions() {
        return !sessionsById.isEmpty();
    }

    // ==================== Lifecycle ====================

    /**
     * Closes all active sessions.
     *
     * <p>Called during proxy shutdown. Sessions are closed gracefully
     * without sending disconnect reasons.</p>
     */
    public void closeAll() {
        int count = sessionsById.size();
        if (count == 0) {
            return;
        }

        LOGGER.info("Closing {} active session(s)", count);

        for (ProxySession session : sessionsById.values()) {
            closeSessionSafely(session);
        }

        sessionsById.clear();
        sessionsByChannel.clear();
        sessionsByUuid.clear();
    }

    private void closeSessionSafely(ProxySession session) {
        try {
            session.close();
        } catch (Exception e) {
            LOGGER.error("Error closing session {}", session.getSessionId(), e);
        }
    }
}
