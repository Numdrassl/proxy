/**
 * Plugin loading and lifecycle management.
 *
 * <p>This package handles the discovery, loading, enabling, and disabling of plugins.
 * Plugins are JAR files containing classes annotated with {@link me.internalizable.numdrassl.api.plugin.Plugin}.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.plugin.loader.NumdrasslPluginManager} - Main
 *       plugin manager that scans directories, loads JARs, and manages lifecycle.</li>
 *   <li>{@link me.internalizable.numdrassl.plugin.loader.NumdrasslPluginContainer} - Holds
 *       a loaded plugin instance with its metadata and classloader.</li>
 *   <li>{@link me.internalizable.numdrassl.plugin.loader.NumdrasslPluginDescription} - Plugin
 *       metadata parsed from the {@code @Plugin} annotation.</li>
 *   <li>{@link me.internalizable.numdrassl.plugin.loader.DiscoveredPlugin} - Record representing
 *       a plugin found during JAR scanning.</li>
 * </ul>
 *
 * <h2>Plugin Discovery Process</h2>
 * <pre>
 * 1. Scan plugins/ directory for JAR files
 * 2. For each JAR, scan classes for @Plugin annotation
 * 3. Build PluginDescription from annotation
 * 4. Sort plugins by dependencies (topological sort)
 * 5. Load plugins in dependency order
 * 6. Create isolated URLClassLoader per plugin
 * 7. Register plugin as event listener
 * </pre>
 *
 * <h2>Classloader Management</h2>
 * <p>Each plugin gets its own {@link java.net.URLClassLoader} to provide isolation.
 * When a plugin is disabled, its classloader is closed to prevent memory leaks.
 * If loading fails partway through, the classloader is also cleaned up.</p>
 *
 * <h2>Dependency Resolution</h2>
 * <p>Dependencies declared in {@code @Plugin(dependencies = {...})} are hard dependencies.
 * Dependencies in {@code @Plugin(softDependencies = {...})} are optional.
 * Plugins with unresolved hard dependencies will still load with a warning.</p>
 *
 * @see me.internalizable.numdrassl.api.plugin.Plugin
 * @see me.internalizable.numdrassl.api.plugin.PluginManager
 */
package me.internalizable.numdrassl.plugin.loader;

