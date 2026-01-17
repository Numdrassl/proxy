package me.internalizable.numdrassl.event;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Tracks event type hierarchies for proper event inheritance.
 * Based on Velocity's EventTypeTracker.
 */
public class EventTypeTracker {

    private final Multimap<Class<?>, Class<?>> eventToSuperclasses = HashMultimap.create();

    /**
     * Get all event types that the given event type can be dispatched as.
     * This includes the event type itself, its superclasses, and interfaces.
     *
     * @param eventType the event type
     * @return collection of all applicable event types
     */
    public Collection<Class<?>> getFriendsOf(Class<?> eventType) {
        if (!eventToSuperclasses.containsKey(eventType)) {
            register(eventType);
        }
        return eventToSuperclasses.get(eventType);
    }

    private void register(Class<?> eventType) {
        eventToSuperclasses.put(eventType, eventType);

        // Walk up the class hierarchy
        Class<?> current = eventType.getSuperclass();
        while (current != null && current != Object.class) {
            eventToSuperclasses.put(eventType, current);
            current = current.getSuperclass();
        }

        // Walk interfaces
        for (Class<?> iface : eventType.getInterfaces()) {
            eventToSuperclasses.put(eventType, iface);
        }
    }

    /**
     * Clear the cache for a specific event type (used when handlers are unregistered).
     */
    public void invalidate(Class<?> eventType) {
        // For now, we don't need to invalidate - handlers are removed directly
    }
}

