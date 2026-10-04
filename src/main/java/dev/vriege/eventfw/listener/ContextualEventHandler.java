package dev.vriege.eventfw.listener;

import dev.vriege.eventfw.dispatch.EventContext;
import dev.vriege.eventfw.event.Event;

/**
 * Handles an event with access to its dispatch metadata and propagation controls.
 *
 * <p>A runtime exception is processed according to the bus failure policy. Calling
 * {@link EventContext#stopPropagation()} prevents every remaining candidate from running.
 *
 * @param <E> accepted event type
 */
@FunctionalInterface
public interface ContextualEventHandler<E extends Event> {
    /**
     * Handles one matching event on the publishing thread.
     *
     * @param event non-null published event instance
     * @param context non-null context shared by the dispatch
     */
    void handle(E event, EventContext<E> context);
}
