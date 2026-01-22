package me.internalizable.numdrassl.api.player;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Optional;

/**
 * Represents the settings/preferences of a connected player.
 *
 * <p>These settings are extracted from the player's Connect packet
 * when they join the proxy.</p>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * PlayerSettings settings = player.getPlayerSettings();
 *
 * // Get the player's locale
 * Locale locale = settings.getLocale();
 * String message = getLocalizedMessage("welcome", locale);
 *
 * // Or with Optional
 * settings.getLocaleCode().ifPresent(code -> {
 *     // Use the raw locale code (e.g., "en-US", "de-DE")
 * });
 * }</pre>
 *
 * @see Player#getPlayerSettings()
 */
public interface PlayerSettings {

    /**
     * Gets the player's locale/language preference.
     *
     * <p>This is parsed from the language field in the Connect packet.
     * If no language was provided or parsing fails, returns the default locale.</p>
     *
     * @return the player's locale, or {@link Locale#ENGLISH} if unknown
     */
    @Nonnull
    Locale getLocale();

    /**
     * Gets the raw locale code as sent by the client.
     *
     * <p>This is the unprocessed language string from the Connect packet,
     * which may be in formats like "en-US", "de-DE", "fr", etc.</p>
     *
     * @return the raw locale code, or empty if not provided
     */
    @Nonnull
    Optional<String> getLocaleCode();

    /**
     * Gets the client type the player is using.
     *
     * <p>This indicates whether the client is the main game client,
     * an editor, or another client type.</p>
     *
     * @return the client type
     */
    @Nonnull
    ClientType getClientType();

    /**
     * Represents the type of Hytale client connecting.
     */
    enum ClientType {
        /**
         * The standard Hytale game client.
         */
        GAME,

        /**
         * The Hytale Model Maker/Editor client.
         */
        EDITOR,

        /**
         * Unknown or unrecognized client type.
         */
        UNKNOWN
    }
}

