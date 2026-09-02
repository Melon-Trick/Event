package dev.melontricks.eventfw.dispatch;

import dev.melontricks.eventfw.event.Event;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record DispatchResult<E extends Event>(
        E event,
        long sequence,
        int matchedListeners,
        int invokedListeners,
        int skippedListeners,
        List<ListenerFailure> failures,
        boolean cancelled,
        boolean propagationStopped,
        Duration duration) {
    public DispatchResult {
        Objects.requireNonNull(event, "event");
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        Objects.requireNonNull(duration, "duration");
    }

    public boolean successful() {
        return failures.isEmpty();
    }

    public boolean delivered() {
        return invokedListeners > 0;
    }
}
