package dev.melontricks.eventfw.bus;

import dev.melontricks.eventfw.dispatch.DispatchResult;
import dev.melontricks.eventfw.dispatch.EventPhase;
import dev.melontricks.eventfw.event.Event;
import java.util.concurrent.CompletionStage;

/**
 * Publishes typed events and reports the completed dispatch.
 *
 * <p>Synchronous methods run listeners on the calling thread. Asynchronous methods schedule the entire synchronous
 * dispatch on the executor configured by the owning bus. Asynchronous publication does not make individual listeners
 * parallel and therefore preserves normal listener ordering.
 */
public interface EventPublisher {
    /**
     * Publishes an event in the {@link EventPhase#DEFAULT} phase.
     *
     * @param event non-null event instance delivered without cloning
     * @param <E> concrete event type
     * @return immutable summary after every eligible listener has completed
     * @throws NullPointerException when {@code event} is {@code null}
     * @throws IllegalStateException when the owning bus is closed or nesting exceeds its configured limit
     * @throws dev.melontricks.eventfw.dispatch.EventDispatchException when fail-fast delivery encounters a listener or
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
