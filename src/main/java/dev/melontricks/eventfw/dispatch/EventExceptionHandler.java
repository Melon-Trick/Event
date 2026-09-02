package dev.melontricks.eventfw.dispatch;

import java.util.Objects;

/**
 * Observes each filter or listener failure before the configured failure policy is applied.
 *
 * <p>Implementations execute synchronously on the publishing thread and should return quickly. Throwing from this
 * callback aborts publication regardless of the configured {@link FailurePolicy}.
 */
@FunctionalInterface
public interface EventExceptionHandler {
    /**
     * Receives one non-null failure.
     *
     * @param failure contained failure with event, subscription, stage, and cause
     */
    void handle(ListenerFailure failure);

    /**
     * Returns a handler that intentionally discards failure notifications.
     *
     * <p>Failures remain available in a normally returned {@link DispatchResult}.
     *
     * @return stateless ignoring handler
     */
    static EventExceptionHandler ignoring() {
        return failure -> {};
    }

    /**
     * Returns a handler that logs failure metadata and the original stack trace at error level.
     *
     * @param logger non-null system logger
     * @return logging handler
     * @throws NullPointerException when {@code logger} is {@code null}
     */
    static EventExceptionHandler logging(System.Logger logger) {
        System.Logger checkedLogger = Objects.requireNonNull(logger, "logger");
        return failure -> checkedLogger.log(
                System.Logger.Level.ERROR,
                "Event listener " + failure.subscription().id() + " failed during " + failure.stage(),
                failure.cause());
    }
}
