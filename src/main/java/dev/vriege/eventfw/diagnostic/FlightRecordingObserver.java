package dev.vriege.eventfw.diagnostic;

import dev.vriege.eventfw.dispatch.DispatchResult;
import dev.vriege.eventfw.dispatch.EventContext;
import dev.vriege.eventfw.internal.diagnostic.DispatchRecordingEvent;
import jdk.jfr.EventType;

/**
 * Emits Java Flight Recorder events named {@code dev.vriege.eventfw.Dispatch} for observed dispatch outcomes.
 *
 * <p>Register an instance with the bus builder and enable this event in a recording. Normally completed dispatches
 * include elapsed nanoseconds, invocation and failure counts, channel, phase, sequence, and runtime event class.
 * Aborted dispatches include the failure's class name instead of partial counts or duration. Event payloads, attribute
 * values, owner references, and exception messages are never recorded. Disabled recordings skip payload construction.
 * Instances are immutable and thread-safe and do not create, start, stop, or own a recording.
 */
public final class FlightRecordingObserver implements DispatchObserver {
    private final EventType eventType;

    /** Creates an observer for the framework's standard dispatch recording event. */
    public FlightRecordingObserver() {
        eventType = EventType.getEventType(DispatchRecordingEvent.class);
    }

    /**
     * Returns the stable event name used by recording configurations.
     *
     * @return the framework dispatch JFR event name
     */
    public String eventName() {
        return eventType.getName();
    }

    @Override
    public void completed(EventContext<?> context, DispatchResult<?> result) {
        if (eventType.isEnabled()) {
            DispatchRecordingEvent event = new DispatchRecordingEvent();
            populate(event, context);
            event.invokedListeners = result.invokedListeners();
            event.failureCount = result.failures().size();
            event.elapsedNanos = result.duration().toNanos();
            event.commit();
        }
    }

    @Override
    public void failed(EventContext<?> context, Throwable cause) {
        if (eventType.isEnabled()) {
            DispatchRecordingEvent event = new DispatchRecordingEvent();
            populate(event, context);
            event.failureType = cause.getClass().getName();
            event.commit();
        }
    }

    private static void populate(DispatchRecordingEvent target, EventContext<?> context) {
        target.eventType = context.event().getClass().getName();
        target.channel = context.channel();
        target.phase = context.phase().name();
        target.sequence = context.sequence();
    }
}
