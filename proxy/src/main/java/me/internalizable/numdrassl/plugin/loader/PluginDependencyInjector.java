package me.internalizable.numdrassl.plugin.loader;

import me.internalizable.numdrassl.api.ProxyServer;
import me.internalizable.numdrassl.api.command.CommandManager;
import me.internalizable.numdrassl.api.event.EventManager;
import me.internalizable.numdrassl.api.messaging.MessagingService;
import me.internalizable.numdrassl.api.plugin.DataDirectory;
import me.internalizable.numdrassl.api.plugin.Inject;
import me.internalizable.numdrassl.api.scheduler.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Handles dependency injection for plugins.
 *
 * <p>Supports both constructor injection and field injection via {@link Inject}.</p>
 */
public final class PluginDependencyInjector {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginDependencyInjector.class);

    private final ProxyServer proxyServer;

    public PluginDependencyInjector(@Nonnull ProxyServer proxyServer) {
        this.proxyServer = Objects.requireNonNull(proxyServer, "proxyServer");
    }

    /**
     * Creates a plugin instance with dependency injection.
     *
     * @param mainClass the plugin's main class
     * @param pluginId the plugin's ID (for logger naming)
     * @param dataDirectory the plugin's data directory
     * @return the instantiated plugin with injected dependencies
     * @throws Exception if instantiation or injection fails
     */
    @Nonnull
    public Object createInstance(
            @Nonnull Class<?> mainClass,
            @Nonnull String pluginId,
            @Nonnull Path dataDirectory) throws Exception {

        Object instance = instantiate(mainClass, pluginId, dataDirectory);
        injectFields(instance, pluginId, dataDirectory);
        return instance;
    }

    /**
     * Instantiates the plugin using constructor injection if available.
     */
    private Object instantiate(Class<?> mainClass, String pluginId, Path dataDirectory) throws Exception {
        // Look for @Inject constructor
        for (Constructor<?> constructor : mainClass.getDeclaredConstructors()) {
            if (constructor.isAnnotationPresent(Inject.class)) {
                return instantiateWithConstructor(constructor, pluginId, dataDirectory);
            }
        }

        // Try default constructor
        try {
            Constructor<?> defaultConstructor = mainClass.getDeclaredConstructor();
            defaultConstructor.setAccessible(true);
            return defaultConstructor.newInstance();
        } catch (NoSuchMethodException e) {
            // No default constructor - try to find any constructor we can satisfy
            for (Constructor<?> constructor : mainClass.getDeclaredConstructors()) {
                if (canSatisfyConstructor(constructor)) {
                    return instantiateWithConstructor(constructor, pluginId, dataDirectory);
                }
            }
            throw new IllegalStateException(
                    "Plugin " + pluginId + " has no default constructor and no @Inject constructor");
        }
    }

    /**
     * Checks if we can satisfy all constructor parameters.
     */
    private boolean canSatisfyConstructor(Constructor<?> constructor) {
        for (Parameter param : constructor.getParameters()) {
            if (resolveDependency(param.getType(), false, null, null) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Instantiates using a specific constructor.
     */
    private Object instantiateWithConstructor(
            Constructor<?> constructor,
            String pluginId,
            Path dataDirectory) throws Exception {

        constructor.setAccessible(true);
        Parameter[] params = constructor.getParameters();
        Object[] args = new Object[params.length];

        for (int i = 0; i < params.length; i++) {
            Parameter param = params[i];
            boolean isDataDir = param.isAnnotationPresent(DataDirectory.class);
            args[i] = resolveDependency(param.getType(), isDataDir, pluginId, dataDirectory);

            if (args[i] == null) {
                throw new IllegalStateException(
                        "Cannot resolve constructor parameter: " + param.getType().getName() +
                        " for plugin " + pluginId);
            }
        }

        return constructor.newInstance(args);
    }

    /**
     * Performs field injection on an existing instance.
     */
    private void injectFields(Object instance, String pluginId, Path dataDirectory) {
        Class<?> clazz = instance.getClass();

        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Inject.class)) {
                    injectField(instance, field, pluginId, dataDirectory);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * Injects a single field.
     */
    private void injectField(Object instance, Field field, String pluginId, Path dataDirectory) {
        boolean isDataDir = field.isAnnotationPresent(DataDirectory.class);
        Object value = resolveDependency(field.getType(), isDataDir, pluginId, dataDirectory);

        if (value == null) {
            LOGGER.warn("Cannot inject field {} in plugin {}: unknown type {}",
                    field.getName(), pluginId, field.getType().getName());
            return;
        }

        try {
            field.setAccessible(true);
            field.set(instance, value);
            LOGGER.debug("Injected {} into {}.{}",
                    field.getType().getSimpleName(), instance.getClass().getSimpleName(), field.getName());
        } catch (IllegalAccessException e) {
            LOGGER.error("Failed to inject field {} in plugin {}", field.getName(), pluginId, e);
        }
    }

    /**
     * Resolves a dependency by type.
     *
     * @param type the requested type
     * @param isDataDirectory whether this is a data directory injection
     * @param pluginId the plugin ID (for logger/datadir)
     * @param dataDirectory the plugin's data directory
     * @return the resolved instance, or null if not resolvable
     */
    private Object resolveDependency(
            Class<?> type,
            boolean isDataDirectory,
            String pluginId,
            Path dataDirectory) {

        // ProxyServer
        if (ProxyServer.class.isAssignableFrom(type)) {
            return proxyServer;
        }

        // EventManager
        if (EventManager.class.isAssignableFrom(type)) {
            return proxyServer.getEventManager();
        }

        // CommandManager
        if (CommandManager.class.isAssignableFrom(type)) {
            return proxyServer.getCommandManager();
        }

        // MessagingService
        if (MessagingService.class.isAssignableFrom(type)) {
            return proxyServer.getMessagingService();
        }

        // Scheduler
        if (Scheduler.class.isAssignableFrom(type)) {
            return proxyServer.getScheduler();
        }

        // Logger
        if (Logger.class.isAssignableFrom(type)) {
            return LoggerFactory.getLogger("Plugin:" + (pluginId != null ? pluginId : "unknown"));
        }

        // Path (data directory)
        if (Path.class.isAssignableFrom(type) && isDataDirectory) {
            return dataDirectory;
        }

        return null;
    }
}

