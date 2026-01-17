/**
 * Player identity information.
 *
 * <p>This package contains immutable value objects representing player identity
 * information extracted from the Hytale Connect packet.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.session.identity.PlayerIdentity} - Immutable
 *       record-like class holding UUID, username, protocol hash, and identity token.
 *       Created from a Connect packet and safely shareable across threads.</li>
 * </ul>
 *
 * <h2>Identity Lifecycle</h2>
 * <pre>
 * 1. Session created with PlayerIdentity.unknown()
 * 2. Connect packet received
 * 3. PlayerIdentity.fromConnect(packet) creates populated identity
 * 4. Identity stored atomically in ProxySession
 * 5. Identity remains immutable for session lifetime
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>{@code PlayerIdentity} is immutable and therefore inherently thread-safe.
 * It can be freely shared between threads without synchronization.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Check if identity is known
 * PlayerIdentity identity = session.getIdentity();
 * if (identity.isKnown()) {
 *     System.out.println("Player: " + identity.username());
 *     System.out.println("UUID: " + identity.uuid());
 * }
 * }</pre>
 *
 * @see me.internalizable.numdrassl.session.identity.PlayerIdentity
 */
package me.internalizable.numdrassl.session.identity;

