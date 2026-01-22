package me.internalizable.numdrassl;

import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import me.internalizable.numdrassl.common.PluginMessagePacket;
import me.internalizable.numdrassl.common.RandomUtil;
import me.internalizable.numdrassl.common.SecretMessageUtil;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * Bridge plugin for Hytale servers that enables proxy integration.
 *
 * <p>This plugin handles:</p>
 * <ul>
 *   <li>Player authentication via signed referral data from the proxy</li>
 *   <li>Plugin messaging between the proxy and this server</li>
 * </ul>
 */
public class Bridge extends JavaPlugin {

    /**
     * Marker to identify control connections from the proxy.
     */
    public static final String CONTROL_CONNECTION_MARKER = "NUMDRASSL_CONTROL";

    private final Config<BridgeConfig> config = this.withConfig("config", BridgeConfig.CODEC);

    /**
     * Registered plugin message handlers.
     * Key: channel name, Value: handler that receives (channel, data)
     */
    private final Map<String, BiConsumer<String, byte[]>> messageHandlers = new ConcurrentHashMap<>();

    public Bridge(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        HytaleServer.get().getEventBus().register(
                EventPriority.FIRST,
                PlayerSetupConnectEvent.class,
                this::onPlayerSetupConnect
        );

        this.config.save();

        getLogger().at(Level.INFO).log("Bridge plugin initialized for server: " + config.get().getServerName());
    }

    /**
     * Registers a handler for plugin messages on a specific channel.
     *
     * @param channel the channel to listen on (e.g., "luckperms:sync")
     * @param handler the handler that receives (channel, data)
     */
    public void registerMessageHandler(String channel, BiConsumer<String, byte[]> handler) {
        messageHandlers.put(channel, handler);
        getLogger().at(Level.INFO).log("Registered plugin message handler for channel: " + channel);
    }

    /**
     * Unregisters a plugin message handler.
     *
     * @param channel the channel to stop listening on
     */
    public void unregisterMessageHandler(String channel) {
        messageHandlers.remove(channel);
    }

    private void onPlayerSetupConnect(PlayerSetupConnectEvent event) {
        byte[] data = event.getReferralData();

        if (data == null) {
            event.setCancelled(true);
            event.setReason("You have to go through our main proxy to join this server.");
            return;
        }

        // Check if this is a control connection from the proxy
        if (isControlConnection(data)) {
            handleControlConnection(event, data);
            return;
        }

        // Normal player connection - verify referral
        handlePlayerConnection(event, data);
    }

