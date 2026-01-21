package me.internalizable.numdrassl.messaging.processing;

import me.internalizable.numdrassl.api.plugin.Plugin;
import me.internalizable.numdrassl.api.plugin.PluginContainer;
import me.internalizable.numdrassl.api.plugin.PluginManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility for extracting plugin IDs from classes annotated with {@link Plugin}.
 *
 * <p>Supports multiple extraction strategies:</p>
 * <ol>
 *   <li>From @Plugin annotation on the class directly</li>
 *   <li>From enclosing classes (for inner classes)</li>
 *   <li>From PluginManager by matching the plugin instance</li>
 * </ol>
 */
public final class PluginIdExtractor {

    private PluginIdExtractor() {
        // Utility class
    }

    /**
     * Extract plugin ID from a class's @Plugin annotation (direct only).
     *
     * @param clazz the class to check
     * @return the plugin ID, or null if not found
     */
    @Nullable
    public static String fromClass(@Nonnull Class<?> clazz) {
        Plugin pluginAnnotation = clazz.getAnnotation(Plugin.class);
        return pluginAnnotation != null ? pluginAnnotation.id() : null;
    }

    /**
     * Extract plugin ID from an object's class or its enclosing classes.
     *
     * <p>Checks in order:</p>
     * <ol>
     *   <li>The object's class directly</li>
     *   <li>Enclosing classes (for inner classes)</li>
     * </ol>
     *
     * @param listener the listener object
     * @return the plugin ID, or null if not found
     */
    @Nullable
    public static String fromListener(@Nonnull Object listener) {
        Class<?> clazz = listener.getClass();

        // Check the listener class itself
        String pluginId = fromClass(clazz);
        if (pluginId != null) {
            return pluginId;
        }

        // Check enclosing class (for inner classes)
        Class<?> enclosing = clazz.getEnclosingClass();
        while (enclosing != null) {
            pluginId = fromClass(enclosing);
            if (pluginId != null) {
                return pluginId;
            }
            enclosing = enclosing.getEnclosingClass();
        }

        return null;
    }

    /**
     * Extract plugin ID using the PluginManager to find the owning plugin.
     *
     * <p>This method checks if the listener object is the plugin instance itself
     * by comparing with all registered plugins.</p>
     *
     * @param listener the listener object
     * @param pluginManager the plugin manager to query
     * @return the plugin ID, or null if not found
     */
    @Nullable
    public static String fromPluginManager(@Nonnull Object listener, @Nonnull PluginManager pluginManager) {
        for (PluginContainer container : pluginManager.getPlugins()) {
            if (container.getInstance().map(i -> i == listener).orElse(false)) {
                return container.getDescription().getId();
            }
        }
        return null;
    }

    /**
     * Extract plugin ID from an explicit plugin object.
     *
     * @param plugin the plugin object (must have @Plugin annotation)
     * @return the plugin ID, or null if not found
     */
    @Nullable
    public static String fromPlugin(@Nonnull Object plugin) {
        return fromClass(plugin.getClass());
    }
}

