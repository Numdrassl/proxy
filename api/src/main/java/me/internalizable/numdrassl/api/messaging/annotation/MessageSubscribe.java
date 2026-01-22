package me.internalizable.numdrassl.api.messaging.annotation;

import me.internalizable.numdrassl.api.messaging.MessagingService;
import me.internalizable.numdrassl.api.messaging.channel.SystemChannel;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a cross-proxy message subscriber.
 *
 * <p>Methods annotated with {@code @MessageSubscribe} are automatically registered
 * when the containing class is registered with the messaging service.</p>
 *
 * <h2>Method Signature</h2>
 * <p>The annotated method may accept either:</p>
 * <ul>
 *   <li><b>One parameter</b>: the message data type only (e.g., {@code ScoreData data})</li>
 *   <li><b>Two parameters</b>: the source proxy ID as a {@code String} first, followed by
 *       the message data type (e.g., {@code String sourceProxyId, GameEvent event})</li>
 * </ul>
 *
 * <p>The parameter order for two-parameter methods is always:
 * {@code (String sourceProxyId, MessageType data)}</p>
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * @Plugin(id = "my-plugin", name = "My Plugin", version = "1.0.0")
 * public class MyPlugin {
 *
 *     // Single parameter: message data type only
 *     @MessageSubscribe(channel = "scores")
 *     public void onScoreUpdate(ScoreData data) {
 *         logger.info("Player {} scored {}", data.playerName(), data.score());
 *     }
 *
 *     // Two parameters: sourceProxyId first, then message data type
 *     @MessageSubscribe(channel = "events")
 *     public void onGameEvent(String sourceProxyId, GameEvent event) {
 *         logger.info("Received event from proxy {}", sourceProxyId);
 *     }
 *
 *     // Subscribe to system channel messages (type-safe enum)
 *     @MessageSubscribe(SystemChannel.CHAT)
 *     public void onChatMessage(ChatMessage message) {
 *         // Handle cross-proxy chat
 *     }
 *
 *     // Subscribe to heartbeats
 *     @MessageSubscribe(SystemChannel.HEARTBEAT)
 *     public void onHeartbeat(HeartbeatMessage message) {
 *         logger.info("Proxy {} is alive", message.sourceProxyId());
 *     }
 * }
 * }</pre>
 *
 * <h2>Registration</h2>
 * <pre>{@code
 * // In your plugin's onEnable:
 * proxy.getMessagingService().registerListener(this);
 * }</pre>
 *
 * @see SystemChannel
 * @see MessagingService#registerListener(Object)
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MessageSubscribe {

    /**
     * The system channel to subscribe to.
     *
     * <p>Use this for subscribing to built-in system channels like
     * {@link SystemChannel#CHAT} or {@link SystemChannel#HEARTBEAT}.</p>
     *
     * <p>Leave as {@link SystemChannel#NONE} when using {@link #channel()} for plugin messages.</p>
     *
     * @return the system channel to subscribe to
     */
    SystemChannel value() default SystemChannel.NONE;

    /**
     * The channel name for plugin-specific messages.
     *
     * <p>When specified, the plugin ID is automatically inferred from the
     * {@link me.internalizable.numdrassl.api.plugin.Plugin @Plugin} annotation
     * on the listener class (or its enclosing plugin class).</p>
     *
     * <p>Leave empty when using {@link #value()} with a system channel.</p>
     *
     * @return the channel name within the plugin
     */
    String channel() default "";

    /**
     * Whether to receive messages sent by this proxy instance.
     *
     * <p>Default is {@code false} - messages from the local proxy are filtered out.</p>
     *
     * @return true to include self-messages
     */
    boolean includeSelf() default false;
}

