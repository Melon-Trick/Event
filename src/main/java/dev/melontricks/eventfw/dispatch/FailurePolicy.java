package dev.melontricks.eventfw.dispatch;

/** Determines whether a filter or handler failure interrupts the current dispatch. */
public enum FailurePolicy {
    /** Records and reports each failure, then evaluates later subscriptions. */
    CONTINUE,
    /** Reports the first failure and immediately throws an {@link EventDispatchException}. */
    FAIL_FAST
}
