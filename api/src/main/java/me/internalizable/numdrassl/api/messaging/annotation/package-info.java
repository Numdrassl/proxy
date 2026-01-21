/**
 * Annotation-based API for cross-proxy message subscriptions.
 *
 * <p><strong>Important:</strong> Use {@code @MessageSubscribe} for cross-proxy messaging.
 * Do not confuse with {@code @Subscribe} from {@code api.event} which is for local events.</p>
 *
 * <p>Provides a declarative alternative to programmatic subscriptions:</p>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.api.messaging.annotation.MessageSubscribe} - Marks methods as cross-proxy message handlers</li>
 *   <li>{@link me.internalizable.numdrassl.api.messaging.annotation.TypeAdapter} - Custom serialization for payloads</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Plugin(id = "my-plugin", name = "My Plugin", version = "1.0.0")
 * public class MyPlugin {
 *
 *     @MessageSubscribe(channel = "scores")
 *     public void onScore(ScoreData data) {
 *         // Handle cross-proxy plugin message
 *     }
 *
 *     @MessageSubscribe(SystemChannel.HEARTBEAT)
 *     public void onHeartbeat(HeartbeatMessage msg) {
 *         // Handle cross-proxy system message
 *     }
 * }
 * }</pre>
 *
 * @see me.internalizable.numdrassl.api.messaging.MessagingService#registerListener(Object)
 * @see me.internalizable.numdrassl.api.messaging.annotation.MessageSubscribe
 */
package me.internalizable.numdrassl.api.messaging.annotation;

