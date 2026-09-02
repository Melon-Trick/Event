package dev.melontricks.eventfw.internal.listener;

import dev.melontricks.eventfw.event.Event;
import dev.melontricks.eventfw.listener.ContextualEventHandler;
import dev.melontricks.eventfw.listener.EventFilter;
import java.util.Objects;

public record SubscriptionConfiguration<E extends Event>(
        Class<E> eventType,
        int priority,
        Object owner,
        EventFilter<E> filter,
        boolean receivesCancelledEvents,
        boolean exactTypeOnly,
        ContextualEventHandler<E> handler) {
    public SubscriptionConfiguration {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(handler, "handler");
    }
}
