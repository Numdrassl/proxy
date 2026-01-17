package me.internalizable.numdrassl.event;

/**
 * A handler for events that doesn't have a specific target type.
 * Based on Velocity's UntargetedEventHandler.
 */
@FunctionalInterface
public interface UntargetedEventHandler {

    /**
     * Execute the handler with the given event.
     *
     * @param event the event to handle
     * @throws Exception if an error occurs
     */
    void execute(Object event) throws Exception;

    /**
     * Returns an empty no-op handler.
     */
    static UntargetedEventHandler empty() {
        return event -> {};
    }
}

