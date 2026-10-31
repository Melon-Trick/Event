package dev.vriege.eventfw.internal.listener;

import dev.vriege.eventfw.dispatch.EventPhase;
import dev.vriege.eventfw.event.Event;
import dev.vriege.eventfw.execution.ExecutionOptions;
import dev.vriege.eventfw.listener.ContextualAsyncEventHandler;
import dev.vriege.eventfw.listener.ContextualEventHandler;
import dev.vriege.eventfw.listener.EventFilter;
import java.time.Duration;
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
        long invocationLimit,
        Duration lifetime,
        String channel,
        ExecutionOptions<E> execution,
        ContextualEventHandler<E> handler,
        ContextualAsyncEventHandler<E> asyncHandler) {
    public SubscriptionConfiguration {
        Objects.requireNonNull(eventType, "eventType");
        phases = Set.copyOf(Objects.requireNonNull(phases, "phases"));
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("phases must not be empty");
        }
        Objects.requireNonNull(channel, "channel");
        if ((handler == null) == (asyncHandler == null)) {
            throw new IllegalArgumentException("exactly one handler must be configured");
        }
    }
}
