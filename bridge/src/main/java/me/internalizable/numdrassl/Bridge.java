package me.internalizable.numdrassl;

import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import me.internalizable.numdrassl.common.RandomUtil;
import me.internalizable.numdrassl.common.SecretMessageUtil;

import java.nio.charset.StandardCharsets;
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

        getLogger().at(Level.INFO).log("Bridge plugin initialized for server: " + config.get().getServerName());
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
                    this.config.get().getServerName(),
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

    /**
     * Gets the Bridge configuration.
     */
    public BridgeConfig getConfiguration() {
        return config.get();
    }
}

