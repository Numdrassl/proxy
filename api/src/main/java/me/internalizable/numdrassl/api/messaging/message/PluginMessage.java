package me.internalizable.numdrassl.api.messaging.message;

import me.internalizable.numdrassl.api.messaging.ChannelMessage;

import javax.annotation.Nonnull;
import java.time.Instant;

/**
 * Plugin-defined custom message for cross-proxy communication.
 *
 * <p>This message type allows plugins to send arbitrary data between
 * proxy instances. The payload is serialized to JSON.</p>
 *
 * <p>For most use cases, prefer the higher-level API:</p>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.api.messaging.MessagingService#publishPlugin}</li>
 *   <li>{@link me.internalizable.numdrassl.api.messaging.MessagingService#subscribePlugin}</li>
 *   <li>{@link me.internalizable.numdrassl.api.messaging.annotation.MessageSubscribe @MessageSubscribe} annotation</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // High-level API (recommended)
 * messaging.publishPlugin("my-plugin", "events", myData);
 *
 * // Low-level API
 * PluginMessage msg = new PluginMessage(
 *     proxyId, Instant.now(),
 *     "my-plugin", "events",
 *     gson.toJson(myData)
 * );
 * messaging.publish(Channels.PLUGIN, msg);
 * }</pre>
 *
 * @param sourceProxyId the proxy that sent the message
 * @param timestamp when the message was created
 * @param pluginId unique identifier of the sending plugin
 * @param channel sub-channel within the plugin namespace
 * @param payload JSON-serialized data payload
 */
public record PluginMessage(
        @Nonnull String sourceProxyId,
        @Nonnull Instant timestamp,
        @Nonnull String pluginId,
        @Nonnull String channel,
        @Nonnull String payload
) implements ChannelMessage {

    @Override
    @Nonnull
    public String messageType() {
        return "plugin";
    }
}

