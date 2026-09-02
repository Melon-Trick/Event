package dev.melontricks.eventfw.internal.listener;

import dev.melontricks.eventfw.event.Event;
import dev.melontricks.eventfw.internal.bus.DefaultEventBus;
import dev.melontricks.eventfw.listener.ContextualEventHandler;
import dev.melontricks.eventfw.listener.EventFilter;
import dev.melontricks.eventfw.listener.EventHandler;
import dev.melontricks.eventfw.listener.EventPriority;
import dev.melontricks.eventfw.listener.EventSubscriptionBuilder;
import dev.melontricks.eventfw.listener.Subscription;
import java.util.Objects;

public final class DefaultSubscriptionBuilder<E extends Event> implements EventSubscriptionBuilder<E> {
    private final DefaultEventBus bus;
    private final Class<E> eventType;
    private int priority = EventPriority.NORMAL.value();
    private Object owner;
    private EventFilter<E> filter = (event, context) -> true;
    private boolean receivesCancelledEvents;
    private boolean exactTypeOnly;

    public DefaultSubscriptionBuilder(DefaultEventBus bus, Class<E> eventType) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
    }

    @Override
    public EventSubscriptionBuilder<E> priority(int value) {
        priority = value;
        return this;
    }

    @Override
    public EventSubscriptionBuilder<E> owner(Object value) {
        owner = Objects.requireNonNull(value, "value");
        return this;
    }

    @Override
    public EventSubscriptionBuilder<E> filter(EventFilter<E> value) {
        filter = Objects.requireNonNull(value, "value");
        return this;
    }

    @Override
    public EventSubscriptionBuilder<E> receiveCancelledEvents() {
        receivesCancelledEvents = true;
        return this;
    }

    @Override
    public EventSubscriptionBuilder<E> exactTypeOnly() {
        exactTypeOnly = true;
        return this;
    }

    @Override
    public Subscription subscribe(EventHandler<? super E> handler) {
        EventHandler<? super E> checkedHandler = Objects.requireNonNull(handler, "handler");
        return subscribe((event, context) -> checkedHandler.handle(event));
    }

    @Override
    public Subscription subscribe(ContextualEventHandler<E> handler) {
        return bus.add(
                eventType,
                priority,
                owner,
                filter,
                receivesCancelledEvents,
                exactTypeOnly,
                Objects.requireNonNull(handler, "handler"));
    }
}
