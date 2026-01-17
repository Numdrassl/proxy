package me.internalizable.numdrassl;

import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.protocol.packets.auth.AuthGrant;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.Config;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import me.internalizable.numdrassl.common.RandomUtil;
import me.internalizable.numdrassl.common.SecretMessageUtil;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class Bridge extends JavaPlugin {
    private final Config<BridgeConfig> config = this.withConfig("config", BridgeConfig.CODEC);

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
    }

    private void onPlayerSetupConnect(PlayerSetupConnectEvent event) {
        byte[] data = event.getReferralData();

        if (data  == null) {
            event.setCancelled(true);
            event.setReason("You have to go through our main proxy to join this server.");
            return;
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
            getLogger().at(Level.SEVERE).log("Error verifying player information: " + throwable.getMessage(), throwable);
            throwable.printStackTrace();
            event.setCancelled(true);
            event.setReason("Internal error while verifying player information: " + throwable.getClass().getSimpleName());
        }
    }

    private byte[] getProxySecret() {
        byte[] proxySecret = System.getenv("NUMDRASSL_SECRET") != null ? System.getenv("NUMDRASSL_SECRET").getBytes(StandardCharsets.UTF_8) : null;

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
