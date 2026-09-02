package dev.melontricks.eventfw.listener;

import dev.melontricks.eventfw.dispatch.EventContext;
import dev.melontricks.eventfw.event.Event;

/**
 * Tests a matching event immediately before its handler becomes eligible for invocation.
 *
 * <p>Filters run in normal listener order and may inspect the dispatch context. Returning {@code false} records a
 * skipped listener. A runtime exception records a filter-stage failure and is processed by the bus failure policy. For
 * a single-use subscription, filtering occurs before atomic consumption, so a rejected or failing filter does not
 * consume the subscription.
 *
 * @param <E> accepted event type
 */
@FunctionalInterface
public interface EventFilter<E extends Event> {
    /**
     * Decides whether the associated handler should run.
     *
     * @param event non-null published event instance
     * @param context non-null dispatch context
     * @return {@code true} to continue to the handler; {@code false} to skip it
     */
    boolean test(E event, EventContext<E> context);
}
