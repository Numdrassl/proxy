package me.internalizable.numdrassl.api.messaging.annotation;

import me.internalizable.numdrassl.api.messaging.MessagingService;

import javax.annotation.Nonnull;

/**
 * Interface for custom type adapters used in message serialization.
 *
 * <p>Implement this interface to provide custom serialization/deserialization
 * logic for your data types. This is useful for:</p>
 * <ul>
 *   <li>Complex types that Gson cannot handle automatically</li>
 *   <li>Custom date/time formats</li>
 *   <li>Polymorphic types requiring special handling</li>
 *   <li>Performance optimizations</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Custom adapter for a Location type
 * public class LocationAdapter implements TypeAdapter<Location> {
 *
 *     @Override
 *     public Class<Location> getType() {
 *         return Location.class;
 *     }
 *
 *     @Override
 *     public String serialize(Location location) {
 *         return location.world() + "," + location.x() + "," + location.y() + "," + location.z();
 *     }
 *
 *     @Override
 *     public Location deserialize(String json) {
 *         String[] parts = json.split(",");
 *         return new Location(parts[0],
 *             Double.parseDouble(parts[1]),
 *             Double.parseDouble(parts[2]),
 *             Double.parseDouble(parts[3]));
 *     }
 * }
 *
 * // Register with messaging service
 * messaging.registerTypeAdapter(new LocationAdapter());
 * }</pre>
 *
 * @param <T> the type this adapter handles
 * @see MessagingService#registerTypeAdapter(TypeAdapter)
 */
public interface TypeAdapter<T> {

    /**
     * Get the class this adapter handles.
     *
     * @return the type class
     */
    @Nonnull
    Class<T> getType();

    /**
     * Serialize an object to JSON string.
     *
     * @param object the object to serialize
     * @return the JSON representation
     */
    @Nonnull
    String serialize(@Nonnull T object);

    /**
     * Deserialize a JSON string to an object.
     *
     * @param json the JSON string
     * @return the deserialized object
     */
    @Nonnull
    T deserialize(@Nonnull String json);
}

