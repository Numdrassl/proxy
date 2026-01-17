package me.internalizable.numdrassl.event.api.handler;

import me.internalizable.numdrassl.api.event.EventPriority;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Represents a registered event handler with its metadata.
 */
public final class HandlerRegistration implements Comparable<HandlerRegistration> {

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
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.priority = Objects.requireNonNull(priority, "priority");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.listenerInstance = Objects.requireNonNull(listenerInstance, "listenerInstance");
        this.methodName = Objects.requireNonNull(methodName, "methodName");
    }

    @Nonnull
    public Object getPlugin() {
        return plugin;
    }

    @Nonnull
    public Class<?> getEventType() {
        return eventType;
    }

    @Nonnull
    public EventPriority getPriority() {
        return priority;
    }

    @Nonnull
    public UntargetedEventHandler getHandler() {
        return handler;
    }

    @Nonnull
    public Object getListenerInstance() {
        return listenerInstance;
    }

    @Nonnull
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

