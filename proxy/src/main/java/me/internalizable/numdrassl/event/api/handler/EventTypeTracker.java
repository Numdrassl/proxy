package me.internalizable.numdrassl.event.api.handler;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Objects;

/**
 * Tracks event type hierarchies for proper event inheritance.
 */
public final class EventTypeTracker {

    private final Multimap<Class<?>, Class<?>> eventToSuperclasses = HashMultimap.create();

    /**
     * Gets all event types that the given event type can be dispatched as.
     */
    @Nonnull
    public Collection<Class<?>> getFriendsOf(@Nonnull Class<?> eventType) {
        Objects.requireNonNull(eventType, "eventType");

        if (!eventToSuperclasses.containsKey(eventType)) {
            register(eventType);
        }
        return eventToSuperclasses.get(eventType);
    }

    private void register(Class<?> eventType) {
        eventToSuperclasses.put(eventType, eventType);

        Class<?> current = eventType.getSuperclass();
        while (current != null && current != Object.class) {
            eventToSuperclasses.put(eventType, current);
            current = current.getSuperclass();
        }

        for (Class<?> iface : eventType.getInterfaces()) {
            eventToSuperclasses.put(eventType, iface);
        }
    }
}

