package dev.vriege.eventfw.diagnostic;

/** Structural selection result; filters are never executed by diagnostic inspection. */
public enum Eligibility {
    /** Structurally eligible; a filter or a later concurrent state change may still prevent invocation. */
    ELIGIBLE,
    /** Subscription has been closed. */
    CLOSED,
    /** Subscription is temporarily paused. */
    PAUSED,
    /** Channel differs from the publication channel. */
    CHANNEL_MISMATCH,
    /** Publication phase is not accepted. */
    PHASE_MISMATCH,
    /** Event is cancelled and the subscription does not receive cancelled events. */
    CANCELLED
}
