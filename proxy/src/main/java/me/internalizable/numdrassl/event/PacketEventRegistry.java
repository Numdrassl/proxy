package me.internalizable.numdrassl.event;

import com.hypixel.hytale.protocol.Packet;
import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.event.mapping.connection.ConnectMapping;
import me.internalizable.numdrassl.event.mapping.connection.DisconnectMapping;
import me.internalizable.numdrassl.event.mapping.interface_.ChatMessageMapping;
import me.internalizable.numdrassl.event.mapping.interface_.ServerMessageMapping;
import me.internalizable.numdrassl.event.mapping.inventory.SetActiveSlotMapping;
import me.internalizable.numdrassl.event.mapping.player.ClientMovementMapping;
import me.internalizable.numdrassl.event.mapping.player.ClientPlaceBlockMapping;
import me.internalizable.numdrassl.plugin.NumdrasslPlayer;
import me.internalizable.numdrassl.plugin.NumdrasslProxyServer;
import me.internalizable.numdrassl.session.ProxySession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for packet-to-event mappings.
 * Automatically translates low-level protocol packets into high-level API events.
 *
 * <p>Mappings are organized in subpackages that mirror the protocol package structure:</p>
 * <ul>
 *   <li>{@code mapping.interface_} - Chat messages, server messages, UI events</li>
 *   <li>{@code mapping.player} - Player movement, block placement, etc.</li>
 *   <li>{@code mapping.inventory} - Inventory actions, slot changes</li>
 *   <li>{@code mapping.connection} - Connect/disconnect events</li>
 * </ul>
 *
 * <p>Plugins can register custom mappings using {@link #register(PacketEventMapping)}.</p>
 */
public class PacketEventRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(PacketEventRegistry.class);

    private final NumdrasslProxyServer apiServer;
    private final NumdrasslEventManager eventManager;

    // Map: Packet class -> Mapping
    private final Map<Class<? extends Packet>, PacketEventMapping<?, ?>> mappings = new ConcurrentHashMap<>();

    public PacketEventRegistry(@Nonnull NumdrasslProxyServer apiServer, @Nonnull NumdrasslEventManager eventManager) {
        this.apiServer = apiServer;
        this.eventManager = eventManager;

        // Register default mappings
        registerDefaultMappings();
    }

    /**
     * Register a packet-to-event mapping.
     *
     * @param mapping the mapping to register
     * @param <P> the packet type
     * @param <E> the event type
     */
    public <P extends Packet, E> void register(@Nonnull PacketEventMapping<P, E> mapping) {
        mappings.put(mapping.getPacketClass(), mapping);
        LOGGER.debug("Registered packet mapping: {} -> {}",
            mapping.getPacketClass().getSimpleName(),
            mapping.getEventClass().getSimpleName());
    }

    /**
     * Unregister a mapping for a packet type.
     *
     * @param packetClass the packet class to unregister
     */
    public void unregister(@Nonnull Class<? extends Packet> packetClass) {
        PacketEventMapping<?, ?> removed = mappings.remove(packetClass);
        if (removed != null) {
            LOGGER.debug("Unregistered packet mapping for: {}", packetClass.getSimpleName());
        }
    }

    /**
     * Check if a mapping exists for a packet type.
     *
     * @param packetClass the packet class to check
     * @return true if a mapping exists
     */
    public boolean hasMapping(@Nonnull Class<? extends Packet> packetClass) {
        return mappings.containsKey(packetClass);
    }

    /**
     * Get the mapping for a packet type.
     *
     * @param packetClass the packet class
     * @return the mapping, or null if none exists
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <P extends Packet> PacketEventMapping<P, ?> getMapping(@Nonnull Class<P> packetClass) {
        return (PacketEventMapping<P, ?>) mappings.get(packetClass);
    }

    /**
     * Process a packet through its mapping, fire the corresponding event,
     * and return the potentially modified packet.
     *
     * @param session the player session
     * @param packet the packet
     * @param direction the packet direction
     * @return the processed packet, or null if cancelled
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <P extends Packet> P processPacket(
            @Nonnull ProxySession session,
            @Nonnull P packet,
            @Nonnull PacketContext.Direction direction) {

        PacketEventMapping<P, Object> mapping = (PacketEventMapping<P, Object>) mappings.get(packet.getClass());
        if (mapping == null) {
            return packet; // No mapping, pass through
        }

        // Create player wrapper
        Player player = new NumdrasslPlayer(session, apiServer);
        PacketContext context = new PacketContext(session, player, direction);

        // Create the event
        Object event = mapping.createEvent(context, packet);
        if (event == null) {
            return packet; // Mapping chose not to create event
        }

        // Fire the event
        eventManager.fireSync(event);

        // Check if cancelled
        if (mapping.isCancelled(event)) {
            LOGGER.debug("Packet {} cancelled by event handler", packet.getClass().getSimpleName());
            return null;
        }

        // Apply changes back to packet
        return mapping.applyChanges(context, packet, event);
    }

    /**
     * Get the number of registered mappings.
     *
     * @return the mapping count
     */
    public int getMappingCount() {
        return mappings.size();
    }

    /**
     * Register all default packet mappings from the mapping subpackages.
     */
    private void registerDefaultMappings() {
        // connection mappings - Connect/Disconnect
        register(new ConnectMapping());
        register(new DisconnectMapping());

        // interface_ mappings - Chat and UI
        register(new ChatMessageMapping());
        register(new ServerMessageMapping());

        // player mappings - Movement, block placement
        register(new ClientPlaceBlockMapping());
        register(new ClientMovementMapping());

        // inventory mappings - Slot changes
        register(new SetActiveSlotMapping());


        LOGGER.info("Registered {} default packet-event mappings", mappings.size());
    }
}
