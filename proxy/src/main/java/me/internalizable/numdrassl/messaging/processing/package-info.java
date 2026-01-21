/**
 * Annotation processing for messaging subscriptions.
 *
 * <p>Contains utilities for processing {@link me.internalizable.numdrassl.api.messaging.Subscribe}
 * annotations and extracting plugin IDs from annotated classes.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link SubscribeMethodProcessor} - Processes @Subscribe annotations on methods</li>
 *   <li>{@link PluginIdExtractor} - Extracts plugin IDs from @Plugin annotations</li>
 * </ul>
 */
@javax.annotation.ParametersAreNonnullByDefault
package me.internalizable.numdrassl.messaging.processing;

