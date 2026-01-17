/**
 * Public API for the Numdrassl proxy.
 *
 * <p>This package provides static utilities and entry points for interacting
 * with the proxy. For plugin development, prefer using the instance-based
 * interfaces in the {@code api/} module.</p>
 *
 * <h2>Entry Points</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.api.ProxyAPI} - Static utility methods</li>
 *   <li>{@link me.internalizable.numdrassl.api.Numdrassl} - Main entry point for plugins</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Preferred: Use instance-based API via Numdrassl entry point
 * ProxyServer server = Numdrassl.server();
 *
 * // Alternative: Static utility methods
 * ProxyAPI.broadcast(packet);
 * }</pre>
 *
 * @see me.internalizable.numdrassl.api.ProxyServer
 */
package me.internalizable.numdrassl.api;

