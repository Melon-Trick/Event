package dev.melontricks.eventfw.dispatch;

import dev.melontricks.eventfw.event.Event;
import dev.melontricks.eventfw.listener.Subscription;
import java.util.Objects;

public record ListenerFailure(Event event, Subscription subscription, FailureStage stage, Throwable cause) {
    public ListenerFailure {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(subscription, "subscription");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(cause, "cause");
    }
}
