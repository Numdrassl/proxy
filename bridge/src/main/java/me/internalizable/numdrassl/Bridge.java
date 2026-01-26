package me.internalizable.numdrassl;

import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.io.ValidationResult;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.io.ServerManager;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PacketWatcher;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import me.internalizable.numdrassl.common.RandomUtil;
import me.internalizable.numdrassl.common.SecretMessageUtil;
import me.internalizable.numdrassl.packet.ProxyPing;
import me.internalizable.numdrassl.packet.ProxyPong;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.function.BiFunction;
import java.util.logging.Level;

/**
 * Bridge plugin for Hytale servers that enables proxy integration.
 *
 * <p>This plugin handles:</p>
 * <ul>
 *   <li>Player authentication via signed referral data from the proxy</li>
 *   <li>Bidirectional plugin messaging between the proxy and this server</li>
 * </ul>
 *
 * <h2>Plugin Messaging</h2>
 * <p>Plugin messaging uses Redis pub/sub for cross-server communication.</p>
 * <!-- TODO: Implement Redis-based plugin messaging -->
 *
 */
public class Bridge extends JavaPlugin {

    private final Config<BridgeConfig> config = this.withConfig("config", BridgeConfig.CODEC);

    private static final String PACKET_REGISTRY = "com.hypixel.hytale.protocol.PacketRegistry";

    public Bridge(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // Register player authentication handler
        HytaleServer.get().getEventBus().register(
                EventPriority.FIRST,
                PlayerSetupConnectEvent.class,
                this::onPlayerSetupConnect
        );

        this.config.save();

        this.registerCustomPacket(ProxyPing.PACKET_ID, "ProxyPing", ProxyPing.class, 16, 16, false, ProxyPing::validateStructure, ProxyPing::deserialize);
        this.registerCustomPacket(ProxyPong.PACKET_ID, "ProxyPong", ProxyPong.class, 16, 16, false, ProxyPong::validateStructure, ProxyPong::deserialize);

        PacketAdapters.registerInbound((PacketWatcher) (handler, packet) -> {
            if (packet instanceof ProxyPing ping) {
                ProxyPong proxyPong = new ProxyPong();
                proxyPong.nonce = ping.nonce;
                proxyPong.timestamp = System.currentTimeMillis();
                handler.writeNoCache(proxyPong);
            }
        });

        getLogger().at(Level.INFO).log("Bridge plugin initialized for server: " + config.get().getServerName());
    }

    // ==================== Custom Packets ====================

    private void registerCustomPacket(int id, String name, Class<? extends Packet> type, int fixedBlockSize, int maxSize, boolean compressed, BiFunction<ByteBuf, Integer, ValidationResult> validate, BiFunction<ByteBuf, Integer, Packet> deserialize) {
        if (!this.hasRegisterCustomPacket()) {
            getLogger().at(Level.WARNING).log("Cannot register custom packet. early plugin bridge-packets not loaded.\nEnsure the server is started with --accept-early-plugins parameter, and the bridge-packets plugin is in the earlyplugins/ folder.\nSome proxy functions may not work properly.");
            return;
        }

        try {
            Class<?> registryClass = Class.forName(PACKET_REGISTRY);
            var method = registryClass.getDeclaredMethod(
                    "registerCustomPacket",
                    int.class,
                    String.class,
                    Class.class,
                    int.class,
                    int.class,
                    boolean.class,
                    java.util.function.BiFunction.class,
                    java.util.function.BiFunction.class
            );

            method.invoke(null, id, name, type, fixedBlockSize, maxSize, compressed, validate, deserialize);
            getLogger().at(Level.INFO).log("Registered successful CustomPacket: " + name);
        } catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            getLogger().at(Level.WARNING).log("Failed to register custom packets.");
        }
    }

    private boolean hasRegisterCustomPacket() {
        try {
            Class<?> registryClass = Class.forName(PACKET_REGISTRY);

            registryClass.getDeclaredMethod(
                    "registerCustomPacket",
                    int.class,
                    String.class,
                    Class.class,
                    int.class,
                    int.class,
                    boolean.class,
                    java.util.function.BiFunction.class,
                    java.util.function.BiFunction.class
            );

            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ==================== Player Authentication ====================

    /**
     * Handles player connection setup - verifies the referral from the proxy.
     */
    private void onPlayerSetupConnect(PlayerSetupConnectEvent event) {
        byte[] data = event.getReferralData();

        if (data == null) {
            event.setCancelled(true);
            event.setReason("You have to go through our main proxy to join this server.");
            return;
        }

        verifyPlayerReferral(event, data);
    }

    /**
     * Verifies the player's referral data from the proxy.
     */
    private void verifyPlayerReferral(PlayerSetupConnectEvent event, byte[] data) {
        try {
            ByteBuf buf = Unpooled.copiedBuffer(data);
            byte[] secret = getProxySecret();

            SecretMessageUtil.BackendPlayerInfoMessage message = SecretMessageUtil.validateAndDecodePlayerInfoReferral(
                    buf,
                    event.getUuid(),
                    event.getUsername(),
                    this.getServerName(),
                    secret
            );

            if (message == null) {
                event.setCancelled(true);
                event.setReason("Could not verify your player information. Make sure you are connecting through the correct proxy.");
            } else {
                getLogger().at(Level.INFO).log("Player " + event.getUsername() + " authenticated via proxy");
            }
        } catch (Throwable t) {
            getLogger().at(Level.SEVERE).log("Error verifying player information: " + t.getMessage());
            t.printStackTrace();
            event.setCancelled(true);
            event.setReason("Internal error while verifying player information: " + t.getClass().getSimpleName());
        }
    }

    // ==================== Configuration ====================

    /**
     * Gets the proxy secret for HMAC verification.
     */
    private byte[] getProxySecret() {
        String envSecret = System.getenv("NUMDRASSL_SECRET");
        if (envSecret != null && !envSecret.isEmpty()) {
            return envSecret.getBytes(StandardCharsets.UTF_8);
        }

        String configSecret = config.get().getProxySecret();
        if (configSecret != null && !configSecret.isEmpty()) {
            return configSecret.getBytes(StandardCharsets.UTF_8);
        }

        getLogger().at(Level.WARNING).log("No proxy secret configured! Using random secret.");
        return RandomUtil.generateSecureRandomString(32).getBytes(StandardCharsets.UTF_8);
    }

    private String getServerName() {
        String envServerName = System.getenv("NUMDRASSL_SERVERNAME");
        if (envServerName != null && !envServerName.isEmpty()) {
            return envServerName;
        }

        String configServerName = config.get().getServerName();
        if (configServerName != null && !configServerName.isEmpty()) {
            return configServerName;
        }

        getLogger().at(Level.WARNING).log("No server name configured! Using default \"main\".");
        return "main";
    }

    /**
     * Gets the Bridge configuration.
     */
    public BridgeConfig getConfiguration() {
        return config.get();
    }
}

