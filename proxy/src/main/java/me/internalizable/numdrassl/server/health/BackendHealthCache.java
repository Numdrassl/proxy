package me.internalizable.numdrassl.server.health;

import me.internalizable.numdrassl.config.BackendServer;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class BackendHealthCache {

    private ConcurrentHashMap<String, BackendHealth> cache = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, CompletableFuture<Boolean>> inFlight = new ConcurrentHashMap<>();

    public BackendHealthCache() {

    }

    public void invalidate(@Nonnull BackendServer backend) {
        Objects.requireNonNull(backend, "backend");
        cache.remove(backend.getName());
        inFlight.remove(backend.getName());
    }

    public CompletableFuture<Boolean> get(@Nonnull BackendServer backend, @Nonnull Supplier<CompletableFuture<Boolean>> supplier) {
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(supplier, "supplier");

        String backendName = backend.getName();

        long now = System.currentTimeMillis();

        BackendHealth backendHealth = cache.get(backendName);
        if (backendHealth != null && backendHealth.statusExpire > now) {
            return CompletableFuture.completedFuture(backendHealth.status.equals(BackendHealth.Status.ONLINE));
        }

        CompletableFuture<Boolean> running = inFlight.get(backendName);
        if (running != null) {
            return running;
        }

        CompletableFuture<Boolean> created = supplier.get()
                .handle((alive, err) -> {
                    boolean ok = err == null && Boolean.TRUE.equals(alive);
                    long ttl = ok ? 1000 : 2000;
                    cache.put(backendName, new BackendHealth(ok ? BackendHealth.Status.ONLINE : BackendHealth.Status.OFFLINE, System.currentTimeMillis() + ttl));
                    return ok;
                })
                .whenComplete((alive, err) -> inFlight.remove(backendName));

        CompletableFuture<Boolean> prev = inFlight.putIfAbsent(backendName, created);
        if (prev != null) {
            return prev;
        }

        return created;
    }

}
