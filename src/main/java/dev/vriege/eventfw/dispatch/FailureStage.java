package dev.vriege.eventfw.dispatch;

/** Identifies the subscription operation that produced a contained failure. */
public enum FailureStage {
    /** Failure occurred while deciding whether a handler was eligible. */
    FILTER,
    /** Failure occurred during handler invocation. */
    HANDLER,
    /** Publication mode, executor submission, or queue admission rejected an invocation. */
    EXECUTION,
    /** An invocation exceeded its observation timeout; its underlying work may still run. */
    TIMEOUT
}
