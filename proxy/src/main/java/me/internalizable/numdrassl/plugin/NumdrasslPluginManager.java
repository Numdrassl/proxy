package me.internalizable.numdrassl.plugin;

import me.internalizable.numdrassl.api.plugin.Plugin;
import me.internalizable.numdrassl.api.plugin.PluginContainer;
import me.internalizable.numdrassl.api.plugin.PluginDescription;
import me.internalizable.numdrassl.api.plugin.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Implementation of PluginManager that scans for and loads plugins.
 */
public class NumdrasslPluginManager implements PluginManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NumdrasslPluginManager.class);

    private final Path pluginsDirectory;
    private final Map<String, NumdrasslPluginContainer> plugins = new ConcurrentHashMap<>();
    private final List<Path> additionalPaths = new ArrayList<>();
    private final me.internalizable.numdrassl.api.ProxyServer proxyServer;

    public NumdrasslPluginManager(@Nonnull me.internalizable.numdrassl.api.ProxyServer proxyServer, @Nonnull Path pluginsDirectory) {
        this.proxyServer = proxyServer;
        this.pluginsDirectory = pluginsDirectory;
    }

    /**
     * Scan and load all plugins from the plugins directory.
     */
    public void loadPlugins() {
        LOGGER.info("Loading plugins from: {}", pluginsDirectory);

        // Create plugins directory if it doesn't exist
        try {
            Files.createDirectories(pluginsDirectory);
        } catch (IOException e) {
            LOGGER.error("Failed to create plugins directory", e);
            return;
        }

        // Find all JAR files
        List<Path> jarFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.list(pluginsDirectory)) {
            paths.filter(p -> p.toString().endsWith(".jar"))
                 .forEach(jarFiles::add);
        } catch (IOException e) {
            LOGGER.error("Failed to scan plugins directory", e);
            return;
        }

        // Add additional paths
        jarFiles.addAll(additionalPaths);

        // Discover plugins in JAR files
        List<DiscoveredPlugin> discovered = new ArrayList<>();
        for (Path jarPath : jarFiles) {
            try {
                DiscoveredPlugin plugin = discoverPlugin(jarPath);
                if (plugin != null) {
                    discovered.add(plugin);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to discover plugin in: {}", jarPath, e);
            }
        }

        // Sort by dependencies
        discovered = sortByDependencies(discovered);

        // Load plugins
        for (DiscoveredPlugin dp : discovered) {
            try {
                loadPlugin(dp);
            } catch (Exception e) {
                LOGGER.error("Failed to load plugin: {}", dp.description.getId(), e);
            }
        }

        LOGGER.info("Loaded {} plugin(s)", plugins.size());
    }

    private DiscoveredPlugin discoverPlugin(Path jarPath) throws Exception {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName()
                        .replace('/', '.')
                        .replace(".class", "");

                    // Create a temporary classloader to check for @Plugin annotation
                    try (URLClassLoader tempLoader = new URLClassLoader(
                            new URL[]{jarPath.toUri().toURL()},
                            getClass().getClassLoader())) {

                        Class<?> clazz = tempLoader.loadClass(className);
                        Plugin annotation = clazz.getAnnotation(Plugin.class);
                        if (annotation != null) {
                            NumdrasslPluginDescription description = new NumdrasslPluginDescription(annotation, className);
                            return new DiscoveredPlugin(jarPath, description, className);
                        }
                    } catch (ClassNotFoundException | NoClassDefFoundError e) {
                        // Skip classes that can't be loaded
                    }
                }
            }
        }
        return null;
    }

    private List<DiscoveredPlugin> sortByDependencies(List<DiscoveredPlugin> plugins) {
        // Simple topological sort based on dependencies
        List<DiscoveredPlugin> sorted = new ArrayList<>();
        Set<String> loaded = new HashSet<>();

        while (sorted.size() < plugins.size()) {
            boolean progress = false;
            for (DiscoveredPlugin dp : plugins) {
                if (loaded.contains(dp.description.getId())) {
                    continue;
                }

                boolean depsLoaded = dp.description.getDependencies().stream()
                    .filter(d -> !d.isOptional())
                    .allMatch(d -> loaded.contains(d.getId()));

                if (depsLoaded) {
                    sorted.add(dp);
                    loaded.add(dp.description.getId());
                    progress = true;
                }
            }

            if (!progress) {
                // Add remaining plugins (may have missing dependencies)
                for (DiscoveredPlugin dp : plugins) {
                    if (!loaded.contains(dp.description.getId())) {
                        LOGGER.warn("Plugin {} has unresolved dependencies, loading anyway", dp.description.getId());
                        sorted.add(dp);
                        loaded.add(dp.description.getId());
                    }
                }
                break;
            }
        }

        return sorted;
    }

    private void loadPlugin(DiscoveredPlugin dp) throws Exception {
        LOGGER.info("Loading plugin: {} v{}", dp.description.getName(),
            dp.description.getVersion().orElse("unknown"));

        // Create plugin classloader
        URLClassLoader classLoader = new URLClassLoader(
            new URL[]{dp.jarPath.toUri().toURL()},
            getClass().getClassLoader()
        );

        // Load the main class
        Class<?> mainClass = classLoader.loadClass(dp.mainClass);
        Object instance = mainClass.getDeclaredConstructor().newInstance();

        // Create data directory
        Path dataDir = pluginsDirectory.resolve(dp.description.getId());
        Files.createDirectories(dataDir);

        // Create container
        NumdrasslPluginContainer container = new NumdrasslPluginContainer(
            dp.description, instance, dataDir, classLoader
        );

        plugins.put(dp.description.getId(), container);

        // Register the plugin instance as an event listener (for @Subscribe methods)
        proxyServer.getEventManager().register(instance, instance);

        LOGGER.info("Loaded plugin: {}", dp.description.getId());
    }

    /**
     * Enable all loaded plugins.
     */
    public void enablePlugins() {
        LOGGER.info("Enabling {} plugin(s)...", plugins.size());
        // Plugins are enabled when their @Subscribe methods receive ProxyInitializeEvent
    }

    /**
     * Disable and unload all plugins.
     */
    public void disablePlugins() {
        LOGGER.info("Disabling {} plugin(s)...", plugins.size());
        for (NumdrasslPluginContainer container : plugins.values()) {
            try {
                proxyServer.getEventManager().unregisterAll(container.getInstance().orElse(null));
                container.close();
                LOGGER.info("Disabled plugin: {}", container.getDescription().getId());
            } catch (Exception e) {
                LOGGER.error("Error disabling plugin: {}", container.getDescription().getId(), e);
            }
        }
        plugins.clear();
    }

    @Override
    @Nonnull
    public Optional<PluginContainer> getPlugin(@Nonnull String id) {
        return Optional.ofNullable(plugins.get(id.toLowerCase()));
    }

    @Override
    @Nonnull
    public Collection<PluginContainer> getPlugins() {
        return Collections.unmodifiableCollection(plugins.values());
    }

    @Override
    public boolean isLoaded(@Nonnull String id) {
        return plugins.containsKey(id.toLowerCase());
    }

    @Override
    @Nonnull
    public Path getPluginsDirectory() {
        return pluginsDirectory;
    }

    @Override
    public void addPluginPath(@Nonnull Path path) {
        additionalPaths.add(path);
    }

    private static class DiscoveredPlugin {
        final Path jarPath;
        final NumdrasslPluginDescription description;
        final String mainClass;

        DiscoveredPlugin(Path jarPath, NumdrasslPluginDescription description, String mainClass) {
            this.jarPath = jarPath;
            this.description = description;
            this.mainClass = mainClass;
        }
    }
}

