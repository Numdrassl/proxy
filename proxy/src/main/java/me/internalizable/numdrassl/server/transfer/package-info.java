/**
 * Player server transfer functionality.
 *
 * <p>This package handles transferring players between backend servers. When a player
 * needs to switch servers (e.g., joining a game from the lobby), this package manages
 * the referral system that routes them correctly on reconnection.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.server.transfer.PlayerTransfer} - API for
 *       initiating player transfers. Sends {@code ClientReferral} packets to clients.</li>
 *   <li>{@link me.internalizable.numdrassl.server.transfer.ReferralManager} - Tracks
 *       pending transfers and routes reconnecting players to their target backend.</li>
 *   <li>{@link me.internalizable.numdrassl.server.transfer.PendingReferral} - Immutable
 *       record representing a pending transfer with expiration tracking.</li>
 * </ul>
 *
 * <h2>Transfer Flow</h2>
 * <pre>
 * 1. Plugin calls PlayerTransfer.transfer(session, targetServer)
 * 2. ReferralManager creates and stores a PendingReferral
 * 3. ClientReferral packet sent to player with referral data
 * 4. Client disconnects and reconnects to the proxy
 * 5. On Connect, ReferralManager.consumeReferral() returns target backend
 * 6. Player is connected to the target backend instead of default
 * </pre>
 *
 * <h2>Referral Expiration</h2>
 * <p>Referrals expire after 30 seconds by default. This prevents stale referrals
 * from routing players unexpectedly if they reconnect much later.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * PlayerTransfer transfer = new PlayerTransfer(proxyCore);
 * transfer.transfer(session, "game-server-1")
 *     .thenAccept(result -> {
 *         if (result.isSuccess()) {
 *             // Transfer initiated
 *         } else {
 *             // Handle failure: result.getMessage()
 *         }
 *     });
 * }</pre>
 *
 * @see me.internalizable.numdrassl.server.transfer.PlayerTransfer
 * @see me.internalizable.numdrassl.server.transfer.ReferralManager
 */
package me.internalizable.numdrassl.server.transfer;

