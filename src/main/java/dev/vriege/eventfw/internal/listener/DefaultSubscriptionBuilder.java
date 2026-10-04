package dev.vriege.eventfw.internal.listener;

import dev.vriege.eventfw.dispatch.EventPhase;
import dev.vriege.eventfw.event.Event;
import dev.vriege.eventfw.internal.bus.DefaultEventBus;
import dev.vriege.eventfw.listener.ContextualEventHandler;
import dev.vriege.eventfw.listener.EventFilter;
import dev.vriege.eventfw.listener.EventHandler;
import dev.vriege.eventfw.listener.EventPriority;
import dev.vriege.eventfw.listener.EventSubscriptionBuilder;
import dev.vriege.eventfw.listener.Subscription;
import java.util.Objects;
import java.util.Set;

public final class DefaultSubscriptionBuilder<E extends Event> implements EventSubscriptionBuilder<E> {
    private static final String VALUE_PARAMETER = "value";

    private final DefaultEventBus bus;
    private final Class<E> eventType;
    private int priority = EventPriority.NORMAL.value();
    private Object owner;
    private EventFilter<E> filter;
    private boolean receivesCancelledEvents;
    private boolean exactTypeOnly;
    private Set<EventPhase> phases = Set.of(EventPhase.values());
    private boolean singleUse;

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
        owner = Objects.requireNonNull(value, VALUE_PARAMETER);
        return this;
    }

    @Override
    public EventSubscriptionBuilder<E> filter(EventFilter<E> value) {
        filter = Objects.requireNonNull(value, VALUE_PARAMETER);
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
    public EventSubscriptionBuilder<E> phase(EventPhase value) {
        phases = Set.of(Objects.requireNonNull(value, VALUE_PARAMETER));
        return this;
    }

    @Override
    public EventSubscriptionBuilder<E> phases(Set<EventPhase> value) {
        Set<EventPhase> checkedPhases = Set.copyOf(Objects.requireNonNull(value, VALUE_PARAMETER));
        if (checkedPhases.isEmpty()) {
            throw new IllegalArgumentException("phases must not be empty");
        }
        phases = checkedPhases;
        return this;
    }

    @Override
    public EventSubscriptionBuilder<E> once() {
        singleUse = true;
        return this;
    }

    @Override
    public Subscription subscribe(EventHandler<? super E> handler) {
        EventHandler<? super E> checkedHandler = Objects.requireNonNull(handler, "handler");
        return subscribe((event, _) -> checkedHandler.handle(event));
    }

    @Override
    public Subscription subscribe(ContextualEventHandler<E> handler) {
        return bus.add(new SubscriptionConfiguration<>(
                eventType,
                priority,
                owner,
                filter,
                receivesCancelledEvents,
                exactTypeOnly,
                phases,
                singleUse,
                Objects.requireNonNull(handler, "handler")));
    }
}
