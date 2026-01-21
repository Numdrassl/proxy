/**
 * Cluster management API for distributed Numdrassl deployments.
 *
 * <p>This package provides abstractions for managing a cluster of proxy instances,
 * including:</p>
 * <ul>
 *   <li><b>Proxy discovery</b> - Track which proxies are online</li>
 *   <li><b>Health monitoring</b> - Detect proxy failures via heartbeats</li>
 *   <li><b>Global state</b> - Aggregate player counts, server lists</li>
 *   <li><b>Load balancing</b> - Find the best proxy for a region</li>
 * </ul>
 *
 * <h2>Cluster Topology</h2>
 * <p>Each proxy instance registers itself with the cluster on startup and
 * sends periodic heartbeats. Other proxies receive these heartbeats and
 * maintain a local view of the cluster state.</p>
 *
 * <h2>Failure Detection</h2>
 * <p>If a proxy stops sending heartbeats, it is considered failed after a
 * configurable timeout (default: 30 seconds). Failed proxies are removed
 * from the cluster view and a {@link me.internalizable.numdrassl.api.event.cluster.ProxyLeaveClusterEvent}
 * is fired.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ClusterManager cluster = proxy.getClusterManager();
 *
 * // Get all online proxies
 * Collection<ProxyInfo> proxies = cluster.getOnlineProxies();
 *
 * // Get global player count
 * int globalCount = cluster.getGlobalPlayerCount();
 *
 * // Find proxies in a region
 * Optional<ProxyInfo> euProxy = cluster.getProxiesInRegion("eu-west")
 *     .stream()
 *     .min(Comparator.comparingInt(ProxyInfo::playerCount));
 * }</pre>
 *
 * @see me.internalizable.numdrassl.api.cluster.ClusterManager
 * @see me.internalizable.numdrassl.api.cluster.ProxyInfo
 */
package me.internalizable.numdrassl.api.cluster;

