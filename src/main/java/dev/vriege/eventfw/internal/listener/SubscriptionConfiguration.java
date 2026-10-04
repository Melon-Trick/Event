package dev.vriege.eventfw.internal.listener;

import dev.vriege.eventfw.dispatch.EventPhase;
import dev.vriege.eventfw.event.Event;
import dev.vriege.eventfw.listener.ContextualEventHandler;
import dev.vriege.eventfw.listener.EventFilter;
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
