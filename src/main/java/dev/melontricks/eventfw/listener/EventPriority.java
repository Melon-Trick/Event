package dev.melontricks.eventfw.listener;

public enum EventPriority {
    LOWEST(-1_000),
    LOW(-100),
    NORMAL(0),
    HIGH(100),
    HIGHEST(1_000),
    MONITOR(-10_000);

    private final int value;

    EventPriority(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
