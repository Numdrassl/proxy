package me.internalizable.numdrassl.event.api;

import me.internalizable.numdrassl.api.event.Cancellable;
import me.internalizable.numdrassl.api.event.EventHandler;
import me.internalizable.numdrassl.api.event.EventManager;
import me.internalizable.numdrassl.api.event.EventPriority;
import me.internalizable.numdrassl.api.event.Subscribe;
import me.internalizable.numdrassl.event.api.handler.EventTypeTracker;
import me.internalizable.numdrassl.event.api.handler.HandlerRegistration;
import me.internalizable.numdrassl.event.api.handler.UntargetedEventHandler;
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
 * Plugin API event manager with priority-based ordering and async support.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Event type hierarchy tracking</li>
 *   <li>Priority-based handler ordering</li>
 *   <li>Async event firing with CompletableFuture</li>
 *   <li>MethodHandle-based invocation for performance</li>
 *   <li>Thread-safe handler registration</li>
 * </ul>
 */
public final class NumdrasslEventManager implements EventManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NumdrasslEventManager.class);
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private final EventTypeTracker eventTypeTracker = new EventTypeTracker();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final Map<Class<?>, List<HandlerRegistration>> handlersByType = new ConcurrentHashMap<>();
    private final Map<Object, List<HandlerRegistration>> handlersByPlugin = new ConcurrentHashMap<>();
    private final Map<Object, List<HandlerRegistration>> handlersByListener = new ConcurrentHashMap<>();

    private final ExecutorService asyncExecutor;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public NumdrasslEventManager() {
        this.asyncExecutor = createDefaultExecutor();
    }

    public NumdrasslEventManager(@Nonnull ExecutorService executor) {
        this.asyncExecutor = Objects.requireNonNull(executor, "executor");
    }

    private static ExecutorService createDefaultExecutor() {
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        return Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "Numdrassl-Event-Executor");
            t.setDaemon(true);
            return t;
        });
    }

    // ==================== Registration via @Subscribe ====================

    @Override
    public void register(@Nonnull Object plugin, @Nonnull Object listener) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(listener, "listener");

        List<HandlerRegistration> registrations = scanForSubscribeMethods(plugin, listener);
        if (!registrations.isEmpty()) {
            handlersByPlugin.computeIfAbsent(plugin, k -> new CopyOnWriteArrayList<>()).addAll(registrations);
            handlersByListener.computeIfAbsent(listener, k -> new CopyOnWriteArrayList<>()).addAll(registrations);
        }
    }

    private List<HandlerRegistration> scanForSubscribeMethods(Object plugin, Object listener) {
        List<HandlerRegistration> registrations = new ArrayList<>();

        for (Method method : listener.getClass().getDeclaredMethods()) {
            Subscribe subscribe = method.getAnnotation(Subscribe.class);
            if (subscribe == null) {
                continue;
            }

            HandlerRegistration reg = createRegistration(plugin, listener, method, subscribe);
            if (reg != null) {
                registrations.add(reg);
                registerHandler(reg);
            }
        }

        return registrations;
    }

    private HandlerRegistration createRegistration(Object plugin, Object listener, Method method, Subscribe subscribe) {
        if (method.getParameterCount() != 1) {
            LOGGER.warn("@Subscribe method {}.{} has invalid parameter count (expected 1)",
                listener.getClass().getSimpleName(), method.getName());
            return null;
        }

        Class<?> eventType = method.getParameterTypes()[0];

        try {
            method.setAccessible(true);
            MethodHandle handle = LOOKUP.unreflect(method).bindTo(listener);

            UntargetedEventHandler handler = event -> {
                try {
                    handle.invoke(event);
                } catch (Throwable t) {
                    throw new RuntimeException("Error invoking " + method.getName(), t);
                }
            };

            HandlerRegistration registration = new HandlerRegistration(
                plugin, eventType, subscribe.priority(), handler, listener, method.getName()
            );

            LOGGER.debug("Registered handler: {}.{} for {} (priority={})",
                listener.getClass().getSimpleName(), method.getName(),
                eventType.getSimpleName(), subscribe.priority());

            return registration;
        } catch (IllegalAccessException e) {
            LOGGER.error("Failed to create MethodHandle for {}.{}",
                listener.getClass().getSimpleName(), method.getName(), e);
            return null;
        }
    }

    // ==================== Programmatic Registration ====================

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
            handlers.sort(Comparator.comparingInt(h -> h.getPriority().getValue()));
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ==================== Unregistration ====================

    @Override
    public void unregister(@Nonnull Object listener) {
        Objects.requireNonNull(listener, "listener");

        List<HandlerRegistration> registrations = handlersByListener.remove(listener);
        if (registrations != null) {
            for (HandlerRegistration reg : registrations) {
                removeHandler(reg);
                removeFromPluginTracking(reg);
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
                removeFromListenerTracking(reg);
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

    private void removeFromPluginTracking(HandlerRegistration reg) {
        List<HandlerRegistration> pluginRegs = handlersByPlugin.get(reg.getPlugin());
        if (pluginRegs != null) {
            pluginRegs.remove(reg);
        }
    }

    private void removeFromListenerTracking(HandlerRegistration reg) {
        List<HandlerRegistration> listenerRegs = handlersByListener.get(reg.getListenerInstance());
        if (listenerRegs != null) {
            listenerRegs.remove(reg);
        }
    }

    // ==================== Event Firing ====================

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

        List<HandlerRegistration> handlers = collectHandlers(event.getClass());
        if (handlers.isEmpty()) {
            return event;
        }

        handlers.sort(Comparator.comparingInt(h -> h.getPriority().getValue()));

        for (HandlerRegistration handler : handlers) {
            executeHandler(event, handler);
        }

        return event;
    }

    private List<HandlerRegistration> collectHandlers(Class<?> eventType) {
        Collection<Class<?>> eventTypes = eventTypeTracker.getFriendsOf(eventType);
        List<HandlerRegistration> applicable = new ArrayList<>();

        lock.readLock().lock();
        try {
            for (Class<?> type : eventTypes) {
                List<HandlerRegistration> handlers = handlersByType.get(type);
                if (handlers != null) {
                    applicable.addAll(handlers);
                }
            }
        } finally {
            lock.readLock().unlock();
        }

        return applicable;
    }

    private void executeHandler(Object event, HandlerRegistration handler) {
        try {
            handler.getHandler().execute(event);
        } catch (Exception e) {
            LOGGER.error("Error handling event {} in handler {} from plugin {}",
                event.getClass().getSimpleName(),
                handler.getMethodName(),
                handler.getPlugin().getClass().getSimpleName(), e);
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Fires an event and checks if it was cancelled.
     *
     * @return true if the event was NOT cancelled
     */
    public boolean fireAndForget(@Nonnull Object event) {
        Objects.requireNonNull(event, "event");
        fireSync(event);
        return !(event instanceof Cancellable cancellable) || !cancellable.isCancelled();
    }

    public int getHandlerCount(@Nonnull Class<?> eventType) {
        Objects.requireNonNull(eventType, "eventType");
        lock.readLock().lock();
        try {
            List<HandlerRegistration> handlers = handlersByType.get(eventType);
            return handlers != null ? handlers.size() : 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Nonnull
    public Set<Class<?>> getRegisteredEventTypes() {
        lock.readLock().lock();
        try {
            return new HashSet<>(handlersByType.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

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

    public boolean isShutdown() {
        return shutdown.get();
    }
}

