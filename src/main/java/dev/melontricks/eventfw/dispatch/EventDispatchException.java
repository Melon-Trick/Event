package dev.melontricks.eventfw.dispatch;

import java.io.Serial;

/**
 * Indicates that fail-fast publication stopped after a filter or handler failure.
 *
 * <p>The original runtime exception is available through {@link #getCause()}. The richer {@link ListenerFailure}
 * descriptor is retained for normal in-process use but is intentionally transient to preserve the serialization
 * contract inherited from {@link RuntimeException} without requiring events and subscriptions to be serializable.
 */
public final class EventDispatchException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final transient ListenerFailure failure;

    /**
     * Creates an exception from the failure that stopped dispatch.
     *
     * @param failure non-null failure descriptor
     * @throws NullPointerException when {@code failure} is {@code null}
     */
    public EventDispatchException(ListenerFailure failure) {
        super(
                "Event subscription " + failure.subscription().id() + " failed during " + failure.stage(),
                failure.cause());
        this.failure = failure;
    }

    /**
     * Returns the complete failure descriptor retained by the live exception.
     *
     * @return event, subscription, stage, and cause of the dispatch failure
     * @throws IllegalStateException when called on a deserialized instance, whose descriptor is transient
     */
    public ListenerFailure failure() {
        if (failure == null) {
            throw new IllegalStateException("failure descriptor is unavailable after deserialization");
        }
        return failure;
    }
}
