package me.internalizable.numdrassl.event.mapping;

import com.hypixel.hytale.protocol.Packet;
import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.event.api.NumdrasslEventManager;
import me.internalizable.numdrassl.event.mapping.connection.ConnectMapping;
import me.internalizable.numdrassl.event.mapping.connection.DisconnectMapping;
import me.internalizable.numdrassl.event.mapping.interface_.ChatMessageMapping;
import me.internalizable.numdrassl.event.mapping.interface_.ServerMessageMapping;
import me.internalizable.numdrassl.plugin.NumdrasslProxy;
import me.internalizable.numdrassl.plugin.player.NumdrasslPlayer;
import me.internalizable.numdrassl.session.ProxySession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for packet-to-event mappings.
 */
public final class PacketEventRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(PacketEventRegistry.class);

    private final NumdrasslProxy apiProxy;
    private final NumdrasslEventManager eventManager;
    private final Map<Class<? extends Packet>, PacketEventMapping<?, ?>> mappings = new ConcurrentHashMap<>();

    public PacketEventRegistry(@Nonnull NumdrasslProxy apiProxy, @Nonnull NumdrasslEventManager eventManager) {
        this.apiProxy = Objects.requireNonNull(apiProxy, "apiProxy");
        this.eventManager = Objects.requireNonNull(eventManager, "eventManager");
        registerDefaultMappings();
    }

    public <P extends Packet, E> void register(@Nonnull PacketEventMapping<P, E> mapping) {
        Objects.requireNonNull(mapping, "mapping");
        mappings.put(mapping.getPacketClass(), mapping);
        LOGGER.debug("Registered packet mapping: {} -> {}",
            mapping.getPacketClass().getSimpleName(),
            mapping.getEventClass().getSimpleName());
    }

    public void unregister(@Nonnull Class<? extends Packet> packetClass) {
        Objects.requireNonNull(packetClass, "packetClass");
        mappings.remove(packetClass);
    }

    public boolean hasMapping(@Nonnull Class<? extends Packet> packetClass) {
        Objects.requireNonNull(packetClass, "packetClass");
        return mappings.containsKey(packetClass);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <P extends Packet> PacketEventMapping<P, ?> getMapping(@Nonnull Class<P> packetClass) {
        Objects.requireNonNull(packetClass, "packetClass");
        return (PacketEventMapping<P, ?>) mappings.get(packetClass);
    }

    public int getMappingCount() {
        return mappings.size();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <P extends Packet> P processPacket(
            @Nonnull ProxySession session,
            @Nonnull P packet,
            @Nonnull PacketContext.Direction direction) {

        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(direction, "direction");

        PacketEventMapping<P, Object> mapping = (PacketEventMapping<P, Object>) mappings.get(packet.getClass());
        if (mapping == null) {
            return packet;
        }

        return processWithMapping(session, packet, direction, mapping);
    }

    private <P extends Packet> P processWithMapping(
            ProxySession session,
            P packet,
            PacketContext.Direction direction,
            PacketEventMapping<P, Object> mapping) {

        // Get or create cached player
        Player player = getOrCreatePlayer(session);
        PacketContext context = new PacketContext(session, player, direction);

        Object event = mapping.createEvent(context, packet);
        if (event == null) {
            return packet;
        }

        // Note: PermissionSetupEvent is fired earlier in ClientAuthenticationHandler.handleConnect()
        // This gives LuckPerms time to load data before LoginEvent fires.

        eventManager.fireSync(event);

        if (mapping.isCancelled(event)) {
            LOGGER.debug("Packet {} cancelled by event handler", packet.getClass().getSimpleName());
            return null;
        }

        return mapping.applyChanges(context, packet, event);
    }

    /**
     * Gets or creates a cached player for the session.
     */
    @Nullable
    private Player getOrCreatePlayer(ProxySession session) {
        // Check for cached player first
        Player cached = session.getCachedPlayer();
        if (cached != null) {
            return cached;
        }

        // Create new player if identity is available
        if (session.getPlayerUuid() != null || session.getUsername() != null) {
            NumdrasslPlayer player = new NumdrasslPlayer(session, apiProxy);
            session.setCachedPlayer(player);
            return player;
        }

        // Return a temporary player for packets before identity is known
        return new NumdrasslPlayer(session, apiProxy);
    }

    private void registerDefaultMappings() {
        register(new ConnectMapping());
        register(new DisconnectMapping());
        register(new ChatMessageMapping());
        register(new ServerMessageMapping());

        LOGGER.info("Registered {} default packet-event mappings", mappings.size());
    }
}

