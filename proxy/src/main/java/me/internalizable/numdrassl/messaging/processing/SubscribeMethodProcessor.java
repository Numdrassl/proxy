package me.internalizable.numdrassl.messaging.processing;

import me.internalizable.numdrassl.api.messaging.*;
import me.internalizable.numdrassl.api.messaging.annotation.MessageSubscribe;
import me.internalizable.numdrassl.api.messaging.channel.MessageChannel;
import me.internalizable.numdrassl.api.messaging.channel.SystemChannel;
import me.internalizable.numdrassl.api.messaging.handler.MessageHandler;
import me.internalizable.numdrassl.api.messaging.handler.PluginMessageHandler;
import me.internalizable.numdrassl.messaging.codec.MessageCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Processes {@link MessageSubscribe} annotations on methods to create subscriptions.
 *
 * <p>Handles both plugin message subscriptions and system channel subscriptions.</p>
 */
public final class SubscribeMethodProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscribeMethodProcessor.class);

    private final String localProxyId;
    private final MessageCodec codec;
    private final SubscriptionFactory subscriptionFactory;

    /**
     * Create a new processor.
     *
     * @param localProxyId this proxy's ID for filtering
     * @param codec for deserializing payloads
     * @param subscriptionFactory creates subscriptions
     */
    public SubscribeMethodProcessor(
            @Nonnull String localProxyId,
            @Nonnull MessageCodec codec,
            @Nonnull SubscriptionFactory subscriptionFactory) {
        this.localProxyId = localProxyId;
        this.codec = codec;
        this.subscriptionFactory = subscriptionFactory;
    }

    /**
     * Process a method annotated with @MessageSubscribe and create a subscription.
     *
     * @param listener the listener object
     * @param method the annotated method
     * @param annotation the MessageSubscribe annotation
     * @param explicitPluginId optional plugin ID override
     * @return the created subscription
     * @throws IllegalArgumentException if the method signature is invalid
     */
    @Nonnull
    public Subscription process(
            @Nonnull Object listener,
            @Nonnull Method method,
            @Nonnull MessageSubscribe annotation,
            @Nullable String explicitPluginId) {

        Parameter[] params = method.getParameters();
        if (params.length == 0 || params.length > 2) {
            throw new IllegalArgumentException("@MessageSubscribe method must have 1 or 2 parameters");
        }

        method.setAccessible(true);

        boolean isPluginSubscription = !annotation.channel().isEmpty();
        boolean isSystemSubscription = annotation.value() != SystemChannel.NONE;

        if (!isPluginSubscription && !isSystemSubscription) {
            throw new IllegalArgumentException(
                    "@MessageSubscribe must specify either channel (for plugin messages) or a SystemChannel value");
        }

        if (isPluginSubscription && isSystemSubscription) {
            throw new IllegalArgumentException(
                    "@MessageSubscribe cannot specify both channel and SystemChannel");
        }

        if (isPluginSubscription) {
            return processPluginSubscription(listener, method, annotation, params, explicitPluginId);
        } else {
            return processSystemSubscription(listener, method, annotation, params);
        }
    }

    @SuppressWarnings("unchecked")
    private Subscription processPluginSubscription(
            Object listener, Method method, MessageSubscribe annotation,
            Parameter[] params, String explicitPluginId) {

        String pluginId = explicitPluginId != null ? explicitPluginId : PluginIdExtractor.fromListener(listener);
        if (pluginId == null) {
            throw new IllegalArgumentException(
                    "@MessageSubscribe with channel requires the listener class to have @Plugin annotation, " +
                    "or use registerListener(listener, plugin)");
        }

        Class<?> dataType;
        boolean hasSourceProxy = false;

        if (params.length == 2 && params[0].getType() == String.class) {
            hasSourceProxy = true;
            dataType = params[1].getType();
        } else if (params.length == 1) {
            dataType = params[0].getType();
        } else {
            throw new IllegalArgumentException(
                    "@MessageSubscribe plugin method signature must be (DataType) or (String, DataType)");
        }

        final boolean finalHasSourceProxy = hasSourceProxy;
        final Class<?> finalDataType = dataType;

        PluginMessageHandler<Object> handler = (sourceProxyId, data) -> {
            try {
                if (finalHasSourceProxy) {
                    method.invoke(listener, sourceProxyId, data);
                } else {
                    method.invoke(listener, data);
                }
            } catch (Exception e) {
                LOGGER.error("Error invoking @MessageSubscribe method {}.{}: {}",
                        listener.getClass().getSimpleName(), method.getName(), e.getMessage(), e);
            }
        };

        return subscriptionFactory.subscribePlugin(
                pluginId, annotation.channel(),
                (Class<Object>) finalDataType, handler, annotation.includeSelf());
    }

    @SuppressWarnings("unchecked")
    private Subscription processSystemSubscription(
            Object listener, Method method, MessageSubscribe annotation, Parameter[] params) {

        MessageChannel channel = annotation.value().toMessageChannel();
        if (channel == null) {
            throw new IllegalArgumentException("Invalid system channel: " + annotation.value());
        }

        if (params.length != 1 || !ChannelMessage.class.isAssignableFrom(params[0].getType())) {
            throw new IllegalArgumentException(
                    "@MessageSubscribe SystemChannel method must have exactly one ChannelMessage parameter");
        }

        Class<? extends ChannelMessage> messageType =
                (Class<? extends ChannelMessage>) params[0].getType();

        MessageHandler<ChannelMessage> handler = (ch, message) -> {
            if (!messageType.isInstance(message)) {
                return;
            }
            try {
                method.invoke(listener, message);
            } catch (Exception e) {
                LOGGER.error("Error invoking @MessageSubscribe method {}.{}: {}",
                        listener.getClass().getSimpleName(), method.getName(), e.getMessage(), e);
            }
        };

        return subscriptionFactory.subscribe(channel, handler, messageType, annotation.includeSelf());
    }

    /**
     * Factory interface for creating subscriptions.
     */
    public interface SubscriptionFactory {

        Subscription subscribe(
                MessageChannel channel,
                MessageHandler<ChannelMessage> handler,
                Class<? extends ChannelMessage> messageType,
                boolean includeSelf);

        <T> Subscription subscribePlugin(
                String pluginId,
                String channel,
                Class<T> dataType,
                PluginMessageHandler<T> handler,
                boolean includeSelf);
    }
}

