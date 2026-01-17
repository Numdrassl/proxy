package me.internalizable.numdrassl.event;

import me.internalizable.numdrassl.api.event.Cancellable;
import me.internalizable.numdrassl.api.event.EventHandler;
import me.internalizable.numdrassl.api.event.EventManager;
import me.internalizable.numdrassl.api.event.EventPriority;
import me.internalizable.numdrassl.api.event.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Advanced event manager implementation based on Velocity's VelocityEventManager.
 *
 * Features:
 * - Event type hierarchy tracking (events dispatch to superclass/interface handlers)
 * - Priority-based handler ordering
 * - Async event firing with CompletableFuture
 * - MethodHandle-based invocation for performance
 * - Thread-safe handler registration
 */
public class NumdrasslEventManager implements EventManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NumdrasslEventManager.class);
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private final EventTypeTracker eventTypeTracker = new EventTypeTracker();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // Map: EventType -> Sorted list of handlers
    private final Map<Class<?>, List<HandlerRegistration>> handlersByType = new ConcurrentHashMap<>();

    // Map: Plugin -> List of registrations (for unregistration)
    private final Map<Object, List<HandlerRegistration>> handlersByPlugin = new ConcurrentHashMap<>();

    // Map: Listener instance -> List of registrations
    private final Map<Object, List<HandlerRegistration>> handlersByListener = new ConcurrentHashMap<>();

    // Executor for async event handling
    private final ExecutorService asyncExecutor;

    // Flag to track if we're shutting down
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public NumdrasslEventManager() {
        this.asyncExecutor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            r -> {
                Thread t = new Thread(r, "Numdrassl-Event-Executor");
                t.setDaemon(true);
                return t;
            }
        );
    }

    public NumdrasslEventManager(ExecutorService executor) {
        this.asyncExecutor = executor;
    }

    @Override
    public void register(@Nonnull Object plugin, @Nonnull Object listener) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(listener, "listener");

        List<HandlerRegistration> registrations = new ArrayList<>();

        // Scan for @Subscribe methods
        for (Method method : listener.getClass().getDeclaredMethods()) {
            Subscribe subscribe = method.getAnnotation(Subscribe.class);
            if (subscribe == null) {
                continue;
            }

            // Validate method signature
            if (method.getParameterCount() != 1) {
                LOGGER.warn("@Subscribe method {}.{} has invalid parameter count (expected 1, got {})",
                    listener.getClass().getSimpleName(), method.getName(), method.getParameterCount());
                continue;
            }

            Class<?> eventType = method.getParameterTypes()[0];
            EventPriority priority = subscribe.priority();

            // Create MethodHandle for fast invocation
            try {
                method.setAccessible(true);
                MethodHandle handle = LOOKUP.unreflect(method);
                MethodHandle boundHandle = handle.bindTo(listener);

                UntargetedEventHandler handler = event -> {
                    try {
                        boundHandle.invoke(event);
                    } catch (Throwable t) {
                        throw new RuntimeException("Error invoking event handler " + method.getName(), t);
                    }
                };

                HandlerRegistration registration = new HandlerRegistration(
                    plugin, eventType, priority, handler, listener, method.getName()
                );

                registrations.add(registration);
                registerHandler(registration);

                LOGGER.debug("Registered event handler: {}.{} for {} with priority {}",
                    listener.getClass().getSimpleName(), method.getName(),
                    eventType.getSimpleName(), priority);

            } catch (IllegalAccessException e) {
                LOGGER.error("Failed to create MethodHandle for {}.{}",
                    listener.getClass().getSimpleName(), method.getName(), e);
            }
        }

        if (!registrations.isEmpty()) {
            handlersByPlugin.computeIfAbsent(plugin, k -> new CopyOnWriteArrayList<>()).addAll(registrations);
            handlersByListener.computeIfAbsent(listener, k -> new CopyOnWriteArrayList<>()).addAll(registrations);
        }
    }

    @Override
    public <E> void register(@Nonnull Object plugin, @Nonnull Class<E> eventClass, @Nonnull EventHandler<E> handler) {
        register(plugin, eventClass, EventPriority.NORMAL, handler);
    }

    @Override
    public <E> void register(@Nonnull Object plugin, @Nonnull Class<E> eventClass,
                             @Nonnull EventPriority priority, @Nonnull EventHandler<E> handler) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(eventClass, "eventClass");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(handler, "handler");

        UntargetedEventHandler untargeted = event -> {
            @SuppressWarnings("unchecked")
            E typedEvent = (E) event;
            handler.handle(typedEvent);
        };

        HandlerRegistration registration = new HandlerRegistration(
            plugin, eventClass, priority, untargeted, handler, "lambda"
        );

        registerHandler(registration);
        handlersByPlugin.computeIfAbsent(plugin, k -> new CopyOnWriteArrayList<>()).add(registration);
        handlersByListener.computeIfAbsent(handler, k -> new CopyOnWriteArrayList<>()).add(registration);
    }

    private void registerHandler(HandlerRegistration registration) {
        lock.writeLock().lock();
        try {
            List<HandlerRegistration> handlers = handlersByType.computeIfAbsent(
                registration.getEventType(), k -> new CopyOnWriteArrayList<>()
            );
            handlers.add(registration);
            // Re-sort by priority
            handlers.sort(Comparator.comparingInt(h -> h.getPriority().getValue()));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void unregister(@Nonnull Object listener) {
        Objects.requireNonNull(listener, "listener");

        List<HandlerRegistration> registrations = handlersByListener.remove(listener);
        if (registrations != null) {
            for (HandlerRegistration reg : registrations) {
                removeHandler(reg);
                // Also remove from plugin tracking
                List<HandlerRegistration> pluginRegs = handlersByPlugin.get(reg.getPlugin());
                if (pluginRegs != null) {
                    pluginRegs.remove(reg);
                }
            }
        }
    }

    @Override
    public void unregisterAll(@Nonnull Object plugin) {
        Objects.requireNonNull(plugin, "plugin");

        List<HandlerRegistration> registrations = handlersByPlugin.remove(plugin);
        if (registrations != null) {
            for (HandlerRegistration reg : registrations) {
                removeHandler(reg);
                // Also remove from listener tracking
                List<HandlerRegistration> listenerRegs = handlersByListener.get(reg.getListenerInstance());
                if (listenerRegs != null) {
                    listenerRegs.remove(reg);
                }
            }
        }
    }

    private void removeHandler(HandlerRegistration registration) {
        lock.writeLock().lock();
        try {
            List<HandlerRegistration> handlers = handlersByType.get(registration.getEventType());
            if (handlers != null) {
                handlers.remove(registration);
                if (handlers.isEmpty()) {
                    handlersByType.remove(registration.getEventType());
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    @Nonnull
    public <E> CompletableFuture<E> fire(@Nonnull E event) {
        Objects.requireNonNull(event, "event");

        if (shutdown.get()) {
            return CompletableFuture.completedFuture(event);
        }

        return CompletableFuture.supplyAsync(() -> fireSync(event), asyncExecutor);
    }

    @Override
    @Nonnull
    public <E> E fireSync(@Nonnull E event) {
        Objects.requireNonNull(event, "event");

        Class<?> eventType = event.getClass();
        Collection<Class<?>> eventTypes = eventTypeTracker.getFriendsOf(eventType);

        // Collect all handlers that should receive this event
        List<HandlerRegistration> applicableHandlers = new ArrayList<>();

        lock.readLock().lock();
        try {
            for (Class<?> type : eventTypes) {
                List<HandlerRegistration> handlers = handlersByType.get(type);
                if (handlers != null) {
                    applicableHandlers.addAll(handlers);
                }
            }
        } finally {
            lock.readLock().unlock();
        }

        if (applicableHandlers.isEmpty()) {
            return event;
        }

        // Sort all handlers by priority
        applicableHandlers.sort(Comparator.comparingInt(h -> h.getPriority().getValue()));

        // Execute handlers in order
        for (HandlerRegistration handler : applicableHandlers) {
            try {
                handler.getHandler().execute(event);
            } catch (Exception e) {
                LOGGER.error("Error handling event {} in handler {} from plugin {}",
                    eventType.getSimpleName(),
                    handler.getMethodName(),
                    handler.getPlugin().getClass().getSimpleName(),
                    e);
            }
        }

        return event;
    }

    /**
     * Fire an event and get the result, with support for Cancellable events.
     * Returns true if the event was NOT cancelled.
     */
    public boolean fireAndForget(@Nonnull Object event) {
        fireSync(event);
        if (event instanceof Cancellable) {
            return !((Cancellable) event).isCancelled();
        }
        return true;
    }

    /**
     * Get the number of registered handlers for a specific event type.
     */
    public int getHandlerCount(Class<?> eventType) {
        lock.readLock().lock();
        try {
            List<HandlerRegistration> handlers = handlersByType.get(eventType);
            return handlers != null ? handlers.size() : 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all event types that have registered handlers.
     */
    public Set<Class<?>> getRegisteredEventTypes() {
        lock.readLock().lock();
        try {
            return new HashSet<>(handlersByType.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Shutdown the event manager.
     */
    public void shutdown() {
        shutdown.set(true);
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Check if the event manager has been shut down.
     */
    public boolean isShutdown() {
        return shutdown.get();
    }
}

