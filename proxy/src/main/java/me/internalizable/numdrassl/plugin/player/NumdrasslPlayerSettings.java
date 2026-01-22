package me.internalizable.numdrassl.plugin.player;

import me.internalizable.numdrassl.api.player.PlayerSettings;
import me.internalizable.numdrassl.session.identity.PlayerIdentity;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Optional;

/**
 * Implementation of {@link PlayerSettings} backed by {@link PlayerIdentity}.
 *
 * <p>Extracts and parses player settings from the Connect packet data stored
 * in the player's identity.</p>
 */
final class NumdrasslPlayerSettings implements PlayerSettings {

    private final PlayerIdentity identity;
    private final Locale locale;

    NumdrasslPlayerSettings(@Nonnull PlayerIdentity identity) {
        this.identity = identity;
        this.locale = parseLocale(identity.language());
    }

    @Override
    @Nonnull
    public Locale getLocale() {
        return locale;
    }

    @Override
    @Nonnull
    public Optional<String> getLocaleCode() {
        return Optional.ofNullable(identity.language());
    }

    @Override
    @Nonnull
    public ClientType getClientType() {
        var protocolClientType = identity.clientType();
        if (protocolClientType == null) {
            return ClientType.UNKNOWN;
        }
        return switch (protocolClientType) {
            case Game -> ClientType.GAME;
            case Editor -> ClientType.EDITOR;
        };
    }

    /**
     * Parses a locale string into a {@link Locale} object.
     *
     * <p>Handles formats like:</p>
     * <ul>
     *   <li>"en" -> Locale.ENGLISH</li>
     *   <li>"en-US" -> new Locale("en", "US")</li>
     *   <li>"en_US" -> new Locale("en", "US")</li>
     *   <li>"de-DE" -> new Locale("de", "DE")</li>
     * </ul>
     *
     * @param language the language string from the client, may be null
     * @return the parsed Locale, or {@link Locale#ENGLISH} if parsing fails
     */
    @Nonnull
    private static Locale parseLocale(String language) {
        if (language == null || language.isBlank()) {
            return Locale.ENGLISH;
        }

        try {
            // Handle both "en-US" and "en_US" formats
            String normalized = language.replace('-', '_');
            String[] parts = normalized.split("_", 3);

            return switch (parts.length) {
                case 1 -> Locale.of(parts[0].toLowerCase());
                case 2 -> Locale.of(parts[0].toLowerCase(), parts[1].toUpperCase());
                case 3 -> Locale.of(parts[0].toLowerCase(), parts[1].toUpperCase(), parts[2]);
                default -> Locale.ENGLISH;
            };
        } catch (Exception e) {
            return Locale.ENGLISH;
        }
    }
}
