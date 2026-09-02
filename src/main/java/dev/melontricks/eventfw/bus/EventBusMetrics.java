package dev.melontricks.eventfw.bus;

/**
 * Immutable, weakly consistent snapshot of event-bus counters.
 *
 * <p>Concurrent activity may advance different counters between reads used to construct a snapshot, so consumers should
 * use these values for diagnostics and telemetry rather than transactional decisions.
 *
 * @param publishedEvents publications that entered dispatch
 * @param listenerInvocations handler invocation attempts, including attempts that failed
 * @param skippedListeners matching candidates skipped by state, phase, cancellation, filtering, or stopped propagation
 * @param listenerFailures contained filter and handler failures
 * @param activeSubscriptions currently registered subscriptions, including paused subscriptions
 */
public record EventBusMetrics(
        long publishedEvents,
        long listenerInvocations,
        long skippedListeners,
        long listenerFailures,
        int activeSubscriptions) {}
