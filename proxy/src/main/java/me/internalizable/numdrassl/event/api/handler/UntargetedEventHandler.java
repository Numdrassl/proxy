package me.internalizable.numdrassl.event.api.handler;

/**
 * A handler for events without a specific target type.
 */
@FunctionalInterface
public interface UntargetedEventHandler {

    /**
     * Executes the handler with the given event.
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

