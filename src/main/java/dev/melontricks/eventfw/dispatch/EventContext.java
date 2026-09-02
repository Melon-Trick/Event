package dev.melontricks.eventfw.dispatch;

import dev.melontricks.eventfw.bus.EventPublisher;
import dev.melontricks.eventfw.event.Event;
import java.time.Instant;

/**
 * Exposes immutable metadata and a shared propagation control for one dispatch.
 *
 * <p>The same context instance is passed to every contextual filter and handler in a dispatch. Stopping propagation is
 * thread-safe and monotonic. It does not cancel the event and cannot be reversed.
 *
 * @param <E> concrete published event type
 */
public interface EventContext<E extends Event> {
    /**
     * Returns the exact object supplied to publication.
     *
     * @return non-null event instance
     */
    E event();

    /**
     * Returns a publisher backed by the same bus, enabling nested publication.
     *
     * @return non-null publisher
     */
    EventPublisher publisher();

    /**
     * Returns the monotonic sequence assigned by the bus when dispatch began.
     *
     * <p>Sequence values are unique within one bus but concurrent threads may complete out of sequence.
     *
     * @return positive dispatch sequence
     */
    long sequence();

    /**
     * Returns the wall-clock instant captured when dispatch began.
     *
     * @return non-null publication instant
     */
    Instant publishedAt();

    /**
     * Returns the publication phase used to select subscriptions.
     *
     * @return non-null phase
     */
    EventPhase phase();

    /**
     * Returns synchronous nesting depth on the current publishing thread.
     *
     * @return {@code 0} for top-level publication, increasing by one for each nested publication
     */
    int nestingDepth();

    /**
     * Returns whether a listener has stopped this dispatch.
     *
     * @return current monotonic propagation state
     */
    boolean propagationStopped();

    /**
     * Prevents every remaining candidate from receiving the current dispatch.
     *
     * <p>This operation is idempotent and affects neither nested dispatches nor future publication of the same event
     * object.
     */
    void stopPropagation();
}
