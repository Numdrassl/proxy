package me.internalizable.numdrassl.session;

/**
 * Represents the lifecycle state of a proxy session.
 *
 * <p>State transitions follow this flow:</p>
 * <pre>
 * HANDSHAKING → AUTHENTICATING → CONNECTING → CONNECTED
 *                                     ↓
 *                              TRANSFERRING → CONNECTED
 *                                     ↓
 *                              DISCONNECTED
 * </pre>
 */
public enum SessionState {

    /**
     * Initial state - awaiting Connect packet from client.
     */
    HANDSHAKING,

    /**
     * Connect received, authenticating with Hytale session service.
     */
    AUTHENTICATING,

    /**
     * Authentication complete, establishing backend connection.
     */
    CONNECTING,

    /**
     * Fully connected and actively proxying packets.
     */
    CONNECTED,

    /**
     * Switching to a different backend server.
     */
    TRANSFERRING,

    /**
     * Session has ended.
     */
    DISCONNECTED;

    /**
     * Checks if this state allows packet forwarding to backend.
     */
    public boolean canForwardToBackend() {
        return this == CONNECTED;
    }

    /**
     * Checks if this state represents an active connection.
     */
    public boolean isActive() {
        return this != DISCONNECTED;
    }

    /**
     * Checks if this state is during the connection setup phase.
     */
    public boolean isConnecting() {
        return this == HANDSHAKING || this == AUTHENTICATING || this == CONNECTING;
    }
}
