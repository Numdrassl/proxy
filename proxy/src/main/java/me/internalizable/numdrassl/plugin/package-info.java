/**
 * Plugin API implementation and proxy server wrapper.
 *
 * <p>This package provides the implementation of the public API that plugins use
 * to interact with the proxy. The main entry point is {@link NumdrasslProxy}.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.plugin.NumdrasslProxy} - Implementation of
 *       {@link me.internalizable.numdrassl.api.ProxyServer}. Bridges internal components
 *       with the public API.</li>
 * </ul>
 *
 * <h2>Subpackages</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.plugin.bridge} - Event bridging between
 *       internal packet system and API event system.</li>
 *   <li>{@link me.internalizable.numdrassl.plugin.loader} - Plugin discovery, loading,
 *       and lifecycle management.</li>
 *   <li>{@link me.internalizable.numdrassl.plugin.player} - Player API implementation.</li>
 *   <li>{@link me.internalizable.numdrassl.plugin.server} - Server API implementation.</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <pre>
 *              Plugin Code
 *                  │
 *                  ▼
 *          ┌──────────────┐
 *          │  Public API  │  (api/ module)
 *          └──────┬───────┘
 *                 │
 *                 ▼
 *          ┌──────────────┐
 *          │NumdrasslProxy│  (this package)
 *          └──────┬───────┘
 *                 │
 *                 ▼
 *          ┌──────────────┐
 *          │  ProxyCore   │  (server package)
 *          └──────────────┘
 * </pre>
 *
 * @see me.internalizable.numdrassl.api.ProxyServer
 * @see me.internalizable.numdrassl.server.ProxyCore
 */
package me.internalizable.numdrassl.plugin;