    /**
     * Checks if the referral data indicates a control connection.
     */
    private boolean isControlConnection(byte[] data) {
        if (data.length < 2) {
            return false;
        }

        try {
            ByteBuf buf = Unpooled.wrappedBuffer(data);
            int markerLen = buf.readUnsignedShort();

            if (markerLen != CONTROL_CONNECTION_MARKER.length()) {
                return false;
            }

            byte[] markerBytes = new byte[markerLen];
            buf.readBytes(markerBytes);
            String marker = new String(markerBytes, StandardCharsets.UTF_8);

            return CONTROL_CONNECTION_MARKER.equals(marker);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Handles a control connection from the proxy.
     */
    private void handleControlConnection(PlayerSetupConnectEvent event, byte[] data) {
        try {
            ByteBuf buf = Unpooled.wrappedBuffer(data);
            byte[] secret = getProxySecret();

            // Skip marker
            int markerLen = buf.readUnsignedShort();
            buf.skipBytes(markerLen);

            // Read timestamp
            long timestamp = buf.readLong();
            long now = System.currentTimeMillis();

            // Check timestamp (allow 5 minute window)
            if (Math.abs(now - timestamp) > 300_000) {
                getLogger().at(Level.WARNING).log("Control connection rejected: timestamp too old");
                event.setCancelled(true);
                event.setReason("Control handshake expired");
                return;
            }

            // Read server name
            int serverNameLen = buf.readUnsignedShort();
            byte[] serverNameBytes = new byte[serverNameLen];
            buf.readBytes(serverNameBytes);
            String serverName = new String(serverNameBytes, StandardCharsets.UTF_8);

            // Verify server name matches
            if (!serverName.equalsIgnoreCase(config.get().getServerName())) {
                getLogger().at(Level.WARNING).log("Control connection rejected: server name mismatch");
                event.setCancelled(true);
                event.setReason("Server name mismatch");
                return;
            }

            // Verify HMAC signature
            int dataLen = buf.readerIndex();
            byte[] dataToVerify = new byte[dataLen];
            buf.getBytes(0, dataToVerify);

            byte[] signature = new byte[buf.readableBytes()];
            buf.readBytes(signature);

            byte[] expectedSignature = SecretMessageUtil.calculateHmac(dataToVerify, secret);
            if (!java.util.Arrays.equals(signature, expectedSignature)) {
                getLogger().at(Level.WARNING).log("Control connection rejected: invalid signature");
                event.setCancelled(true);
                event.setReason("Invalid control signature");
                return;
            }

            getLogger().at(Level.INFO).log("Control connection established from proxy");

            // TODO: Set up this connection as a control channel
            // For now, we cancel it since we can't spawn a "fake" player
            // The proper implementation would maintain this as a special connection type
            event.setCancelled(true);
            event.setReason("Control connection accepted - channel established");

        } catch (Exception e) {
            getLogger().at(Level.SEVERE).log("Error handling control connection: " + e.getMessage());
            event.setCancelled(true);
            event.setReason("Control connection error");
        }
    }

    /**
     * Handles a normal player connection with referral verification.
     */
    private void handlePlayerConnection(PlayerSetupConnectEvent event, byte[] data) {
        // Check if data contains a plugin message
        if (PluginMessagePacket.isPluginMessage(data)) {
            handlePluginMessage(data);
            // Don't cancel - let the normal connection proceed
            // The plugin message was embedded alongside the referral
        }

        try {
            ByteBuf buf = Unpooled.copiedBuffer(data);
            byte[] secret = getProxySecret();

            SecretMessageUtil.BackendPlayerInfoMessage message = SecretMessageUtil.validateAndDecodePlayerInfoReferral(
                    buf,
                    event.getUuid(),
                    event.getUsername(),
                    this.config.get().getServerName(),
                    secret
            );

            if (message == null) {
                event.setCancelled(true);
                event.setReason("Could not verify your player information. Make sure you are connecting through the correct proxy.");
            }
        } catch (Throwable throwable) {
            getLogger().at(Level.SEVERE).log("Error verifying player information: " + throwable.getMessage());
            throwable.printStackTrace();
            event.setCancelled(true);
            event.setReason("Internal error while verifying player information: " + throwable.getClass().getSimpleName());
        }
    }

    /**
     * Handles an incoming plugin message.
     */
    private void handlePluginMessage(byte[] data) {
        PluginMessagePacket packet = PluginMessagePacket.fromBytes(data);
        if (packet == null) {
            getLogger().at(Level.WARNING).log("Received malformed plugin message");
            return;
        }

        String channel = packet.getChannel();
        byte[] payload = packet.getDataUnsafe();

        getLogger().at(Level.FINE).log("Received plugin message on channel: " + channel + " (" + payload.length + " bytes)");

        // Find and invoke handler
        BiConsumer<String, byte[]> handler = messageHandlers.get(channel);
        if (handler != null) {
            try {
                handler.accept(channel, payload);
            } catch (Exception e) {
                getLogger().at(Level.SEVERE).log("Error in plugin message handler for " + channel + ": " + e.getMessage());
            }
        }
    }

    private byte[] getProxySecret() {
        byte[] proxySecret = System.getenv("NUMDRASSL_SECRET") != null
                ? System.getenv("NUMDRASSL_SECRET").getBytes(StandardCharsets.UTF_8)
                : null;

        if (proxySecret == null) {
            String configProxySecret = config.get().getProxySecret();

            if (configProxySecret == null) {
                return RandomUtil.generateSecureRandomString(32).getBytes(StandardCharsets.UTF_8);
            }

            proxySecret = config.get().getProxySecret().getBytes(StandardCharsets.UTF_8);
        }

        return proxySecret;
    }
}
