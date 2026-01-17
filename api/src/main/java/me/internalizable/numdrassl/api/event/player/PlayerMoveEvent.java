package me.internalizable.numdrassl.api.event.player;

import me.internalizable.numdrassl.api.event.Cancellable;
import me.internalizable.numdrassl.api.player.Player;

import javax.annotation.Nonnull;

/**
 * Event fired when a player moves.
 * This includes position changes, rotation changes, and velocity changes.
 */
public class PlayerMoveEvent implements Cancellable {

    private final Player player;
    private double fromX, fromY, fromZ;
    private double toX, toY, toZ;
    private float fromYaw, fromPitch;
    private float toYaw, toPitch;
    private boolean cancelled;

    public PlayerMoveEvent(@Nonnull Player player,
                           double fromX, double fromY, double fromZ,
                           double toX, double toY, double toZ,
                           float fromYaw, float fromPitch,
                           float toYaw, float toPitch) {
        this.player = player;
        this.fromX = fromX;
        this.fromY = fromY;
        this.fromZ = fromZ;
        this.toX = toX;
        this.toY = toY;
        this.toZ = toZ;
        this.fromYaw = fromYaw;
        this.fromPitch = fromPitch;
        this.toYaw = toYaw;
        this.toPitch = toPitch;
        this.cancelled = false;
    }

    @Nonnull
    public Player getPlayer() {
        return player;
    }

    public double getFromX() { return fromX; }
    public double getFromY() { return fromY; }
    public double getFromZ() { return fromZ; }

    public double getToX() { return toX; }
    public double getToY() { return toY; }
    public double getToZ() { return toZ; }

    public void setToX(double x) { this.toX = x; }
    public void setToY(double y) { this.toY = y; }
    public void setToZ(double z) { this.toZ = z; }

    public float getFromYaw() { return fromYaw; }
    public float getFromPitch() { return fromPitch; }

    public float getToYaw() { return toYaw; }
    public float getToPitch() { return toPitch; }

    public void setToYaw(float yaw) { this.toYaw = yaw; }
    public void setToPitch(float pitch) { this.toPitch = pitch; }

    /**
     * Check if the position changed.
     */
    public boolean hasPositionChanged() {
        return fromX != toX || fromY != toY || fromZ != toZ;
    }

    /**
     * Check if the rotation changed.
     */
    public boolean hasRotationChanged() {
        return fromYaw != toYaw || fromPitch != toPitch;
    }

    /**
     * Get the distance moved.
     */
    public double getDistance() {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}

