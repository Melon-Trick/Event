package dev.vriege.eventfw.internal.listener;

import dev.vriege.eventfw.dispatch.EventPhase;
import dev.vriege.eventfw.event.Event;
import dev.vriege.eventfw.execution.ExecutionBuilder;
import dev.vriege.eventfw.execution.ExecutionOptions;
import dev.vriege.eventfw.internal.bus.DefaultEventBus;
import dev.vriege.eventfw.listener.AsyncEventHandler;
import dev.vriege.eventfw.listener.ContextualAsyncEventHandler;
import dev.vriege.eventfw.listener.ContextualEventHandler;
import dev.vriege.eventfw.listener.EventFilter;
import dev.vriege.eventfw.listener.EventHandler;
import dev.vriege.eventfw.listener.EventPriority;
import dev.vriege.eventfw.listener.EventSubscriptionBuilder;
import dev.vriege.eventfw.listener.Subscription;
import dev.vriege.eventfw.scope.SubscriptionScope;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class DefaultSubscriptionBuilder<E extends Event> implements EventSubscriptionBuilder<E> {
    private static final String VALUE_PARAMETER = "value";
    private static final String HANDLER_PARAMETER = "handler";

    private final DefaultEventBus bus;
    private final Class<E> eventType;
    private int priority = EventPriority.NORMAL.value();
    private Object owner;
    private EventFilter<E> filter;
    private boolean receivesCancelledEvents;
    private boolean exactTypeOnly;
    private Set<EventPhase> phases = Set.of(EventPhase.values());
    private long invocationLimit = Long.MAX_VALUE;
    private Duration lifetime;
    private String channel = "";
    private ExecutionOptions<E> execution;
    private SubscriptionScope scope;

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
        EventFilter<E> checked = Objects.requireNonNull(value, VALUE_PARAMETER);
        filter = filter == null ? checked : filter.and(checked);
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
        return limit(1);
    }

    @Override
    public EventSubscriptionBuilder<E> scope(SubscriptionScope value) {
        scope = Objects.requireNonNull(value, VALUE_PARAMETER);
        return this;
    }

    @Override
    public EventSubscriptionBuilder<E> channel(String value) {
        channel = Objects.requireNonNull(value, VALUE_PARAMETER);
        return this;
    }

    @Override
    public EventSubscriptionBuilder<E> limit(long value) {
        if (value < 1) {
            throw new IllegalArgumentException("invocation limit must be positive");
        }
        invocationLimit = value;
        return this;
    }

    @Override
    public EventSubscriptionBuilder<E> expireAfter(Duration value) {
        Objects.requireNonNull(value, VALUE_PARAMETER);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("lifetime must be positive");
        }
        lifetime = Duration.ofNanos(value.toNanos());
        return this;
    }

    @Override
    public EventSubscriptionBuilder<E> execution(Consumer<ExecutionBuilder<E>> configuration) {
        ExecutionBuilder<E> builder = new ExecutionBuilder<>();
        Objects.requireNonNull(configuration, "configuration").accept(builder);
        execution = builder.build();
        return this;
    }

    @Override
    public Subscription subscribe(EventHandler<? super E> handler) {
        EventHandler<? super E> checkedHandler = Objects.requireNonNull(handler, HANDLER_PARAMETER);
        return subscribe((event, _) -> checkedHandler.handle(event));
    }

    @Override
    public Subscription subscribe(ContextualEventHandler<E> handler) {
        return install(Objects.requireNonNull(handler, HANDLER_PARAMETER), null);
    }

    @Override
    public Subscription subscribeAsync(AsyncEventHandler<? super E> handler) {
        Objects.requireNonNull(handler, HANDLER_PARAMETER);
        return subscribeAsync((event, _) -> handler.handle(event));
    }

    @Override
    public Subscription subscribeAsync(ContextualAsyncEventHandler<E> handler) {
        return install(null, Objects.requireNonNull(handler, HANDLER_PARAMETER));
    }

    private Subscription install(ContextualEventHandler<E> handler, ContextualAsyncEventHandler<E> asyncHandler) {
        Subscription subscription = bus.add(new SubscriptionConfiguration<>(
                eventType,
                priority,
                owner,
                filter,
                receivesCancelledEvents,
                exactTypeOnly,
                phases,
                invocationLimit,
                lifetime,
                channel,
                execution,
                handler,
                asyncHandler));
        return scope == null ? subscription : scope.track(subscription);
    }
}
