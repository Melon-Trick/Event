package dev.melontricks.eventfw.internal.listener;

import dev.melontricks.eventfw.dispatch.EventContext;
import dev.melontricks.eventfw.event.Event;
import dev.melontricks.eventfw.internal.bus.DefaultEventBus;
import dev.melontricks.eventfw.listener.ContextualEventHandler;
import dev.melontricks.eventfw.listener.EventFilter;
import dev.melontricks.eventfw.listener.Subscription;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DefaultSubscription<E extends Event> implements Subscription {
    private final DefaultEventBus bus;
    private final long id;
    private final Class<E> eventType;
    private final int priority;
    private final Object owner;
    private final EventFilter<E> filter;
    private final boolean receivesCancelledEvents;
    private final boolean exactTypeOnly;
    private final ContextualEventHandler<E> handler;
    private final AtomicBoolean active = new AtomicBoolean(true);

    public DefaultSubscription(
            DefaultEventBus bus,
            long id,
            Class<E> eventType,
            int priority,
            Object owner,
            EventFilter<E> filter,
            boolean receivesCancelledEvents,
            boolean exactTypeOnly,
            ContextualEventHandler<E> handler) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.id = id;
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.priority = priority;
        this.owner = owner;
        this.filter = Objects.requireNonNull(filter, "filter");
        this.receivesCancelledEvents = receivesCancelledEvents;
        this.exactTypeOnly = exactTypeOnly;
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public long id() {
        return id;
    }

    @Override
    public Class<E> eventType() {
        return eventType;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public boolean receivesCancelledEvents() {
        return receivesCancelledEvents;
    }

    @Override
    public boolean exactTypeOnly() {
        return exactTypeOnly;
    }

    @Override
    public Optional<Object> owner() {
        return Optional.ofNullable(owner);
    }

    public Object rawOwner() {
        return owner;
    }

    @Override
    public boolean active() {
        return active.get();
    }

    @Override
    public void close() {
        if (active.compareAndSet(true, false)) {
            bus.remove(this);
        }
    }

    public boolean test(E event, EventContext<E> context) throws Exception {
        return filter.test(event, context);
    }

    public void invoke(E event, EventContext<E> context) throws Exception {
        handler.handle(event, context);
    }

    public void deactivate() {
        active.set(false);
    }
}
