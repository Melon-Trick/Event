package dev.vriege.eventfw.listener;

import dev.vriege.eventfw.event.Event;
import java.util.concurrent.CompletionStage;

/**
 * Begins asynchronous work whose completion participates in ordered dispatch. The next listener starts only after the
 * returned stage completes. Synchronous publication rejects asynchronous subscriptions through the configured failure
 * policy without starting their work or consuming their invocation limit.
 *
 * @param <E> accepted event type
 */
@FunctionalInterface
@SuppressWarnings("java:S1452")
public interface AsyncEventHandler<E extends Event> {
    /**
     * Starts processing an event.
     *
     * @param event non-null event, retained without cloning
     * @return non-null stage whose failure is reported as a handler failure; its successful value is ignored
     */
    CompletionStage<?> handle(E event);
}
