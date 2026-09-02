package dev.melontricks.eventfw.bus;

public record EventBusMetrics(
        long publishedEvents,
        long listenerInvocations,
        long skippedListeners,
        long listenerFailures,
        int activeSubscriptions) {}
