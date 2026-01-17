/**
 * Command system for the Numdrassl proxy.
 *
 * <p>Provides command registration, execution, and management for both
 * plugins and the console.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.command.NumdrasslCommandManager} -
 *       Main command manager implementing the API</li>
 *   <li>{@link me.internalizable.numdrassl.command.CommandEventListener} -
 *       Listens for chat messages and dispatches commands</li>
 *   <li>{@link me.internalizable.numdrassl.command.ConsoleCommandSource} -
 *       Command source for console-based command execution</li>
 * </ul>
 *
 * <h2>Subpackages</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.command.builtin} - Built-in proxy commands</li>
 * </ul>
 *
 * @see me.internalizable.numdrassl.api.command.CommandManager
 */
package me.internalizable.numdrassl.command;

