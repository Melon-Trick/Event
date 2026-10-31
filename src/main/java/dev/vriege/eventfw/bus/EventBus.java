package dev.vriege.eventfw.bus;

import dev.vriege.eventfw.await.AwaitBuilder;
import dev.vriege.eventfw.diagnostic.SubscriptionDiagnostic;
import dev.vriege.eventfw.dispatch.DispatchOptions;
import dev.vriege.eventfw.event.Event;
import dev.vriege.eventfw.event.EventKey;
import dev.vriege.eventfw.listener.ContextualEventHandler;
import dev.vriege.eventfw.listener.EventHandler;
import dev.vriege.eventfw.listener.EventSubscriptionBuilder;
import dev.vriege.eventfw.listener.Registration;
import dev.vriege.eventfw.listener.Subscription;
import dev.vriege.eventfw.scope.SubscriptionScope;
import java.util.List;
import java.util.Objects;

/**
 * Combines event publication, listener registration, lifecycle management, and metrics.
 *
 * <p>Implementations returned by {@link EventBuses} are safe for concurrent use. Publication is synchronous unless an
 * asynchronous method is called. Subscription changes during publication affect later eligibility checks but do not
 * alter the ordered candidate snapshot already selected for that dispatch.
 *
 * <p>A bus owns subscription state but does not own its configured asynchronous executor. Closing the bus permanently
 * rejects new publications and registrations, removes all subscriptions, and leaves the executor running.
 */
public interface EventBus extends EventPublisher, AutoCloseable {
    /**
     * Configures subscriptions on a typed channel without installing a listener.
     *
     * @param key non-null event contract and channel
     * @param <E> event type
     * @return new subscription builder
     */
    default <E extends Event> EventSubscriptionBuilder<E> on(EventKey<E> key) {
        return on(key.eventType()).channel(key.name());
    }

    /**
     * Configures an inactive, cancellable wait for a matching event.
     *
     * @param eventType non-null event contract
     * @param <E> event type
     * @return new wait builder; no listener is installed until start
     */
    <E extends Event> AwaitBuilder<E> await(Class<E> eventType);

    /**
     * Configures an inactive wait on a typed channel.
     *
     * @param key non-null event contract and channel
     * @param <E> event type
     * @return new wait builder
     */
    default <E extends Event> AwaitBuilder<E> await(EventKey<E> key) {
        return await(key.eventType()).channel(key.name());
    }

    /**
     * Registers an annotated listener and attaches its registration to a scope. Scope rejection removes all newly
     * installed subscriptions. The scope is independent of the listener's identity-based owner.
     *
     * @param listener non-null annotated listener
     * @param scope non-null scope
     * @return aggregate registration
     */
    default Registration register(Object listener, SubscriptionScope scope) {
        Objects.requireNonNull(scope, "scope");
        return scope.track(register(listener));
    }

    /**
     * Returns current subscriptions ordered by installation identifier, including paused subscriptions.
     *
     * @return immutable, weakly consistent snapshot of live handles
     */
    List<Subscription> subscriptions();

    /**
     * Explains structural eligibility in dispatch order without executing filters or handlers or consuming limits.
     * Concurrent changes may invalidate the explanation immediately. Only type-matching candidates are included.
     *
     * @param event non-null event to inspect
     * @param options non-null selection options
     * @return immutable diagnostic snapshot
     */
    List<SubscriptionDiagnostic> inspect(Event event, DispatchOptions options);
    /**
     * Registers a simple handler with default options.
     *
     * @param eventType non-null event class or interface to match
     * @param handler non-null handler receiving matching events
     * @param <E> subscribed event type
     * @return live, idempotently closeable subscription
     * @throws NullPointerException when an argument is {@code null}
     * @throws IllegalStateException when the bus is closed
     */
    <E extends Event> Subscription subscribe(Class<E> eventType, EventHandler<? super E> handler);

    /**
     * Registers a context-aware handler with default options.
     *
     * @param eventType non-null event class or interface to match
     * @param handler non-null handler receiving both event and dispatch context
     * @param <E> subscribed event type
     * @return live, idempotently closeable subscription
     * @throws NullPointerException when an argument is {@code null}
     * @throws IllegalStateException when the bus is closed
     */
    <E extends Event> Subscription subscribe(Class<E> eventType, ContextualEventHandler<E> handler);

    /**
     * Starts fluent configuration for a programmatic subscription.
     *
     * @param eventType non-null event class or interface to match
     * @param <E> subscribed event type
     * @return a mutable builder that may create multiple independent subscriptions
     * @throws NullPointerException when {@code eventType} is {@code null}
     * @throws IllegalStateException when the bus is closed
     */
    <E extends Event> EventSubscriptionBuilder<E> on(Class<E> eventType);

    /**
     * Discovers and registers every valid {@link dev.vriege.eventfw.annotation.Subscribe} method on an object.
     *
     * @param listener non-null listener object, also used as subscription owner
     * @return aggregate registration closing only subscriptions created by this call
     * @throws NullPointerException when {@code listener} is {@code null}
     * @throws dev.vriege.eventfw.annotation.InvalidListenerException when an annotated method is invalid or
     *     inaccessible
     * @throws IllegalStateException when the bus is closed
     */
    Registration register(Object listener);

    /**
     * Removes every programmatic or annotated subscription owned by the exact object reference.
     *
     * <p>Ownership uses identity rather than {@link Object#equals(Object)}. Passing {@code null} is a no-op.
     *
     * @param owner owner reference used during subscription
     * @return number of live subscriptions removed
     */
    int unregister(Object owner);

    /**
     * Removes all currently registered subscriptions without closing the bus.
     *
     * @return number of live subscriptions removed
     */
    int clear();

    /**
     * Returns the current number of registered subscriptions, including paused subscriptions.
     *
     * @return non-negative subscription count
     */
    int subscriptionCount();

    /**
     * Takes a weakly consistent snapshot of operational counters.
     *
     * @return immutable metrics snapshot
     */
    EventBusMetrics metrics();

    /**
     * Returns whether this bus has been permanently closed.
     *
     * @return {@code true} after the first successful close
     */
    boolean closed();

    /** Removes every subscription and permanently closes this bus. */
    @Override
    void close();
}
