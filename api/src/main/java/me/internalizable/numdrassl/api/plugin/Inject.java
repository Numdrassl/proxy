package me.internalizable.numdrassl.api.plugin;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field or constructor parameter for dependency injection.
 *
 * <p>The plugin loader will automatically inject available services into
 * fields or constructor parameters annotated with {@code @Inject}.</p>
 *
 * <h2>Available Injectables</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.api.ProxyServer} - The proxy server instance</li>
 *   <li>{@link me.internalizable.numdrassl.api.event.EventManager} - Event registration</li>
 *   <li>{@link me.internalizable.numdrassl.api.command.CommandManager} - Command registration</li>
 *   <li>{@link me.internalizable.numdrassl.api.messaging.MessagingService} - Cross-proxy messaging</li>
 *   <li>{@link me.internalizable.numdrassl.api.scheduler.Scheduler} - Task scheduling</li>
 *   <li>{@link org.slf4j.Logger} - Plugin-specific logger</li>
 *   <li>{@link java.nio.file.Path} - Plugin data directory (annotate with {@link DataDirectory})</li>
 * </ul>
 *
 * <h2>Field Injection</h2>
 * <pre>{@code
 * @Plugin(id = "my-plugin", name = "My Plugin", version = "1.0.0")
 * public class MyPlugin {
 *
 *     @Inject
 *     private ProxyServer server;
 *
 *     @Inject
 *     private MessagingService messaging;
 *
 *     @Inject
 *     private Logger logger;
 *
 *     @Subscribe
 *     public void onInit(ProxyInitializeEvent event) {
 *         logger.info("Plugin loaded with {} players", server.getPlayerCount());
 *     }
 * }
 * }</pre>
 *
 * <h2>Constructor Injection</h2>
 * <pre>{@code
 * @Plugin(id = "my-plugin", name = "My Plugin", version = "1.0.0")
 * public class MyPlugin {
 *
 *     private final ProxyServer server;
 *     private final Logger logger;
 *
 *     @Inject
 *     public MyPlugin(ProxyServer server, Logger logger) {
 *         this.server = server;
 *         this.logger = logger;
 *     }
 * }
 * }</pre>
 *
 * @see Plugin
 * @see DataDirectory
 */
@Documented
@Target({ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {
}

