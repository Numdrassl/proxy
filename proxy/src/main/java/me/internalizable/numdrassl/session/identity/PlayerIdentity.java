package me.internalizable.numdrassl.session.identity;

import com.hypixel.hytale.protocol.packets.connection.ClientType;
import com.hypixel.hytale.protocol.packets.connection.Connect;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable value object representing a player's identity information.
 *
 * <p>Populated from the {@link Connect} packet during the handshake phase.
 * Once created, this object is thread-safe and can be freely shared.</p>
 */
public final class PlayerIdentity {

    private final UUID uuid;
    private final String username;
    private final String protocolHash;
    private final String identityToken;
    private final String language;
    private final ClientType clientType;

    private PlayerIdentity(UUID uuid, String username, String protocolHash,
                           String identityToken, String language, ClientType clientType) {
        this.uuid = uuid;
        this.username = username;
        this.protocolHash = protocolHash;
        this.identityToken = identityToken;
        this.language = language;
        this.clientType = clientType;
    }

    /**
     * Creates a PlayerIdentity from a Connect packet.
     *
     * @param connect the connect packet containing player info
     * @return new PlayerIdentity instance
     */
    @Nonnull
    public static PlayerIdentity fromConnect(@Nonnull Connect connect) {
        Objects.requireNonNull(connect, "connect packet");
        return new PlayerIdentity(
            connect.uuid,
            connect.username,
            connect.protocolHash,
            connect.identityToken,
            connect.language,
            connect.clientType
        );
    }

    /**
     * Creates an empty/unknown identity (for sessions before Connect is received).
     */
    @Nonnull
    public static PlayerIdentity unknown() {
        return new PlayerIdentity(null, null, null, null, null, null);
    }

    @Nullable
    public UUID uuid() {
        return uuid;
    }

    @Nullable
    public String username() {
        return username;
    }

    @Nullable
    public String protocolHash() {
        return protocolHash;
    }

    @Nullable
    public String identityToken() {
        return identityToken;
    }

    /**
     * Gets the player's language/locale code.
     *
     * @return the language code (e.g., "en-US", "de-DE"), or null if not provided
     */
    @Nullable
    public String language() {
        return language;
    }

    /**
     * Gets the client type the player is using.
     *
     * @return the client type, or null if unknown
     */
    @Nullable
    public ClientType clientType() {
        return clientType;
    }

    /**
     * Checks if this identity has been populated with actual player data.
     */
    public boolean isKnown() {
        return uuid != null && username != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerIdentity that)) return false;
        return Objects.equals(uuid, that.uuid) && Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, username);
    }

    @Override
    public String toString() {
        if (!isKnown()) {
            return "PlayerIdentity{unknown}";
        }
        return "PlayerIdentity{" + username + " (" + uuid + ")}";
    }
}
