package dev.melontricks.eventfw.event;

import java.util.Optional;

/**
 * Represents an event whose ordinary delivery may be cancelled.
 *
 * <p>Cancellation and propagation stopping are separate concepts. Cancelling an event skips subsequent subscriptions
 * unless they explicitly opt into cancelled-event delivery. Stopping propagation through the dispatch context prevents
 * every remaining subscription from running, including cancellation observers.
 *
 * <p>Implementations should make cancellation monotonic: once cancelled, an event should remain cancelled for the rest
 * of its lifetime.
 */
public interface CancellableEvent extends Event {
    /**
     * Returns the current cancellation state.
     *
     * @return {@code true} after cancellation has been accepted
     */
    boolean cancelled();

    /**
     * Returns the diagnostic reason supplied by the successful cancelling call.
     *
     * @return the reason, or an empty optional when no reason was supplied
     */
    Optional<String> cancellationReason();

    /**
     * Attempts to cancel the event without a diagnostic reason.
     *
     * @return {@code true} when this invocation changed the event from active to cancelled
     */
    boolean cancel();

    /**
     * Attempts to cancel the event with a diagnostic reason.
     *
     * @param reason non-null, non-blank text describing why processing was cancelled
     * @return {@code true} when this invocation changed the event from active to cancelled
     * @throws NullPointerException when {@code reason} is {@code null}
     * @throws IllegalArgumentException when {@code reason} is blank
     */
    boolean cancel(String reason);
}
