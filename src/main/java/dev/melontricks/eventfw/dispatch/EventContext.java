package dev.melontricks.eventfw.dispatch;

import dev.melontricks.eventfw.bus.EventPublisher;
import dev.melontricks.eventfw.event.Event;
import java.time.Instant;

public interface EventContext<E extends Event> {
    E event();

    EventPublisher publisher();

    long sequence();

    Instant publishedAt();

    EventPhase phase();

    int nestingDepth();

    boolean propagationStopped();

    void stopPropagation();
}
