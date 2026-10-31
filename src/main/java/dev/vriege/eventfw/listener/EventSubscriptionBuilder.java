package dev.vriege.eventfw.listener;

import dev.vriege.eventfw.dispatch.EventPhase;
import dev.vriege.eventfw.event.Event;
import dev.vriege.eventfw.execution.ExecutionBuilder;
import dev.vriege.eventfw.scope.SubscriptionScope;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
     * Attaches subscriptions created by this builder to a lifecycle scope.
     *
     * @param scope non-null scope; a closed scope rejects and closes the new subscription
     * @return this builder
     */
    EventSubscriptionBuilder<E> scope(SubscriptionScope scope);

    /**
     * Selects a channel, replacing the previous selection. Default-channel listeners do not observe named channels.
     *
     * @param channel non-null name, empty for the default channel
     * @return this builder
     */
    EventSubscriptionBuilder<E> channel(String channel);

    /**
     * Adds a short-circuit event-only filter, combined with previous filters using conjunction.
     *
     * @param predicate non-null predicate
     * @return this builder
     */
    default EventSubscriptionBuilder<E> filter(Predicate<? super E> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return filter((event, _) -> predicate.test(event));
    }

    /**
     * Limits atomic invocation claims across concurrent publishers. Failed handlers consume a claim; filters do not.
     * The last claim removes the subscription before starting its handler. Replaces any previous limit or once setting.
     *
     * @param maximum positive invocation-attempt limit
     * @return this builder
     * @throws IllegalArgumentException if maximum is not positive
     */
    EventSubscriptionBuilder<E> limit(long maximum);

    /**
     * Schedules removal after installation. Expiration does not interrupt already claimed invocations.
     *
     * @param duration positive lifetime representable in nanoseconds
     * @return this builder
     * @throws IllegalArgumentException if the lifetime is not positive
     * @throws ArithmeticException if the lifetime exceeds the nanosecond range
     */
    EventSubscriptionBuilder<E> expireAfter(Duration duration);

    /**
     * Captures an execution policy for subscriptions created afterwards. Such subscriptions require publishAsync,
     * including when their handler returns void. The callback receives a fresh builder with default options.
     *
     * @param configuration non-null execution configuration callback
     * @return this builder
     */
    EventSubscriptionBuilder<E> execution(Consumer<ExecutionBuilder<E>> configuration);

    /**
     * Installs a stage-returning handler whose completion is awaited by asynchronous dispatch.
     *
     * @param handler non-null handler returning non-null stages
     * @return installed subscription
     */
    Subscription subscribeAsync(AsyncEventHandler<? super E> handler);

    /**
     * Installs a stage-returning contextual handler, preserving ordered propagation across stage completion.
     *
     * @param handler non-null handler returning non-null stages
     * @return installed subscription
     */
    Subscription subscribeAsync(ContextualAsyncEventHandler<E> handler);

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
     * Adds a predicate evaluated immediately before handler invocation. Repeated calls compose using short-circuit AND.
     *
     * @param filter non-null event filter
     * @return this builder
     * @throws NullPointerException when {@code filter} is {@code null}
     */
    EventSubscriptionBuilder<E> filter(EventFilter<E> filter);

    /**
     * Allows subscriptions to run after a {@link dev.vriege.eventfw.event.CancellableEvent} has been cancelled.
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
