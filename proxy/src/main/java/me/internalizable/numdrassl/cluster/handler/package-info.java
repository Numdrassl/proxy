/**
 * System channel message handlers for cross-proxy communication.
 *
 * <p>This package contains handlers that process messages received on
 * system channels (HEARTBEAT, CHAT, TRANSFER, BROADCAST, PLAYER_COUNT).</p>
 *
 * <h2>Handlers</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.cluster.handler.BroadcastHandler} - Handles cluster-wide broadcasts</li>
 *   <li>{@link me.internalizable.numdrassl.cluster.handler.ChatHandler} - Handles cross-proxy chat messages</li>
 *   <li>{@link me.internalizable.numdrassl.cluster.handler.TransferHandler} - Handles player transfer coordination</li>
 *   <li>{@link me.internalizable.numdrassl.cluster.handler.PlayerCountHandler} - Handles player count synchronization</li>
 * </ul>
 *
 * <p>Note: Heartbeat handling is in {@link me.internalizable.numdrassl.cluster.ProxyRegistry}.</p>
 */
package me.internalizable.numdrassl.cluster.handler;

