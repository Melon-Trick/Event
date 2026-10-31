package dev.vriege.eventfw.bus;

import dev.vriege.eventfw.dispatch.DispatchBuilder;
import dev.vriege.eventfw.dispatch.DispatchOptions;
import dev.vriege.eventfw.dispatch.DispatchResult;
import dev.vriege.eventfw.dispatch.EventPhase;
import dev.vriege.eventfw.event.Event;
import dev.vriege.eventfw.event.EventKey;
import java.util.concurrent.CompletionStage;

/**
 * Publishes typed events and reports the completed dispatch.
 *
 * <p>Synchronous methods run synchronous listeners on the calling thread and report async-only candidates as execution
 * failures. Asynchronous methods begin dispatch on the bus executor, awaiting each handler's completion before starting
 * the next candidate. Individual subscriptions may select a different executor. Continuations can run on completion
 * threads; asynchronous dispatch preserves listener order within a publication without serializing separate
 * publications. Publication never clones the event. Callers must avoid unsynchronized mutation while asynchronous work
 * is outstanding.
 */
public interface EventPublisher {
    /**
     * Creates an inactive publication builder. Repeated terminal calls republish the same event reference.
     *
     * @param event non-null event
     * @param <E> event type
     * @return new mutable publication builder
     */
    default <E extends Event> DispatchBuilder<E> dispatch(E event) {
        return new DispatchBuilder<>(this, event);
    }

    /**
     * Creates an inactive publication builder on a typed channel, validating the event before any dispatch.
     *
     * @param key non-null typed channel
     * @param event non-null event implementing the key's contract
     * @param <E> event type
     * @return new builder with the key's channel selected
     */
    default <E extends Event> DispatchBuilder<E> dispatch(EventKey<E> key, E event) {
        return dispatch(key.eventType().cast(event)).channel(key.name());
    }

    /**
     * Publishes synchronously with explicit metadata. Async-only candidates are reported through the failure policy,
     * without starting or consuming them. Filters execute before this execution-mode check.
     *
     * @param event non-null event
     * @param options non-null immutable dispatch options
     * @param <E> event type
     * @return completed dispatch summary
     */
    <E extends Event> DispatchResult<E> publish(E event, DispatchOptions options);

    /**
     * Publishes asynchronously, awaiting each candidate's stage before advancing to the next candidate. Listener-start
     * order is preserved; continuations may run on completion threads. Executor rejection and dispatch failure complete
     * the returned stage exceptionally. Cancelling the result does not interrupt accepted work.
     *
     * @param event non-null event
     * @param options non-null immutable dispatch options
     * @param <E> event type
     * @return completion stage covering the entire ordered dispatch
     */
    <E extends Event> CompletionStage<DispatchResult<E>> publishAsync(E event, DispatchOptions options);

    /**
     * Publishes an event in the {@link EventPhase#DEFAULT} phase.
     *
     * @param event non-null event instance delivered without cloning
     * @param <E> concrete event type
     * @return immutable summary after every eligible listener has completed
     * @throws NullPointerException when {@code event} is {@code null}
     * @throws IllegalStateException when the owning bus is closed or nesting exceeds its configured limit
     * @throws dev.vriege.eventfw.dispatch.EventDispatchException when fail-fast delivery encounters a listener or
     *     filter failure
     */
    <E extends Event> DispatchResult<E> publish(E event);

    /**
     * Publishes an event in an explicit phase.
     *
     * @param event non-null event instance
     * @param phase non-null phase used to select subscriptions
     * @param <E> concrete event type
     * @return immutable completed dispatch summary
     * @throws NullPointerException when either argument is {@code null}
     * @throws IllegalStateException when the owning bus is closed or nesting exceeds its configured limit
     */
    <E extends Event> DispatchResult<E> publish(E event, EventPhase phase);

    /**
     * Schedules publication in the {@link EventPhase#DEFAULT} phase.
     *
     * @param event non-null event instance
     * @param <E> concrete event type
     * @return stage completed with the dispatch result or exceptionally with the publication failure
     * @throws NullPointerException when {@code event} is {@code null}
     * @throws IllegalStateException when the owning bus is already closed
     */
    <E extends Event> CompletionStage<DispatchResult<E>> publishAsync(E event);

    /**
     * Schedules publication in an explicit phase.
     *
     * @param event non-null event instance
     * @param phase non-null phase used to select subscriptions
     * @param <E> concrete event type
     * @return stage completed with the dispatch result or exceptionally with the publication failure
     * @throws NullPointerException when either argument is {@code null}
     * @throws IllegalStateException when the owning bus is already closed
     */
    <E extends Event> CompletionStage<DispatchResult<E>> publishAsync(E event, EventPhase phase);
}
