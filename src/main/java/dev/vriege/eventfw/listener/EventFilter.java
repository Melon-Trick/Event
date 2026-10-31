package dev.vriege.eventfw.listener;

import dev.vriege.eventfw.dispatch.EventContext;
import dev.vriege.eventfw.event.Event;
import java.util.Objects;

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

    /**
     * Combines this filter with another using short-circuit conjunction.
     *
     * @param other non-null second filter
     * @return filter evaluating the second operand only if this filter accepts
     */
    default EventFilter<E> and(EventFilter<E> other) {
        Objects.requireNonNull(other, "other");
        return (event, context) -> test(event, context) && other.test(event, context);
    }

    /**
     * Combines this filter with another using short-circuit disjunction.
     *
     * @param other non-null second filter
     * @return filter evaluating the second operand only if this filter rejects
     */
    default EventFilter<E> or(EventFilter<E> other) {
        Objects.requireNonNull(other, "other");
        return (event, context) -> test(event, context) || other.test(event, context);
    }

    /**
     * Inverts this filter without suppressing exceptions.
     *
     * @return negated filter
     */
    default EventFilter<E> negate() {
        return (event, context) -> !test(event, context);
    }
}
