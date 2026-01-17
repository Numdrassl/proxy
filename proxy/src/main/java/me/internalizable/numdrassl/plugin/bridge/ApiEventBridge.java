package me.internalizable.numdrassl.plugin.bridge;

import com.hypixel.hytale.protocol.Packet;
import me.internalizable.numdrassl.config.BackendServer;
import me.internalizable.numdrassl.event.mapping.PacketEventRegistry;
import me.internalizable.numdrassl.event.packet.PacketEvent;
import me.internalizable.numdrassl.event.packet.PacketListener;
import me.internalizable.numdrassl.plugin.NumdrasslProxy;
import me.internalizable.numdrassl.session.ProxySession;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Facade that bridges the internal proxy system with the API event system.
 *
 * <p>Composes two specialized handlers:</p>
 * <ul>
 *   <li>{@link SessionLifecycleHandler} - Session lifecycle events</li>
 *   <li>{@link PacketEventBridge} - Packet-to-event translation</li>
 * </ul>
 *
 * <p>This class implements {@link PacketListener} to integrate with the internal
 * event system while delegating actual work to focused, single-responsibility classes.</p>
 */
public final class ApiEventBridge implements PacketListener {

    private final SessionLifecycleHandler lifecycleHandler;
    private final PacketEventBridge packetBridge;

    public ApiEventBridge(@Nonnull NumdrasslProxy proxy) {
        Objects.requireNonNull(proxy, "proxy");
        this.lifecycleHandler = new SessionLifecycleHandler(proxy);
        this.packetBridge = new PacketEventBridge(proxy, proxy.getNumdrasslEventManager());
    }

    // ==================== PacketListener Implementation ====================

    @Override
    public void onSessionCreated(@Nonnull ProxySession session) {
        lifecycleHandler.onSessionCreated(session);
    }

    @Override
    public void onSessionClosed(@Nonnull ProxySession session) {
        lifecycleHandler.onSessionClosed(session);
    }

    @Override
    @Nullable
    public <T extends Packet> T onClientPacket(@Nonnull PacketEvent<T> event) {
        return packetBridge.processClientPacket(event);
    }

    @Override
    @Nullable
    public <T extends Packet> T onServerPacket(@Nonnull PacketEvent<T> event) {
        return packetBridge.processServerPacket(event);
    }

    // ==================== Public API ====================

    /**
     * Fires ServerPreConnectEvent before connecting to a backend.
     */
    @Nonnull
    public ServerPreConnectResult fireServerPreConnectEvent(
            @Nonnull ProxySession session,
            @Nonnull BackendServer backend) {
        return lifecycleHandler.onServerPreConnect(session, backend);
    }

    /**
     * Fires ServerConnectedEvent after successful backend connection.
     */
    public void fireServerConnectedEvent(
            @Nonnull ProxySession session,
            @Nullable ProxySession previousSession) {
        lifecycleHandler.onServerConnected(session, previousSession);
    }

    /**
     * Fires PostLoginEvent after authentication completes.
     */
    public void firePostLoginEvent(@Nonnull ProxySession session) {
        lifecycleHandler.onPostLogin(session);
    }

    // ==================== Accessors ====================

    /**
     * Gets the packet event registry for registering custom mappings.
     */
    @Nonnull
    public PacketEventRegistry getPacketRegistry() {
        return packetBridge.getPacketRegistry();
    }

    /**
     * Gets the session lifecycle handler.
     */
    @Nonnull
    public SessionLifecycleHandler getLifecycleHandler() {
        return lifecycleHandler;
    }

    /**
     * Gets the packet event bridge.
     */
    @Nonnull
    public PacketEventBridge getPacketBridge() {
        return packetBridge;
    }
}
