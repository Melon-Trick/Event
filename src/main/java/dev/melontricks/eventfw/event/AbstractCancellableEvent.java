package dev.melontricks.eventfw.event;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractCancellableEvent implements CancellableEvent {
    private final AtomicReference<Cancellation> cancellation = new AtomicReference<>();

    protected AbstractCancellableEvent() {}

    @Override
    public final boolean cancelled() {
        return cancellation.get() != null;
    }

    @Override
    public final Optional<String> cancellationReason() {
        Cancellation state = cancellation.get();
        return state == null ? Optional.empty() : Optional.ofNullable(state.reason());
    }

    @Override
    public final boolean cancel() {
        return cancellation.compareAndSet(null, new Cancellation(null));
    }

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
