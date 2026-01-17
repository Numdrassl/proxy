package me.internalizable.numdrassl.event;

import me.internalizable.numdrassl.api.event.EventPriority;

import javax.annotation.Nonnull;

/**
 * Represents a registered event handler with its metadata.
 */
public class HandlerRegistration implements Comparable<HandlerRegistration> {

    private final Object plugin;
    private final Class<?> eventType;
    private final EventPriority priority;
    private final UntargetedEventHandler handler;
    private final Object listenerInstance;
    private final String methodName;

    public HandlerRegistration(
            @Nonnull Object plugin,
            @Nonnull Class<?> eventType,
            @Nonnull EventPriority priority,
            @Nonnull UntargetedEventHandler handler,
            @Nonnull Object listenerInstance,
            @Nonnull String methodName) {
        this.plugin = plugin;
        this.eventType = eventType;
        this.priority = priority;
        this.handler = handler;
        this.listenerInstance = listenerInstance;
        this.methodName = methodName;
    }

    public Object getPlugin() {
        return plugin;
    }

    public Class<?> getEventType() {
        return eventType;
    }

    public EventPriority getPriority() {
        return priority;
    }

    public UntargetedEventHandler getHandler() {
        return handler;
    }

    public Object getListenerInstance() {
        return listenerInstance;
    }

    public String getMethodName() {
        return methodName;
    }

    @Override
    public int compareTo(@Nonnull HandlerRegistration other) {
        return Integer.compare(this.priority.getValue(), other.priority.getValue());
    }

    @Override
    public String toString() {
        return String.format("HandlerRegistration{plugin=%s, event=%s, priority=%s, method=%s}",
            plugin.getClass().getSimpleName(),
            eventType.getSimpleName(),
            priority,
            methodName);
    }
}

