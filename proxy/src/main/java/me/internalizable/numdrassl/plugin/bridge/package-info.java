/**
 * Event bridging between internal packet system and public API events.
 *
 * <p>This package handles the translation of internal events into
 * public API events that plugins can subscribe to.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.plugin.bridge.ApiEventBridge} - Facade that
 *       composes the specialized handlers and implements {@code PacketListener}.</li>
 *   <li>{@link me.internalizable.numdrassl.plugin.bridge.SessionLifecycleHandler} - Handles
 *       session lifecycle events (PreLogin, PostLogin, Disconnect, ServerConnect).</li>
 *   <li>{@link me.internalizable.numdrassl.plugin.bridge.PacketEventBridge} - Handles
 *       packet-to-event translation via {@code PacketEventRegistry}.</li>
 *   <li>{@link me.internalizable.numdrassl.plugin.bridge.ServerPreConnectResult} - Result
 *       record for server pre-connect event processing.</li>
 * </ul>
 *
 * <h2>Design Pattern: Facade</h2>
 * <p>{@code ApiEventBridge} acts as a facade, providing a unified interface while
 * delegating to focused, single-responsibility classes. This adheres to SRP while
 * keeping the external API simple.</p>
 *
 * <h2>Event Flow</h2>
 * <pre>
 * Internal Event
 *       │
 *       ▼
 * ApiEventBridge (Facade)
 *       │
 *       ├──► SessionLifecycleHandler
 *       │         │
 *       │         └──► Lifecycle API Events
 *       │
 *       └──► PacketEventBridge
 *                 │
 *                 └──► PacketEventRegistry
 *                           │
 *                           └──► Packet API Events
 * </pre>
 *
 * <h2>Session Lifecycle Events</h2>
 * <ul>
 *   <li>{@code PreLoginEvent} - When a connection is established</li>
 *   <li>{@code PostLoginEvent} - After authentication completes</li>
 *   <li>{@code DisconnectEvent} - When a session closes</li>
 *   <li>{@code ServerPreConnectEvent} - Before connecting to backend</li>
 *   <li>{@code ServerConnectedEvent} - After successful backend connection</li>
 * </ul>
 *
 * @see me.internalizable.numdrassl.plugin.bridge.ApiEventBridge
 * @see me.internalizable.numdrassl.event.PacketEventRegistry
 */
package me.internalizable.numdrassl.plugin.bridge;

