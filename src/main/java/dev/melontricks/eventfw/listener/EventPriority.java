package dev.melontricks.eventfw.listener;

/**
 * Supplies conventional numeric priorities for programmatic subscriptions.
 *
 * <p>Greater numeric values execute first. Callers may pass arbitrary integers directly to the subscription builder
 * when these presets are insufficient.
 */
public enum EventPriority {
    /** Runs substantially after normal priority. */
    LOWEST(-1_000),
    /** Runs after normal priority. */
    LOW(-100),
    /** Default priority used by simple and fluent subscriptions. */
    NORMAL(0),
    /** Runs before normal priority. */
    HIGH(100),
    /** Runs substantially before normal priority. */
    HIGHEST(1_000),
    /** Runs last and is intended for observation-oriented listeners. */
    MONITOR(-10_000);

    private final int value;

    EventPriority(int value) {
        this.value = value;
    }

    /**
     * Returns the numeric value used by dispatch ordering.
     *
     * @return priority value, where greater values execute first
     */
    public int value() {
        return value;
    }
}
