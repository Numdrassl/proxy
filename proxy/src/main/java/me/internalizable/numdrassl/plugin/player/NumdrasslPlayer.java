package me.internalizable.numdrassl.plugin.player;

import com.hypixel.hytale.protocol.Packet;
import me.internalizable.numdrassl.api.chat.ChatMessageBuilder;
import me.internalizable.numdrassl.api.event.permission.PermissionSetupEvent;
import me.internalizable.numdrassl.api.permission.PermissionFunction;
import me.internalizable.numdrassl.api.permission.PermissionProvider;
import me.internalizable.numdrassl.api.permission.Tristate;
import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.player.PlayerSettings;
import me.internalizable.numdrassl.api.player.TransferResult;
import me.internalizable.numdrassl.api.server.RegisteredServer;
import me.internalizable.numdrassl.plugin.NumdrasslProxy;
import me.internalizable.numdrassl.session.ProxySession;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of the {@link Player} API interface.
 *
 * <p>Wraps a {@link ProxySession} and provides a clean API for plugins to interact
 * with connected players.</p>
 */
public final class NumdrasslPlayer implements Player {

    private static final UUID UNKNOWN_UUID = new UUID(0, 0);
    private static final String UNKNOWN_USERNAME = "Unknown";

    private final ProxySession session;
    private final NumdrasslProxy proxy;
    private final AtomicReference<PermissionFunction> permissionFunction;
    private final PlayerSettings playerSettings;
    private final AtomicBoolean permissionsSetup = new AtomicBoolean(false);

    public NumdrasslPlayer(@Nonnull ProxySession session, @Nonnull NumdrasslProxy proxy) {
        this.session = Objects.requireNonNull(session, "session");
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        // Initialize with a lazy permission function that will be set up on first access
        this.permissionFunction = new AtomicReference<>(PermissionFunction.ALWAYS_UNDEFINED);
        // Initialize player settings from session identity
        this.playerSettings = new NumdrasslPlayerSettings(session.getIdentity());
    }

    /**
     * Sets up the permission function by firing PermissionSetupEvent.
     * This allows permission plugins like LuckPerms to inject their providers.
     * This should be called once during the login process.
     *
     * <p>This method fires the event synchronously and waits for any registered
     * async tasks to complete before returning.</p>
     *
     * @return a CompletableFuture that completes when permission setup is done
     */
    public CompletableFuture<Void> setupPermissions() {
        if (!permissionsSetup.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null); // Already set up
        }

        // Get the default provider from the permission manager
        PermissionProvider defaultProvider = proxy.getPermissionManager()
            .getProvider()
            .orElse(player -> PermissionFunction.ALWAYS_UNDEFINED);

        // Fire the event to let plugins provide their own permission function
        // Plugins may register async tasks (e.g., LuckPerms loading user data)
        PermissionSetupEvent event = new PermissionSetupEvent(this, defaultProvider);
        proxy.getNumdrasslEventManager().fireSync(event);

