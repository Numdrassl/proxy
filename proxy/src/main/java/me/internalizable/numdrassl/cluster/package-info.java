/**
 * Cluster management for distributed proxy deployments.
 *
 * <p>This package provides functionality for coordinating multiple proxy
 * instances via Redis pub/sub messaging.</p>
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link ClusterManager} - Main coordinator for all cluster features</li>
 *   <li>{@link ProxyRegistry} - Tracks online proxies via heartbeats</li>
 *   <li>{@link HeartbeatPublisher} - Publishes this proxy's heartbeat</li>
 * </ul>
 *
 * <h2>System Channel Handlers</h2>
 * <p>Located in the {@link me.internalizable.numdrassl.cluster.handler} subpackage:</p>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.cluster.handler.BroadcastHandler} - Cluster broadcasts</li>
 *   <li>{@link me.internalizable.numdrassl.cluster.handler.ChatHandler} - Cross-proxy chat</li>
 *   <li>{@link me.internalizable.numdrassl.cluster.handler.PlayerCountHandler} - Player count sync</li>
 *   <li>{@link me.internalizable.numdrassl.cluster.handler.TransferHandler} - Player transfers</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // The ClusterManager is initialized by ProxyCore
 * ClusterManager cluster = proxyCore.getClusterManager();
 *
 * // Send a broadcast to all proxies
 * cluster.broadcast("announcement", "Server restart in 5 minutes!");
 *
 * // Get global player count
 * int total = cluster.getGlobalPlayerCount();
 *
 * // Send cross-proxy private message
 * cluster.getChatHandler().sendPrivateMessage(targetUuid, targetName, "Hello!", senderName, senderUuid);
 * }</pre>
 */
package me.internalizable.numdrassl.cluster;

