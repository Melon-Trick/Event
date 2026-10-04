package dev.vriege.eventfw.listener;

import dev.vriege.eventfw.event.Event;

/**
 * Handles an event without requiring dispatch metadata.
 *
 * <p>A runtime exception is processed according to the bus failure policy. Errors are not contained by the bus and
 * escape publication.
 *
 * @param <E> accepted event type
 */
@FunctionalInterface
public interface EventHandler<E extends Event> {
    /**
     * Handles one matching event on the publishing thread.
     *
     * @param event non-null published event instance
     */
    void handle(E event);
}
