package dev.melontricks.eventfw.dispatch;

/** Identifies the subscription operation that produced a contained failure. */
public enum FailureStage {
    /** Failure occurred while deciding whether a handler was eligible. */
    FILTER,
    /** Failure occurred during handler invocation. */
    HANDLER
}
