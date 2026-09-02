package dev.melontricks.eventfw.listener;

import dev.melontricks.eventfw.dispatch.EventPhase;
import dev.melontricks.eventfw.event.Event;
import java.util.Set;

/**
 * Configures and installs programmatic subscriptions for one event type.
 *
 * <p>Builders are mutable, are not thread-safe, and may be reused. Each call to {@link #subscribe(EventHandler)} or
 * {@link #subscribe(ContextualEventHandler)} captures the current options in a new independent subscription. Defaults
 * are normal priority, no owner, an accepting filter, delivery to all phases, polymorphic matching according to the
 * bus, cancelled-event rejection, and reusable delivery.
 *
 * @param <E> subscribed event type
 */
public interface EventSubscriptionBuilder<E extends Event> {
    /**
     * Sets an arbitrary numeric priority.
     *
     * @param priority priority where greater values execute first
     * @return this builder
     */
    EventSubscriptionBuilder<E> priority(int priority);

    /**
     * Sets a conventional priority preset.
     *
     * @param priority non-null priority preset
     * @return this builder
     * @throws NullPointerException when {@code priority} is {@code null}
     */
    default EventSubscriptionBuilder<E> priority(EventPriority priority) {
        return priority(priority.value());
    }

    /**
     * Associates subscriptions with an identity-based owner for bulk unregistration.
     *
     * <p>The bus retains a strong reference to the owner until the subscription is removed.
     *
     * @param owner non-null owner reference
     * @return this builder
     * @throws NullPointerException when {@code owner} is {@code null}
     */
    EventSubscriptionBuilder<E> owner(Object owner);

    /**
     * Sets the predicate evaluated immediately before handler invocation.
     *
     * @param filter non-null event filter
     * @return this builder
     * @throws NullPointerException when {@code filter} is {@code null}
     */
    EventSubscriptionBuilder<E> filter(EventFilter<E> filter);

    /**
     * Allows subscriptions to run after a {@link dev.melontricks.eventfw.event.CancellableEvent} has been cancelled.
     *
     * @return this builder
     */
    EventSubscriptionBuilder<E> receiveCancelledEvents();

    /**
     * Rejects subtype instances even when the bus uses polymorphic matching.
     *
     * @return this builder
     */
    EventSubscriptionBuilder<E> exactTypeOnly();

    /**
     * Restricts subscriptions to one publication phase.
     *
     * @param phase non-null accepted phase
     * @return this builder
     * @throws NullPointerException when {@code phase} is {@code null}
     */
    EventSubscriptionBuilder<E> phase(EventPhase phase);

    /**
     * Restricts subscriptions to a non-empty set of publication phases.
     *
     * @param phases non-null, non-empty phase set containing no null values
     * @return this builder
     * @throws NullPointerException when the set or one of its elements is {@code null}
     * @throws IllegalArgumentException when the set is empty
     */
    EventSubscriptionBuilder<E> phases(Set<EventPhase> phases);

    /**
     * Makes subscriptions atomically single-use across concurrent publishers.
     *
     * @return this builder
     */
    EventSubscriptionBuilder<E> once();

    /**
     * Installs a simple handler using a snapshot of the current options.
     *
     * @param handler non-null handler
     * @return live subscription handle
     * @throws NullPointerException when {@code handler} is {@code null}
     * @throws IllegalStateException when the bus is closed
     */
    Subscription subscribe(EventHandler<? super E> handler);

    /**
     * Installs a context-aware handler using a snapshot of the current options.
     *
     * @param handler non-null contextual handler
     * @return live subscription handle
     * @throws NullPointerException when {@code handler} is {@code null}
     * @throws IllegalStateException when the bus is closed
     */
    Subscription subscribe(ContextualEventHandler<E> handler);
}
