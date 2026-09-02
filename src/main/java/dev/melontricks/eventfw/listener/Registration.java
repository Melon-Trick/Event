package dev.melontricks.eventfw.listener;

import java.util.List;

/**
 * Represents idempotently closeable registration work.
 *
 * <p>A registration may contain one subscription or aggregate every subscription discovered on an annotated listener.
 * Closing it repeatedly is safe. Aggregate registrations close their subscriptions in reverse installation order.
 */
public interface Registration extends AutoCloseable {
    /**
     * Returns whether at least one contained subscription remains registered.
     *
     * @return {@code true} while this registration owns live subscriptions
     */
    boolean active();

    /**
     * Returns an immutable snapshot of subscriptions created by this registration.
     *
     * @return subscriptions in installation order
     */
    List<Subscription> subscriptions();

    /** Removes every still-live subscription represented by this handle. */
    @Override
    void close();
}
