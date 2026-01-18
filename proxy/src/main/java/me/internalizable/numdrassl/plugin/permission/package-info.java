/**
 * Permission system implementation for the proxy.
 *
 * <h2>Components</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.plugin.permission.NumdrasslPermissionManager} -
 *       Central permission management implementation</li>
 *   <li>{@link me.internalizable.numdrassl.plugin.permission.FilePermissionProvider} -
 *       Default file-based permission storage using YAML</li>
 * </ul>
 *
 * <h2>Default Permission Provider</h2>
 * <p>Numdrassl includes a built-in file-based permission provider that stores
 * permissions in YAML files. The file structure is:</p>
 * <pre>
 * data/permissions/
 *   players/
 *     {uuid}.yml       - Individual player permissions
 *   groups/
 *     default.yml      - Default group (all players inherit)
 *     admin.yml        - Custom groups
 *   player-groups.yml  - Maps players to groups
 * </pre>
 *
 * <h2>Player Permission File Format (players/{uuid}.yml)</h2>
 * <pre>
 * permissions:
 *   - numdrassl.command.server
 *   - numdrassl.command.help
 *   - -numdrassl.command.stop  # Negated (denied)
 * </pre>
 *
 * <h2>Group File Format (groups/{name}.yml)</h2>
 * <pre>
 * name: admin
 * default: false
 * permissions:
 *   - numdrassl.command.*
 *   - numdrassl.admin
 * </pre>
 *
 * <h2>Player Groups File (player-groups.yml)</h2>
 * <pre>
 * players:
 *   550e8400-e29b-41d4-a716-446655440000:
 *     - admin
 *     - vip
 * </pre>
 *
 * <h2>Replacing the Default Provider</h2>
 * <p>External permission plugins (like LuckPerms) can replace the default provider:</p>
 * <pre>{@code
 * Numdrassl.getProxy().getPermissionManager().setProvider(new MyPermissionProvider());
 * }</pre>
 *
 * @see me.internalizable.numdrassl.api.permission.PermissionManager
 */
package me.internalizable.numdrassl.plugin.permission;

