package dev.melontricks.eventfw.bus;

/** Provides the supported entry points for creating event buses. */
public final class EventBuses {
    private EventBuses() {}

    /**
     * Creates a bus with polymorphic matching, continuing failure handling, system logging, the common fork-join pool
     * for asynchronous dispatch, and a maximum nesting depth of {@code 64}.
     *
     * @return a new independent event bus
     */
    public static EventBus create() {
        return builder().build();
    }

    /**
     * Creates a mutable policy builder initialized with the default settings.
     *
     * @return a new builder
     */
    public static EventBusBuilder builder() {
        return new EventBusBuilder();
    }
}
