package me.internalizable.numdrassl.api.event.player;

import me.internalizable.numdrassl.api.event.ResultedEvent;
import me.internalizable.numdrassl.api.player.Player;
import me.internalizable.numdrassl.api.server.RegisteredServer;

import javax.annotation.Nullable;

/**
 * Fired when a player’s initial backend server is being determined.
 *
 * <p>Allows listeners to override the selected server or explicitly request the
 * default backend server.</p>
 */
public class PlayerChooseInitialServerEvent implements ResultedEvent<PlayerChooseInitialServerEvent.InitialServerResult> {

    private final Player player;

    private InitialServerResult result = InitialServerResult.useDefault();

    public PlayerChooseInitialServerEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public InitialServerResult getResult() {
        return result;
    }

    @Override
    public void setResult(InitialServerResult result) {
        this.result = result;
    }

    public static final class InitialServerResult implements Result {

        private InitialServerMode mode = InitialServerMode.DEFAULT;

        @Nullable
        private final RegisteredServer initialServer;

        public InitialServerResult(InitialServerMode mode, @Nullable RegisteredServer initialServer) {
            this.mode = mode;
            this.initialServer = initialServer;
        }

        @Override
        public boolean isAllowed() {
            return true;
        }

        @Nullable
        public RegisteredServer getInitialServer() {
            return initialServer;
        }

        public InitialServerMode getMode() {
            return mode;
        }

        public static InitialServerResult useDefault() {
            return new InitialServerResult(InitialServerMode.DEFAULT, null);
        }

        public static InitialServerResult useCustom(RegisteredServer server) {
            return new InitialServerResult(InitialServerMode.CUSTOM, server);
        }

        public enum InitialServerMode {
            CUSTOM,
            DEFAULT
        }
    }
}
