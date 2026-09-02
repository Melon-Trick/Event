package dev.melontricks.eventfw.bus;

public final class EventBuses {
    private EventBuses() {}

    public static EventBus create() {
        return builder().build();
    }

    public static EventBusBuilder builder() {
        return new EventBusBuilder();
    }
}
