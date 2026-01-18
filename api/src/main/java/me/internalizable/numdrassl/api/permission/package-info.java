/**
 * Permission system API for Numdrassl.
 *
 * <p>This package provides a flexible permission system that allows external
 * permission plugins (like LuckPerms) to integrate with the proxy.</p>
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.api.permission.Tristate} - Three-state permission value (TRUE/FALSE/UNDEFINED)</li>
 *   <li>{@link me.internalizable.numdrassl.api.permission.PermissionSubject} - Interface for objects that can have permissions</li>
 *   <li>{@link me.internalizable.numdrassl.api.permission.PermissionFunction} - Functional interface for permission checking</li>
 *   <li>{@link me.internalizable.numdrassl.api.permission.PermissionProvider} - Interface for permission plugins</li>
 *   <li>{@link me.internalizable.numdrassl.api.permission.PermissionManager} - Central permission management</li>
 * </ul>
 *
 * <h2>For Plugin Developers</h2>
 * <p>To check permissions on a player:</p>
 * <pre>{@code
 * if (player.hasPermission("myplugin.command.example")) {
 *     // Player has permission
 * }
 *
 * // For more control over undefined permissions:
 * Tristate state = player.getPermissionValue("myplugin.admin");
 * switch (state) {
 *     case TRUE -> // Explicitly granted
 *     case FALSE -> // Explicitly denied
 *     case UNDEFINED -> // Use default behavior
 * }
 * }</pre>
 *
 * <h2>For Permission Plugin Developers</h2>
 * <p>To integrate a permission plugin (like LuckPerms):</p>
 * <pre>{@code
 * public class MyPermissionProvider implements PermissionProvider {
 *     @Override
 *     public PermissionFunction createFunction(Player player) {
 *         return permission -> {
 *             // Look up permission in your system
 *             Boolean result = myPermissionSystem.check(player.getUniqueId(), permission);
 *             return Tristate.fromNullableBoolean(result);
 *         };
 *     }
 * }
 *
 * // Register in your plugin's onEnable:
 * Numdrassl.getProxy().getPermissionManager().setProvider(new MyPermissionProvider());
 * }</pre>
 *
 * @see me.internalizable.numdrassl.api.permission.PermissionManager
 * @see me.internalizable.numdrassl.api.permission.PermissionSubject
 */
package me.internalizable.numdrassl.api.permission;

