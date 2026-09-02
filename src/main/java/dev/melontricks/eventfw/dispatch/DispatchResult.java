package dev.melontricks.eventfw.dispatch;

import dev.melontricks.eventfw.event.Event;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Summarizes a normally completed synchronous or asynchronous dispatch.
 *
 * <p>{@code matchedListeners} counts the ordered candidate snapshot. {@code invokedListeners} counts handler attempts,
 * including handlers that failed. {@code skippedListeners} counts candidates rejected because they were closed, paused,
 * phase-incompatible, cancellation-incompatible, filtered, lost a single-use race, or remained after propagation
 * stopped.
 *
 * @param event exact published event instance
 * @param sequence positive sequence unique within the bus
 * @param phase publication phase
 * @param matchedListeners candidate count selected for the runtime event type
 * @param invokedListeners handler invocation attempts
 * @param skippedListeners candidates not invoked
 * @param failures immutable failures in dispatch order
 * @param cancelled final cancellation state when the event is cancellable
 * @param propagationStopped final propagation state
 * @param duration monotonic elapsed dispatch duration
 * @param <E> concrete event type
 */
public record DispatchResult<E extends Event>(
        E event,
        long sequence,
        EventPhase phase,
        int matchedListeners,
        int invokedListeners,
        int skippedListeners,
        List<ListenerFailure> failures,
        boolean cancelled,
        boolean propagationStopped,
        Duration duration) {
    /**
     * Creates a dispatch result and freezes its failure snapshot.
     *
     * <p>The supplied failure list is defensively copied. The event, phase, failure list, every failure list element,
     * and duration are therefore stable from the result's point of view; mutating the caller's original list after
     * construction cannot alter this result.
     *
     * @throws NullPointerException if {@code event}, {@code phase}, {@code failures}, or {@code duration} is
     *     {@code null}, or if {@code failures} contains a {@code null} element
     */
    public DispatchResult {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(phase, "phase");
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        Objects.requireNonNull(duration, "duration");
    }

    /**
     * Returns whether no filter or handler failures were recorded.
     *
     * @return {@code true} when {@link #failures()} is empty
     */
    public boolean successful() {
        return failures.isEmpty();
    }

    /**
     * Returns whether at least one handler invocation was attempted.
     *
     * @return {@code true} when {@link #invokedListeners()} is positive
     */
    public boolean delivered() {
        return invokedListeners > 0;
    }
}
