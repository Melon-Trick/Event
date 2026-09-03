package dev.melontricks.eventfw.internal.listener;

import dev.melontricks.eventfw.dispatch.EventPhase;
import dev.melontricks.eventfw.event.Event;
import dev.melontricks.eventfw.listener.ContextualEventHandler;
import dev.melontricks.eventfw.listener.EventFilter;
import java.util.Objects;
import java.util.Set;

public record SubscriptionConfiguration<E extends Event>(
        Class<E> eventType,
        int priority,
        Object owner,
        EventFilter<E> filter,
        boolean receivesCancelledEvents,
        boolean exactTypeOnly,
        Set<EventPhase> phases,
        boolean singleUse,
        ContextualEventHandler<E> handler) {
    public SubscriptionConfiguration {
        Objects.requireNonNull(eventType, "eventType");
        phases = Set.copyOf(Objects.requireNonNull(phases, "phases"));
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("phases must not be empty");
        }
        Objects.requireNonNull(handler, "handler");
    }
}
