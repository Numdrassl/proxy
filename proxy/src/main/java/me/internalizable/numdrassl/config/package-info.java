/**
 * Configuration classes for the Numdrassl proxy.
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.config.ProxyConfig} -
 *       Main proxy configuration (bind address, port, backends, etc.)</li>
 *   <li>{@link me.internalizable.numdrassl.config.BackendServer} -
 *       Backend server definition (name, host, port, default flag)</li>
 * </ul>
 *
 * <h2>Configuration Loading</h2>
 * <p>Configuration is loaded from YAML files at startup. The default
 * location is {@code config.yml} in the working directory.</p>
 */
package me.internalizable.numdrassl.config;

