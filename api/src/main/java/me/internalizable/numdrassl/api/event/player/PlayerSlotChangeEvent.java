package me.internalizable.numdrassl.api.event.player;

import me.internalizable.numdrassl.api.player.Player;

import javax.annotation.Nonnull;

/**
 * Event fired when a player changes their selected hotbar slot.
 */
public class PlayerSlotChangeEvent {

    private final Player player;
    private final int previousSlot;
    private final int newSlot;

    public PlayerSlotChangeEvent(@Nonnull Player player, int previousSlot, int newSlot) {
        this.player = player;
        this.previousSlot = previousSlot;
        this.newSlot = newSlot;
    }

    @Nonnull
    public Player getPlayer() {
        return player;
    }

    public int getPreviousSlot() {
        return previousSlot;
    }

    public int getNewSlot() {
        return newSlot;
    }
}

