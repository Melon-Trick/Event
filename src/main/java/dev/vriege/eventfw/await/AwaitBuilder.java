package dev.vriege.eventfw.await;

import dev.vriege.eventfw.bus.EventBus;
import dev.vriege.eventfw.dispatch.EventPhase;
import dev.vriege.eventfw.event.Event;
import dev.vriege.eventfw.listener.EventFilter;
import dev.vriege.eventfw.listener.Subscription;
import dev.vriege.eventfw.scope.SubscriptionScope;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Configures a cancellable wait for the next eligible event. No listener is installed until {@link #start()}.
 *
 * <p>Builders are mutable, reusable, and not thread-safe. Each start installs an independent listener. The returned
 * future wins at most once even with concurrent publishers. Success, exceptional completion, cancellation, timeout, bus
 * closure, and scope closure all remove the listener and cancel its timeout task. External removal cancels the future.
 * The default wait has no deadline, accepts all phases on the default channel, and rejects cancelled events.
 *
 * @param <E> awaited event type
 */
public final class AwaitBuilder<E extends Event> {
    private final EventBus bus;
    private final Class<E> eventType;
    private final ScheduledExecutorService scheduler;
    private EventFilter<E> filter = (_, _) -> true;
    private String channel = "";
    private EventPhase phase;
    private Duration timeout;
    private SubscriptionScope scope;
    private boolean receiveCancelled;

    /**
     * Creates an inactive wait configuration using a borrowed scheduler.
     *
     * @param bus non-null bus
     * @param eventType non-null event type
     * @param scheduler non-null timer service, never shut down by this builder
     */
    public AwaitBuilder(EventBus bus, Class<E> eventType, ScheduledExecutorService scheduler) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Adds a short-circuit event predicate using conjunction.
     *
     * @param value non-null predicate
     * @return this builder
     */
    public AwaitBuilder<E> filter(Predicate<? super E> value) {
        Objects.requireNonNull(value, "filter");
        return filter((event, _) -> value.test(event));
    }

    /**
     * Adds a contextual predicate using conjunction. A predicate failure completes the wait exceptionally.
     *
     * @param value non-null filter
     * @return this builder
     */
    public AwaitBuilder<E> filter(EventFilter<E> value) {
        filter = filter.and(value);
        return this;
    }

    /**
     * Selects an exact channel name.
     *
     * @param value non-null channel
     * @return this builder
     */
    public AwaitBuilder<E> channel(String value) {
        channel = Objects.requireNonNull(value, "channel");
        return this;
    }

    /**
     * Restricts delivery to one publication phase.
     *
     * @param value non-null phase
     * @return this builder
     */
    public AwaitBuilder<E> phase(EventPhase value) {
        phase = Objects.requireNonNull(value, "phase");
        return this;
    }

    /**
     * Attaches the wait to a scope; closing that scope cancels the wait.
     *
     * @param value non-null scope
     * @return this builder
     */
    public AwaitBuilder<E> scope(SubscriptionScope value) {
        scope = Objects.requireNonNull(value, "scope");
        return this;
    }

    /**
     * Includes cancellable events whose cancellation flag is already set.
     *
     * @return this builder
     */
    public AwaitBuilder<E> receiveCancelledEvents() {
        receiveCancelled = true;
        return this;
    }

    /**
     * Sets a deadline measured from installation. Timeout completes exceptionally with {@link TimeoutException}.
     *
     * @param value positive duration representable in nanoseconds
     * @return this builder
     * @throws IllegalArgumentException if the duration is not positive
     * @throws ArithmeticException if the duration exceeds the nanosecond range
     */
    public AwaitBuilder<E> timeout(Duration value) {
        Objects.requireNonNull(value, "timeout");
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        timeout = Duration.ofNanos(value.toNanos());
        return this;
    }

    /**
     * Installs a listener and returns its cancellable result. Events published before installation are not replayed.
     * Caller completion of the returned future also removes the listener. Scheduler rejection completes exceptionally.
     *
     * @return independently cancellable event future
     * @throws IllegalStateException if the bus is closed; a closed scope instead completes the returned future
     */
    public CompletableFuture<E> start() {
        CompletableFuture<E> result = new CompletableFuture<>();
        AtomicReference<Subscription> subscription = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> timer = new AtomicReference<>();
        result.whenComplete((_, _) -> cleanup(subscription, timer));
        EventFilter<E> capturedFilter = filter;
        var builder = bus.on(eventType).channel(channel).filter((event, context) -> {
            try {
                return !result.isDone() && capturedFilter.test(event, context);
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
                throw failure;
            }
        });
        if (phase != null) {
            builder.phase(phase);
        }
        if (receiveCancelled) {
            builder.receiveCancelledEvents();
        }
        Subscription installed = builder.subscribe(result::complete);
        subscription.set(installed);
        installed.whenClosed().thenRun(() -> result.cancel(false));
        try {
            if (scope != null) {
                scope.track(installed);
            }
            if (timeout != null && !result.isDone()) {
                timer.set(scheduler.schedule(
                        () -> result.completeExceptionally(new TimeoutException("event wait timed out")),
                        timeout.toNanos(),
                        TimeUnit.NANOSECONDS));
            }
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        }
        if (result.isDone()) {
            cleanup(subscription, timer);
        }
        return result;
    }

    private static void cleanup(AtomicReference<Subscription> subscription, AtomicReference<ScheduledFuture<?>> timer) {
        Subscription current = subscription.getAndSet(null);
        if (current != null) {
            current.close();
        }
        ScheduledFuture<?> scheduled = timer.getAndSet(null);
        if (scheduled != null) {
            scheduled.cancel(false);
        }
    }
}
