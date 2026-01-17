package me.internalizable.numdrassl.api.event.player;

import me.internalizable.numdrassl.api.event.Cancellable;
import me.internalizable.numdrassl.api.player.Player;

import javax.annotation.Nonnull;

/**
 * Event fired when a player attempts to place a block.
 */
public class PlayerBlockPlaceEvent implements Cancellable {

    private final Player player;
    private final int blockX, blockY, blockZ;
    private final int blockId;
    private boolean cancelled;

    public PlayerBlockPlaceEvent(@Nonnull Player player, int blockX, int blockY, int blockZ, int blockId) {
        this.player = player;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.blockId = blockId;
        this.cancelled = false;
    }

    @Nonnull
    public Player getPlayer() {
        return player;
    }

    public int getBlockX() { return blockX; }
    public int getBlockY() { return blockY; }
    public int getBlockZ() { return blockZ; }

    public int getBlockId() { return blockId; }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}

