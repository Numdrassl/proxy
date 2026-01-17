package me.internalizable.numdrassl.plugin.loader;

import me.internalizable.numdrassl.api.ProxyServer;
import me.internalizable.numdrassl.api.plugin.Plugin;
import me.internalizable.numdrassl.api.plugin.PluginContainer;
import me.internalizable.numdrassl.api.plugin.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
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
 * Implementation of {@link PluginManager} that discovers, loads, and manages plugins.
 *
 * <p>Plugin discovery:</p>
 * <ol>
 *   <li>Scans JAR files in the plugins directory</li>
 *   <li>Looks for classes annotated with {@link Plugin}</li>
 *   <li>Sorts plugins by dependencies (topological sort)</li>
 *   <li>Loads plugins in dependency order</li>
 * </ol>
 *
 * <p>Classloader management:</p>
 * <p>Each plugin gets its own {@link URLClassLoader} that is properly closed
 * when the plugin is disabled to prevent memory leaks.</p>
 */
public final class NumdrasslPluginManager implements PluginManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NumdrasslPluginManager.class);

    private final ProxyServer proxyServer;
    private final Path pluginsDirectory;
    private final Map<String, NumdrasslPluginContainer> plugins = new ConcurrentHashMap<>();
    private final List<Path> additionalPaths = new ArrayList<>();

    public NumdrasslPluginManager(@Nonnull ProxyServer proxyServer, @Nonnull Path pluginsDirectory) {
        this.proxyServer = Objects.requireNonNull(proxyServer, "proxyServer");
        this.pluginsDirectory = Objects.requireNonNull(pluginsDirectory, "pluginsDirectory");
    }

    // ==================== Plugin Loading ====================

    /**
     * Scans and loads all plugins from the plugins directory.
     */
    public void loadPlugins() {
        LOGGER.info("Loading plugins from: {}", pluginsDirectory);

        createPluginsDirectory();
        List<Path> jarFiles = findJarFiles();
        List<DiscoveredPlugin> discovered = discoverPlugins(jarFiles);
        List<DiscoveredPlugin> sorted = sortByDependencies(discovered);

        for (DiscoveredPlugin dp : sorted) {
            loadPlugin(dp);
        }

        LOGGER.info("Loaded {} plugin(s)", plugins.size());
    }

    private void createPluginsDirectory() {
        try {
            Files.createDirectories(pluginsDirectory);
        } catch (IOException e) {
            LOGGER.error("Failed to create plugins directory", e);
        }
    }

    private List<Path> findJarFiles() {
        List<Path> jarFiles = new ArrayList<>();

        try (Stream<Path> paths = Files.list(pluginsDirectory)) {
            paths.filter(p -> p.toString().endsWith(".jar"))
                .forEach(jarFiles::add);
        } catch (IOException e) {
            LOGGER.error("Failed to scan plugins directory", e);
        }

        jarFiles.addAll(additionalPaths);
        return jarFiles;
    }

    private List<DiscoveredPlugin> discoverPlugins(List<Path> jarFiles) {
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

        return discovered;
    }

    private DiscoveredPlugin discoverPlugin(Path jarPath) throws Exception {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }

                String className = entry.getName()
                    .replace('/', '.')
                    .replace(".class", "");

                DiscoveredPlugin plugin = tryDiscoverClass(jarPath, className);
                if (plugin != null) {
                    return plugin;
                }
            }
        }
        return null;
    }

    private DiscoveredPlugin tryDiscoverClass(Path jarPath, String className) {
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
        } catch (Exception e) {
            LOGGER.debug("Failed to check class {}: {}", className, e.getMessage());
        }
        return null;
    }

    // ==================== Dependency Sorting ====================

    private List<DiscoveredPlugin> sortByDependencies(List<DiscoveredPlugin> plugins) {
        List<DiscoveredPlugin> sorted = new ArrayList<>();
        Set<String> loaded = new HashSet<>();

        while (sorted.size() < plugins.size()) {
            boolean progress = false;

            for (DiscoveredPlugin dp : plugins) {
                if (loaded.contains(dp.getId())) {
                    continue;
                }

                boolean depsLoaded = dp.description().getDependencies().stream()
                    .filter(d -> !d.isOptional())
                    .allMatch(d -> loaded.contains(d.getId()));

                if (depsLoaded) {
                    sorted.add(dp);
                    loaded.add(dp.getId());
                    progress = true;
                }
            }

            if (!progress) {
                addRemainingPlugins(plugins, sorted, loaded);
                break;
            }
        }

        return sorted;
    }

    private void addRemainingPlugins(
            List<DiscoveredPlugin> plugins,
            List<DiscoveredPlugin> sorted,
            Set<String> loaded) {

        for (DiscoveredPlugin dp : plugins) {
            if (!loaded.contains(dp.getId())) {
                LOGGER.warn("Plugin {} has unresolved dependencies, loading anyway", dp.getId());
                sorted.add(dp);
                loaded.add(dp.getId());
            }
        }
    }

    // ==================== Plugin Instance Loading ====================

    private void loadPlugin(DiscoveredPlugin dp) {
        try {
            doLoadPlugin(dp);
        } catch (Exception e) {
            LOGGER.error("Failed to load plugin: {}", dp.getId(), e);
        }
    }

    private void doLoadPlugin(DiscoveredPlugin dp) throws Exception {
        LOGGER.info("Loading plugin: {} v{}",
            dp.description().getName(),
            dp.description().getVersion().orElse("unknown"));

        URLClassLoader classLoader = new URLClassLoader(
            new URL[]{dp.jarPath().toUri().toURL()},
            getClass().getClassLoader()
        );

        try {
            Class<?> mainClass = classLoader.loadClass(dp.mainClass());
            Object instance = mainClass.getDeclaredConstructor().newInstance();

            Path dataDir = pluginsDirectory.resolve(dp.getId());
            Files.createDirectories(dataDir);

            NumdrasslPluginContainer container = new NumdrasslPluginContainer(
                dp.description(), instance, dataDir, classLoader
            );

            plugins.put(dp.getId(), container);
            proxyServer.getEventManager().register(instance, instance);

            LOGGER.info("Loaded plugin: {}", dp.getId());
        } catch (Exception e) {
            // Close classloader if loading fails to prevent leak
            closeClassLoaderSafely(classLoader);
            throw e;
        }
    }

    // ==================== Plugin Lifecycle ====================

    /**
     * Enables all loaded plugins.
     */
    public void enablePlugins() {
        LOGGER.info("Enabling {} plugin(s)...", plugins.size());
        // Plugins receive ProxyInitializeEvent via @Subscribe
    }

    /**
     * Disables and unloads all plugins.
     */
    public void disablePlugins() {
        LOGGER.info("Disabling {} plugin(s)...", plugins.size());

        for (NumdrasslPluginContainer container : plugins.values()) {
            disablePlugin(container);
        }

        plugins.clear();
    }

    private void disablePlugin(NumdrasslPluginContainer container) {
        String id = container.getDescription().getId();

        try {
            container.getInstance().ifPresent(instance ->
                proxyServer.getEventManager().unregisterAll(instance)
            );
            container.close();
            LOGGER.info("Disabled plugin: {}", id);
        } catch (Exception e) {
            LOGGER.error("Error disabling plugin: {}", id, e);
        }
    }

    private void closeClassLoaderSafely(URLClassLoader classLoader) {
        try {
            classLoader.close();
        } catch (IOException e) {
            LOGGER.debug("Failed to close classloader", e);
        }
    }

    // ==================== PluginManager API ====================

    @Override
    @Nonnull
    public Optional<PluginContainer> getPlugin(@Nonnull String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(plugins.get(id.toLowerCase()));
    }

    @Override
    @Nonnull
    public Collection<PluginContainer> getPlugins() {
        return Collections.unmodifiableCollection(plugins.values());
    }

    @Override
    public boolean isLoaded(@Nonnull String id) {
        Objects.requireNonNull(id, "id");
        return plugins.containsKey(id.toLowerCase());
    }

    @Override
    @Nonnull
    public Path getPluginsDirectory() {
        return pluginsDirectory;
    }

    @Override
    public void addPluginPath(@Nonnull Path path) {
        Objects.requireNonNull(path, "path");
        additionalPaths.add(path);
    }
}