        // Wait for any async tasks and then set the permission function
        return event.getAsyncTask()
            .orTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .exceptionally(ex -> {
                org.slf4j.LoggerFactory.getLogger(NumdrasslPlayer.class)
                    .warn("Timeout or error waiting for permission setup for {}", getUsername(), ex);
                return null;
            })
            .thenRun(() -> {
                // Create the permission function from the provider
                PermissionFunction function = event.createFunction();
                permissionFunction.set(function);
            });
    }

    /**
     * Checks if permissions have been set up for this player.
     */
    public boolean arePermissionsSetUp() {
        return permissionsSetup.get();
    }

    // ==================== Identity ====================

    @Override
    @Nonnull
    public UUID getUniqueId() {
        UUID uuid = session.getPlayerUuid();
        return uuid != null ? uuid : UNKNOWN_UUID;
    }

    @Override
    @Nonnull
    public String getUsername() {
        String username = session.getUsername();
        return username != null ? username : UNKNOWN_USERNAME;
    }

    @Override
    @Nonnull
    public InetSocketAddress getRemoteAddress() {
        return session.getClientAddress();
    }

    @Override
    @Nullable
    public String getProtocolHash() {
        return session.getProtocolHash();
    }

    @Override
    public long getSessionId() {
        return session.getSessionId();
    }

    @Override
    @Nonnull
    public PlayerSettings getPlayerSettings() {
        return playerSettings;
    }

    // ==================== Connection State ====================

    @Override
    @Nonnull
    public Optional<RegisteredServer> getCurrentServer() {
        String serverName = session.getCurrentServerName();
        return serverName != null ? proxy.getServer(serverName) : Optional.empty();
    }

    @Override
    public boolean isConnected() {
        return session.isActive();
    }

    @Override
    public long getPing() {
        return session.getPing();
    }

    // ==================== Packet Sending ====================

    @Override
    public void sendPacket(@Nonnull Object packet) {
        Objects.requireNonNull(packet, "packet");
        if (packet instanceof Packet p) {
            session.sendToClient(p);
        }
    }

    @Override
    public void sendPacketToServer(@Nonnull Object packet) {
        Objects.requireNonNull(packet, "packet");
        if (packet instanceof Packet p) {
            session.sendToServer(p);
        }
    }

    @Override
    public void sendMessage(@Nonnull String message) {
        Objects.requireNonNull(message, "message");
        session.sendChatMessage(message);
    }

    @Override
    public void sendMessage(@Nonnull ChatMessageBuilder builder) {
        Objects.requireNonNull(builder, "builder");
        session.sendChatMessage(builder);
    }

    // ==================== Connection Management ====================

    @Override
    public void disconnect(@Nonnull String reason) {
        Objects.requireNonNull(reason, "reason");
        session.disconnect(reason);
    }

    @Override
    @Nonnull
    public CompletableFuture<TransferResult> transfer(@Nonnull RegisteredServer server) {
        Objects.requireNonNull(server, "server");

        // Find the backend server config
        var config = proxy.getCore().getConfig();
        var backend = config.getBackendByName(server.getName());

        if (backend == null) {
            return CompletableFuture.completedFuture(
                TransferResult.failure("Backend server not found: " + server.getName())
            );
        }

        // Use PlayerTransfer which sends ClientReferral
        return proxy.getCore().getPlayerTransfer().transfer(session, backend);
    }

    @Override
    @Nonnull
    public CompletableFuture<TransferResult> transfer(@Nonnull String serverName) {
        Objects.requireNonNull(serverName, "serverName");

        // Use PlayerTransfer which sends ClientReferral
        return proxy.getCore().getPlayerTransfer().transfer(session, serverName);
    }

    // ==================== Permissions ====================

    @Override
    @Nonnull
    public Tristate getPermissionValue(@Nonnull String permission) {
        Objects.requireNonNull(permission, "permission");
        // Ensure permissions are set up before checking
        if (!permissionsSetup.get()) {
            setupPermissions();
        }
        return permissionFunction.get().getPermissionValue(permission);
    }

    @Override
    public boolean hasPermission(@Nonnull String permission) {
        return getPermissionValue(permission).asBoolean();
    }

    @Override
    @Nonnull
    public PermissionFunction getPermissionFunction() {
        // Ensure permissions are set up before returning function
        if (!permissionsSetup.get()) {
            setupPermissions();
        }
        return permissionFunction.get();
    }

    @Override
    public void setPermissionFunction(@Nonnull PermissionFunction function) {
        Objects.requireNonNull(function, "function");
        permissionFunction.set(function);
        permissionsSetup.set(true);
    }

    // ==================== Internal Access ====================

    /**
     * Gets the underlying proxy session.
     */
    @Nonnull
    public ProxySession getSession() {
        return session;
    }


    // ==================== Object Methods ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NumdrasslPlayer that)) return false;
        return session.getSessionId() == that.session.getSessionId();
    }

    @Override
    public int hashCode() {
        return Long.hashCode(session.getSessionId());
    }

    @Override
    public String toString() {
        return String.format("Player{name=%s, uuid=%s, session=%d}",
            getUsername(), getUniqueId(), session.getSessionId());
    }
}

