package me.internalizable.numdrassl;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import lombok.Getter;
import me.internalizable.numdrassl.common.RandomUtil;

@Getter
public class BridgeConfig {
    private String proxySecret = RandomUtil.generateSecureRandomString(32);
    private String serverName = "main";

    public static final BuilderCodec<BridgeConfig> CODEC = BuilderCodec.builder(BridgeConfig.class, BridgeConfig::new)
            .append(
                    new KeyedCodec<>("SecretKey", Codec.STRING),
                    (config, str) -> config.proxySecret = str,
                    (config) -> config.proxySecret
            ).add()
            .append(
                    new KeyedCodec<>("ServerName", Codec.STRING),
                    (config, str) -> config.serverName = str,
                    (config) -> config.serverName
            ).add()
    .build();
}
