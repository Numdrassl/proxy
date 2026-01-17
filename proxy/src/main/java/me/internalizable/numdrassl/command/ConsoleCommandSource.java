package me.internalizable.numdrassl.command;

import me.internalizable.numdrassl.api.command.CommandSource;
import me.internalizable.numdrassl.api.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Console command source implementation.
 */
public class ConsoleCommandSource implements CommandSource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleCommandSource.class);
    private static final ConsoleCommandSource INSTANCE = new ConsoleCommandSource();

    private ConsoleCommandSource() {}

    public static ConsoleCommandSource getInstance() {
        return INSTANCE;
    }

    @Override
    public void sendMessage(@Nonnull String message) {
        LOGGER.info(message);
    }

    @Override
    public boolean hasPermission(@Nonnull String permission) {
        // Console always has all permissions
        return true;
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

