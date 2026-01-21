package me.internalizable.numdrassl.api.messaging.channel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Types of cluster-wide broadcast messages.
 *
 * <p>Using an enum instead of raw strings ensures type safety and
 * prevents typos in broadcast type handling.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Publishing
 * broadcastHandler.broadcast(BroadcastType.ANNOUNCEMENT, "Server restarting in 5 minutes");
 *
 * // Handling
 * switch (message.getBroadcastType()) {
 *     case ANNOUNCEMENT -> handleAnnouncement(message);
 *     case ALERT -> handleAlert(message);
 *     // ...
 * }
 * }</pre>
 */
public enum BroadcastType {

    /**
     * General announcement to all players.
     */
    ANNOUNCEMENT("announcement"),

    /**
     * High-priority alert message.
     */
    ALERT("alert"),

    /**
     * Maintenance notification.
     */
    MAINTENANCE("maintenance"),

    /**
     * Custom/plugin-defined broadcast type.
     * Used for extensibility when plugins need custom broadcast types.
     */
    CUSTOM("custom");

    private static final Map<String, BroadcastType> BY_ID = Arrays.stream(values())
            .collect(Collectors.toMap(BroadcastType::getId, Function.identity()));

    private final String id;

    BroadcastType(String id) {
        this.id = id;
    }

    /**
     * Get the string identifier for this broadcast type.
     *
     * @return the type ID
     */
    @Nonnull
    public String getId() {
        return id;
    }

    /**
     * Look up a broadcast type by its string ID.
     *
     * @param id the type ID
     * @return the broadcast type, or null if not found
     */
    @Nullable
    public static BroadcastType fromId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        return BY_ID.get(id.toLowerCase());
    }

    /**
     * Look up a broadcast type by its string ID, with a default fallback.
     *
     * @param id the type ID
     * @param defaultType the default type if not found
     * @return the broadcast type, or the default if not found
     */
    @Nonnull
    public static BroadcastType fromId(@Nullable String id, @Nonnull BroadcastType defaultType) {
        BroadcastType type = fromId(id);
        return type != null ? type : defaultType;
    }

    @Override
    public String toString() {
        return id;
    }
}

