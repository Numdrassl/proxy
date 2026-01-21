package me.internalizable.numdrassl.api.plugin;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link java.nio.file.Path} injection point as the plugin's data directory.
 *
 * <p>Use this annotation alongside {@link Inject} to receive the plugin's
 * dedicated data directory for configuration and storage.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @Plugin(id = "my-plugin", name = "My Plugin", version = "1.0.0")
 * public class MyPlugin {
 *
 *     @Inject
 *     @DataDirectory
 *     private Path dataDirectory;
 *
 *     @Subscribe
 *     public void onInit(ProxyInitializeEvent event) {
 *         Path configFile = dataDirectory.resolve("config.yml");
 *         // Load configuration...
 *     }
 * }
 * }</pre>
 *
 * <p>The data directory is typically located at {@code plugins/<plugin-id>/}.</p>
 *
 * @see Inject
 * @see Plugin
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataDirectory {
}

