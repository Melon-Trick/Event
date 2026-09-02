package dev.melontricks.eventfw.dispatch;

import dev.melontricks.eventfw.event.Event;
import dev.melontricks.eventfw.listener.Subscription;
import java.util.Objects;

/**
 * Captures a runtime exception thrown by a subscription filter or handler.
 *
 * @param event event being dispatched when the failure occurred
 * @param subscription subscription responsible for the operation
 * @param stage failing subscription stage
 * @param cause original runtime exception
 */
public record ListenerFailure(Event event, Subscription subscription, FailureStage stage, Throwable cause) {
    /**
     * Creates an immutable failure descriptor.
     *
     * @throws NullPointerException if any component is {@code null}
     */
    public ListenerFailure {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(subscription, "subscription");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(cause, "cause");
    }
}
