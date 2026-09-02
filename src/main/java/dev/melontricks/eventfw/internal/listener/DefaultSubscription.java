package dev.melontricks.eventfw.internal.listener;

import dev.melontricks.eventfw.dispatch.EventContext;
import dev.melontricks.eventfw.dispatch.EventPhase;
import dev.melontricks.eventfw.event.Event;
import dev.melontricks.eventfw.internal.bus.DefaultEventBus;
import dev.melontricks.eventfw.listener.ContextualEventHandler;
import dev.melontricks.eventfw.listener.EventFilter;
import dev.melontricks.eventfw.listener.Subscription;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class DefaultSubscription<E extends Event> implements Subscription {
    private static final int ENABLED = 0;
    private static final int PAUSED = 1;
    private static final int CLOSED = 2;

    private final DefaultEventBus bus;
    private final long id;
    private final Class<E> eventType;
    private final int priority;
    private final Object owner;
    private final EventFilter<E> filter;
    private final boolean receivesCancelledEvents;
    private final boolean exactTypeOnly;
    private final Set<EventPhase> phases;
    private final boolean singleUse;
    private final ContextualEventHandler<E> handler;
    private final AtomicInteger state = new AtomicInteger(ENABLED);

    public DefaultSubscription(DefaultEventBus bus, long id, SubscriptionConfiguration<E> configuration) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.id = id;
        SubscriptionConfiguration<E> checkedConfiguration = Objects.requireNonNull(configuration, "configuration");
        eventType = checkedConfiguration.eventType();
        priority = checkedConfiguration.priority();
        owner = checkedConfiguration.owner();
        filter = checkedConfiguration.filter();
        receivesCancelledEvents = checkedConfiguration.receivesCancelledEvents();
        exactTypeOnly = checkedConfiguration.exactTypeOnly();
        phases = checkedConfiguration.phases();
        singleUse = checkedConfiguration.singleUse();
        handler = checkedConfiguration.handler();
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
    public Set<EventPhase> phases() {
        return phases;
    }

    @Override
    public boolean singleUse() {
        return singleUse;
    }

    @Override
    public boolean paused() {
        return state.get() == PAUSED;
    }

    @Override
    public void pause() {
        state.compareAndSet(ENABLED, PAUSED);
    }

    @Override
    public void resume() {
        state.compareAndSet(PAUSED, ENABLED);
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
        return state.get() != CLOSED;
    }

    @Override
    public void close() {
        if (state.getAndSet(CLOSED) != CLOSED) {
            bus.remove(this);
        }
    }

    public boolean acquireForInvocation() {
        if (!singleUse) {
            return state.get() == ENABLED;
        }
        if (state.compareAndSet(ENABLED, CLOSED)) {
            bus.remove(this);
            return true;
        }
        return false;
    }

    public boolean test(E event, EventContext<E> context) {
        return filter.test(event, context);
    }

    public void invoke(E event, EventContext<E> context) {
        handler.handle(event, context);
    }
}
