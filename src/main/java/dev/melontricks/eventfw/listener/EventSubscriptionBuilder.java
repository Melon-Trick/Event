package dev.melontricks.eventfw.listener;

import dev.melontricks.eventfw.dispatch.EventPhase;
import dev.melontricks.eventfw.event.Event;
import java.util.Set;

public interface EventSubscriptionBuilder<E extends Event> {
    EventSubscriptionBuilder<E> priority(int priority);

    default EventSubscriptionBuilder<E> priority(EventPriority priority) {
        return priority(priority.value());
    }

    EventSubscriptionBuilder<E> owner(Object owner);

    EventSubscriptionBuilder<E> filter(EventFilter<E> filter);

    EventSubscriptionBuilder<E> receiveCancelledEvents();

    EventSubscriptionBuilder<E> exactTypeOnly();

    EventSubscriptionBuilder<E> phase(EventPhase phase);

    EventSubscriptionBuilder<E> phases(Set<EventPhase> phases);

    EventSubscriptionBuilder<E> once();

    Subscription subscribe(EventHandler<? super E> handler);

    Subscription subscribe(ContextualEventHandler<E> handler);
}
