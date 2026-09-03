package dev.melontricks.eventfw.internal.listener;

import dev.melontricks.eventfw.dispatch.EventContext;
import dev.melontricks.eventfw.dispatch.EventPhase;
import dev.melontricks.eventfw.event.Event;
import dev.melontricks.eventfw.internal.bus.DefaultEventBus;
import dev.melontricks.eventfw.listener.ContextualEventHandler;
import dev.melontricks.eventfw.listener.EventFilter;
import dev.melontricks.eventfw.listener.Subscription;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class DefaultSubscription<E extends Event> implements Subscription {
    private static final int ENABLED = 0;
    private static final int PAUSED = 1;
    private static final int CLOSED = 2;
    private static final VarHandle STATE_HANDLE;

    static {
        try {
            STATE_HANDLE = MethodHandles.lookup().findVarHandle(DefaultSubscription.class, "state", int.class);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private final DefaultEventBus bus;
    private final long id;
    private final Class<E> eventType;
    private final int priority;
    private final Object owner;
    private final EventFilter<E> filter;
    private final boolean receivesCancelledEvents;
    private final boolean exactTypeOnly;
    private final Set<EventPhase> phases;
    private final int phaseMask;
    private final boolean singleUse;
    private final ContextualEventHandler<E> handler;
    private volatile int state = ENABLED;

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
        phaseMask = phaseMask(phases);
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
        return state == PAUSED;
    }

    @Override
    public void pause() {
        STATE_HANDLE.compareAndSet(this, ENABLED, PAUSED);
    }

    @Override
    public void resume() {
        STATE_HANDLE.compareAndSet(this, PAUSED, ENABLED);
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
        return state != CLOSED;
    }

    @Override
    public void close() {
        if ((int) STATE_HANDLE.getAndSet(this, CLOSED) != CLOSED) {
            bus.remove(this);
        }
    }

    public boolean eligible(EventPhase phase, boolean cancelled) {
        return state == ENABLED && (phaseMask & 1 << phase.ordinal()) != 0 && (!cancelled || receivesCancelledEvents);
    }

    public boolean acquireForInvocation() {
        if (!singleUse) {
            return state == ENABLED;
        }
        if (STATE_HANDLE.compareAndSet(this, ENABLED, CLOSED)) {
            bus.remove(this);
            return true;
        }
        return false;
    }

    public boolean test(E event, EventContext<E> context) {
        return filter == null || filter.test(event, context);
    }

    public void invoke(E event, EventContext<E> context) {
        handler.handle(event, context);
    }

    private static int phaseMask(Set<EventPhase> phases) {
        int mask = 0;
        for (EventPhase phase : phases) {
            mask |= 1 << phase.ordinal();
        }
        return mask;
    }
}
