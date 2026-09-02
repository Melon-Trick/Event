package dev.melontricks.eventfw.event;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe base implementation of {@link CancellableEvent} with first-writer-wins semantics.
 *
 * <p>Cancellation is stored atomically. Exactly one concurrent call to {@link #cancel()} or {@link #cancel(String)} can
 * return {@code true}; later calls preserve the state and reason selected by the winner. Subclasses remain responsible
 * for the thread safety of their own payload fields.
 */
public abstract class AbstractCancellableEvent implements CancellableEvent {
    private final AtomicReference<Cancellation> cancellation = new AtomicReference<>();

    /** Creates an initially active event with no cancellation reason. */
    protected AbstractCancellableEvent() {}

    /** {@inheritDoc} */
    @Override
    public final boolean cancelled() {
        return cancellation.get() != null;
    }

    /** {@inheritDoc} */
    @Override
    public final Optional<String> cancellationReason() {
        Cancellation state = cancellation.get();
        return state == null ? Optional.empty() : Optional.ofNullable(state.reason());
    }

    /** {@inheritDoc} */
    @Override
    public final boolean cancel() {
        return cancellation.compareAndSet(null, new Cancellation(null));
    }

    /** {@inheritDoc} */
    @Override
    public final boolean cancel(String reason) {
        String checkedReason = Objects.requireNonNull(reason, "reason").trim();
        if (checkedReason.isEmpty()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return cancellation.compareAndSet(null, new Cancellation(checkedReason));
    }

    private record Cancellation(String reason) {}
}
