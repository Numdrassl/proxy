package me.internalizable.numdrassl.plugin.loader;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A custom classloader for plugins that allows dynamic addition of URLs to the classpath.
 *
 * <p>Unlike the standard {@link URLClassLoader}, this class exposes {@link #addPath(Path)}
 * as a public method, avoiding the need for reflection to add JARs at runtime.</p>
 *
 * <p>This is essential for plugins like LuckPerms that download and load dependencies
 * at runtime, which would otherwise fail on Java 9+ due to module system restrictions
 * on reflective access to {@code URLClassLoader.addURL()}.</p>
 *
 * <p>The classloader uses a parent-first delegation model by default, but plugins
 * can override classes by placing them in the plugin JAR.</p>
 */
public final class PluginClassLoader extends URLClassLoader {

    static {
        // Enables parallel class loading for better performance
        ClassLoader.registerAsParallelCapable();
    }

    private volatile boolean closed = false;

    /**
     * Creates a new plugin classloader with the specified URLs.
     *
     * @param urls the initial URLs to load from
     * @param parent the parent classloader
     */
    public PluginClassLoader(@Nonnull URL[] urls, @Nonnull ClassLoader parent) {
        super(Objects.requireNonNull(urls, "urls"), Objects.requireNonNull(parent, "parent"));
    }

    /**
     * Adds a JAR file to this classloader's classpath.
     *
     * @param path the path to the JAR file
     * @throws IllegalStateException if this classloader has been closed
     */
    public void addPath(@Nonnull Path path) {
        Objects.requireNonNull(path, "path");
        if (closed) {
            throw new IllegalStateException("ClassLoader has been closed");
        }
        try {
            addURL(path.toUri().toURL());
        } catch (java.net.MalformedURLException e) {
            throw new IllegalArgumentException("Invalid path: " + path, e);
        }
    }

    /**
     * Adds a URL to this classloader's classpath.
     *
     * <p>This method is made public to allow dynamic classpath additions
     * without needing reflection.</p>
     *
     * @param url the URL to add
     * @throws IllegalStateException if this classloader has been closed
     */
    @Override
    public void addURL(@Nonnull URL url) {
        Objects.requireNonNull(url, "url");
        if (closed) {
            throw new IllegalStateException("ClassLoader has been closed");
        }
        super.addURL(url);
    }

    /**
     * Closes this classloader and releases resources.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        closed = true;
        super.close();
    }

    /**
     * Checks if this classloader has been closed.
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return closed;
    }
}

