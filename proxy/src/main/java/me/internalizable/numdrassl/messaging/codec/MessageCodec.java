package me.internalizable.numdrassl.messaging.codec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import me.internalizable.numdrassl.api.messaging.ChannelMessage;
import me.internalizable.numdrassl.api.messaging.annotation.TypeAdapter;
import me.internalizable.numdrassl.api.messaging.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON codec for serializing/deserializing channel messages.
 *
 * <p>Uses Gson with custom type adapters for proper polymorphic deserialization
 * of the sealed {@link ChannelMessage} hierarchy.</p>
 *
 * <p>Supports registration of custom {@link TypeAdapter}s for plugin-specific types.</p>
 */
public final class MessageCodec {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageCodec.class);

    private final Map<Class<?>, TypeAdapter<?>> customAdapters = new ConcurrentHashMap<>();
    private volatile Gson gson;

    public MessageCodec() {
        this.gson = buildGson();
    }

    private Gson buildGson() {
        GsonBuilder builder = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantAdapter())
                .registerTypeAdapter(UUID.class, new UuidAdapter());

        // Register custom adapters as Gson type adapters
        for (Map.Entry<Class<?>, TypeAdapter<?>> entry : customAdapters.entrySet()) {
            builder.registerTypeAdapter(entry.getKey(), new GsonTypeAdapterWrapper<>(entry.getValue()));
        }

        return builder.create();
    }

    /**
     * Register a custom type adapter.
     *
     * @param adapter the adapter to register
     * @param <T> the type handled by the adapter
     */
    public <T> void registerTypeAdapter(@Nonnull TypeAdapter<T> adapter) {
        customAdapters.put(adapter.getType(), adapter);
        this.gson = buildGson();
        LOGGER.debug("Registered type adapter for {}", adapter.getType().getSimpleName());
    }

    /**
     * Unregister a type adapter.
     *
     * @param type the type to unregister
     */
    public void unregisterTypeAdapter(@Nonnull Class<?> type) {
        if (customAdapters.remove(type) != null) {
            this.gson = buildGson();
            LOGGER.debug("Unregistered type adapter for {}", type.getSimpleName());
        }
    }

    /**
     * Get the Gson instance for custom serialization needs.
     *
     * @return the Gson instance
     */
    @Nonnull
    public Gson getGson() {
        return gson;
    }

    /**
     * Encode a plugin payload object to JSON string.
     *
     * @param payload the payload object
     * @return the JSON string
     */
    @Nonnull
    public String encodePayload(@Nonnull Object payload) {
        return gson.toJson(payload);
    }

    /**
     * Decode a plugin payload JSON string to a typed object.
     *
     * @param json the JSON string
     * @param type the target type
     * @param <T> the target type
     * @return the decoded object, or null if decoding fails
     */
    @Nullable
    public <T> T decodePayload(@Nonnull String json, @Nonnull Class<T> type) {
        try {
            return gson.fromJson(json, type);
        } catch (JsonParseException e) {
            LOGGER.error("Failed to decode payload to {}: {}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * Serialize a message to JSON.
     *
     * @param message the message to serialize
     * @return the JSON string
     */
    @Nonnull
    public String encode(@Nonnull ChannelMessage message) {
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("type", message.messageType());
        wrapper.add("data", gson.toJsonTree(message));
        return gson.toJson(wrapper);
    }

    /**
     * Deserialize a message from JSON.
     *
     * @param json the JSON string
     * @return the deserialized message, or null if parsing fails
     */
    @Nullable
    public ChannelMessage decode(@Nonnull String json) {
        try {
            JsonObject wrapper = gson.fromJson(json, JsonObject.class);

            // Guard against malformed messages
            if (wrapper == null) {
                LOGGER.warn("Malformed message: null wrapper from JSON: {}", json);
                return null;
            }
            if (!wrapper.has("type") || wrapper.get("type").isJsonNull()) {
                LOGGER.warn("Malformed message: missing type: {}", json);
                return null;
            }
            if (!wrapper.has("data") || wrapper.get("data").isJsonNull()) {
                LOGGER.warn("Malformed message: missing data: {}", json);
                return null;
            }

            String type = wrapper.get("type").getAsString();
            JsonElement data = wrapper.get("data");

            return switch (type) {
                case "heartbeat" -> gson.fromJson(data, HeartbeatMessage.class);
                case "player_count" -> gson.fromJson(data, PlayerCountMessage.class);
                case "chat" -> gson.fromJson(data, ChatMessage.class);
                case "transfer" -> gson.fromJson(data, TransferMessage.class);
                case "plugin" -> gson.fromJson(data, PluginMessage.class);
                case "broadcast" -> gson.fromJson(data, BroadcastMessage.class);
                default -> {
                    LOGGER.warn("Unknown message type: {}", type);
                    yield null;
                }
            };
        } catch (JsonParseException e) {
            LOGGER.error("Failed to decode message: {}", e.getMessage());
            return null;
        }
    }

    // ==================== Type Adapters ====================

    /**
     * Adapter for java.time.Instant serialization.
     */
    private static final class InstantAdapter
            implements JsonSerializer<Instant>, JsonDeserializer<Instant> {

        @Override
        public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toEpochMilli());
        }

        @Override
        public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            return Instant.ofEpochMilli(json.getAsLong());
        }
    }

    /**
     * Adapter for UUID serialization.
     */
    private static final class UuidAdapter
            implements JsonSerializer<UUID>, JsonDeserializer<UUID> {

        @Override
        public JsonElement serialize(UUID src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }

        @Override
        public UUID deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            return UUID.fromString(json.getAsString());
        }
    }

    /**
     * Wraps our TypeAdapter interface to work with Gson's type adapter system.
     */
    private static final class GsonTypeAdapterWrapper<T>
            implements JsonSerializer<T>, JsonDeserializer<T> {

        private final TypeAdapter<T> adapter;
        private final Gson internalGson = new Gson();

        GsonTypeAdapterWrapper(TypeAdapter<T> adapter) {
            this.adapter = adapter;
        }

        @Override
        public JsonElement serialize(T src, Type typeOfSrc, JsonSerializationContext context) {
            String json = adapter.serialize(src);
            return internalGson.fromJson(json, JsonElement.class);
        }

        @Override
        public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            String jsonStr = internalGson.toJson(json);
            return adapter.deserialize(jsonStr);
        }
    }
}

