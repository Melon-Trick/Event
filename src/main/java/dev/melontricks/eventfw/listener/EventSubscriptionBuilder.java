package dev.melontricks.eventfw.listener;

import dev.melontricks.eventfw.event.Event;

public interface EventSubscriptionBuilder<E extends Event> {
    EventSubscriptionBuilder<E> priority(int priority);

    default EventSubscriptionBuilder<E> priority(EventPriority priority) {
        return priority(priority.value());
    }

    EventSubscriptionBuilder<E> owner(Object owner);

    EventSubscriptionBuilder<E> filter(EventFilter<E> filter);

    EventSubscriptionBuilder<E> receiveCancelledEvents();

    EventSubscriptionBuilder<E> exactTypeOnly();

    Subscription subscribe(EventHandler<? super E> handler);

    Subscription subscribe(ContextualEventHandler<E> handler);
}
