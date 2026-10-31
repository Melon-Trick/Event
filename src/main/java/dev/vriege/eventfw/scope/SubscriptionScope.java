package dev.vriege.eventfw.scope;

import dev.vriege.eventfw.listener.Registration;
import dev.vriege.eventfw.listener.Subscription;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Owns a group of subscriptions independently of their bus or identity-based owner.
 *
 * <p>All operations are thread-safe. Closing permanently rejects new registrations and closes existing subscriptions. A
 * registration racing with closure is closed before rejection. Completed subscriptions are automatically detached.
 * Closing a scope does not cancel invocations already claimed, including queued work, and never closes a bus or
 * executor.
 */
public final class SubscriptionScope implements Registration {
    private final Set<Subscription> members;
    private boolean closed;

    /** Creates an empty, open scope. */
    public SubscriptionScope() {
        members = new LinkedHashSet<>();
    }

    /**
     * Attaches every subscription in a registration, preserving the original handle.
     *
     * @param registration non-null registration to own
     * @param <R> registration handle type
     * @return the supplied handle
     * @throws IllegalStateException if this scope is closed; the supplied registration is closed first
     * @throws NullPointerException if the registration is null
     */
    public <R extends Registration> R track(R registration) {
        Objects.requireNonNull(registration, "registration");
        synchronized (this) {
            if (!closed) {
                for (Subscription subscription : registration.subscriptions()) {
                    members.add(subscription);
                    subscription.whenClosed().thenRun(() -> detach(subscription));
                }
                return registration;
            }
        }
        registration.close();
        throw new IllegalStateException("subscription scope is closed");
    }

    private synchronized void detach(Subscription subscription) {
        members.remove(subscription);
    }

    /**
     * Reports permanent scope closure, including when the scope was empty.
     *
     * @return whether close has been called
     */
    public synchronized boolean closed() {
        return closed;
    }

    @Override
    public synchronized boolean active() {
        return !members.isEmpty();
    }

    @Override
    public synchronized List<Subscription> subscriptions() {
        return List.copyOf(members);
    }

    /** Pauses subscriptions currently attached; subscriptions attached later are unaffected. */
    public void pause() {
        subscriptions().forEach(Subscription::pause);
    }

    /** Resumes subscriptions currently attached without reviving closed subscriptions. */
    public void resume() {
        subscriptions().forEach(Subscription::resume);
    }

    @Override
    public void close() {
        List<Subscription> snapshot;
        synchronized (this) {
            closed = true;
            snapshot = List.copyOf(members);
            members.clear();
        }
        snapshot.reversed().forEach(Subscription::close);
    }
}
