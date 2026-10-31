package dev.vriege.eventfw.listener;

import dev.vriege.eventfw.dispatch.EventPhase;
import dev.vriege.eventfw.event.Event;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * Exposes the immutable configuration and mutable lifecycle of one registered listener.
 *
 * <p>A subscription handle strongly references its handler, filter, and optional owner. Removal releases the bus's
 * references to the subscription; applications must also release retained handles to allow captured objects to be
 * collected. Lifecycle operations are thread-safe and idempotent. Closing is permanent; pausing is reversible only
 * while the subscription remains registered.
 */
public interface Subscription extends Registration {
    /**
     * Observes removal without exposing a completable internal future. Completion may precede the end of an invocation
     * already claimed, including the last allowed invocation. Callbacks should be short and non-blocking.
     *
     * @return read-only completion stage completed when this subscription is removed
     */
    CompletionStage<Void> whenClosed();

    /**
     * Returns the selected channel.
     *
     * @return non-null channel, empty for the default channel
     */
    String channel();

    /**
     * Returns the configured invocation-attempt limit; failed handlers consume attempts, rejected filters do not.
     *
     * @return positive limit or {@link Long#MAX_VALUE} for unlimited delivery
     */
    long invocationLimit();

    /**
     * Reports whether this subscription requires asynchronous publication.
     *
     * @return true for stage-returning handlers or explicit execution policies
     */
    boolean asynchronous();
    /**
     * Returns the monotonically increasing identifier assigned by the owning bus.
     *
     * @return identifier unique within that bus instance
     */
    long id();

    /**
     * Returns the declared event type used for candidate selection.
     *
     * @return subscribed event class or interface
     */
    Class<? extends Event> eventType();

    /**
     * Returns the numeric dispatch priority.
     *
     * @return priority where greater values execute first
     */
    int priority();

    /**
     * Returns whether cancellation leaves this subscription eligible.
     *
     * @return {@code true} when cancelled events may be delivered
     */
    boolean receivesCancelledEvents();

    /**
     * Returns whether subtype instances are rejected by this subscription.
     *
     * @return {@code true} for exact runtime-type matching
     */
    boolean exactTypeOnly();

    /**
     * Returns the immutable set of accepted publication phases.
     *
     * @return non-empty phase set
     */
    Set<EventPhase> phases();

    /**
     * Returns whether the subscription is atomically consumed by its first eligible invocation.
     *
     * @return {@code true} for a single-use subscription
     */
    boolean singleUse();

    /**
     * Returns whether this live subscription is temporarily disabled.
     *
     * @return {@code true} only while registered and paused
     */
    boolean paused();

    /**
     * Temporarily disables delivery without unregistering the subscription.
     *
     * <p>Calling this method on a paused or closed subscription is a no-op. A publication that has already passed the
     * final live-state check may still complete its current invocation.
     */
    void pause();

    /**
     * Re-enables a paused subscription.
     *
     * <p>Calling this method on an enabled, closed, or consumed subscription is a no-op.
     */
    void resume();

    /**
     * Returns the optional owner used for bulk unregistration.
     *
     * @return owner reference or an empty optional for an unowned subscription
     */
    Optional<Object> owner();

    /**
     * Returns this subscription as a one-element immutable list.
     *
     * @return list containing this handle
     */
    @Override
    default List<Subscription> subscriptions() {
        return List.of(this);
    }
}
