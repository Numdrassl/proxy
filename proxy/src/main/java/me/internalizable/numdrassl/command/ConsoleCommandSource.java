package me.internalizable.numdrassl.command;

import me.internalizable.numdrassl.api.command.CommandSource;
import me.internalizable.numdrassl.api.permission.PermissionFunction;
import me.internalizable.numdrassl.api.permission.Tristate;
import me.internalizable.numdrassl.api.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CommandSource implementation for console commands.
 *
 * <p>The console has all permissions by default and outputs via the logger.</p>
 */
public class ConsoleCommandSource implements CommandSource {

    private static final Logger LOGGER = LoggerFactory.getLogger("Console");
    private static final ConsoleCommandSource INSTANCE = new ConsoleCommandSource();

    private final AtomicReference<PermissionFunction> permissionFunction =
        new AtomicReference<>(PermissionFunction.ALWAYS_TRUE);

    private ConsoleCommandSource() {
    }

    public static ConsoleCommandSource getInstance() {
        return INSTANCE;
    }

    @Override
    public void sendMessage(@Nonnull String message) {
        LOGGER.info(message);
    }

    @Override
    @Nonnull
    public Tristate getPermissionValue(@Nonnull String permission) {
        return permissionFunction.get().getPermissionValue(permission);
    }

    @Override
    public boolean hasPermission(@Nonnull String permission) {
        // Console has all permissions by default
        return getPermissionValue(permission) != Tristate.FALSE;
    }

    @Override
    @Nonnull
    public PermissionFunction getPermissionFunction() {
        return permissionFunction.get();
    }

    @Override
    public void setPermissionFunction(@Nonnull PermissionFunction function) {
        permissionFunction.set(function);
    }

    @Override
    @Nonnull
    public Optional<Player> asPlayer() {
        return Optional.empty();
    }

    @Override
    public boolean isConsole() {
        return true;
    }
}

