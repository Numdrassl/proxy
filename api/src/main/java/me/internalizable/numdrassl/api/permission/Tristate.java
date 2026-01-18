package me.internalizable.numdrassl.api.permission;

import javax.annotation.Nonnull;

/**
 * Represents a three-state permission value.
 *
 * <p>Unlike a simple boolean, Tristate allows distinguishing between
 * an explicitly granted permission, an explicitly denied permission,
 * and an undefined/inherited permission.</p>
 *
 * <ul>
 *   <li>{@link #TRUE} - Permission is explicitly granted</li>
 *   <li>{@link #FALSE} - Permission is explicitly denied</li>
 *   <li>{@link #UNDEFINED} - Permission is not set, defer to default or parent</li>
 * </ul>
 */
public enum Tristate {

    /**
     * Permission is explicitly granted.
     */
    TRUE(true),

    /**
     * Permission is explicitly denied.
     */
    FALSE(false),

    /**
     * Permission is not set, defer to default behavior.
     */
    UNDEFINED(false);

    private final boolean booleanValue;

    Tristate(boolean booleanValue) {
        this.booleanValue = booleanValue;
    }

    /**
     * Gets the boolean value of this tristate.
     *
     * <p>{@link #UNDEFINED} returns {@code false}.</p>
     *
     * @return the boolean value
     */
    public boolean asBoolean() {
        return booleanValue;
    }

    /**
     * Converts a boolean to a Tristate.
     *
     * @param value the boolean value
     * @return {@link #TRUE} if true, {@link #FALSE} if false
     */
    @Nonnull
    public static Tristate fromBoolean(boolean value) {
        return value ? TRUE : FALSE;
    }

    /**
     * Converts a nullable Boolean to a Tristate.
     *
     * @param value the boolean value, or null for undefined
     * @return the corresponding Tristate
     */
    @Nonnull
    public static Tristate fromNullableBoolean(Boolean value) {
        if (value == null) {
            return UNDEFINED;
        }
        return value ? TRUE : FALSE;
    }
}

