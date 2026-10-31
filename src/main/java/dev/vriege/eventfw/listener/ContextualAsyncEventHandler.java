package dev.vriege.eventfw.listener;

import dev.vriege.eventfw.dispatch.EventContext;
import dev.vriege.eventfw.event.Event;
import java.util.concurrent.CompletionStage;

/**
 * Asynchronous handler with access to shared propagation control and immutable dispatch metadata. Changes to the
 * context before stage completion are observed by subsequent ordered listeners.
 *
 * @param <E> accepted event type
 */
@FunctionalInterface
@SuppressWarnings("java:S1452")
public interface ContextualAsyncEventHandler<E extends Event> {
    /**
     * Begins processing without requiring the publishing thread to block.
     *
     * @param event non-null event
     * @param context non-null dispatch context
     * @return non-null stage representing all work belonging to this invocation; its successful value is ignored
     */
    CompletionStage<?> handle(E event, EventContext<E> context);
}
