package dev.melontricks.eventfw.event;

import java.util.Optional;

public interface CancellableEvent extends Event {
    boolean cancelled();

    Optional<String> cancellationReason();

    boolean cancel();

    boolean cancel(String reason);
}
