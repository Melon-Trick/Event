package dev.melontricks.eventfw.dispatch;

/** Identifies the application-defined stage in which an event is published. */
public enum EventPhase {
    /** Publication before the operation represented by an event. */
    PRE,
    /** Ordinary publication with no before-or-after specialization. */
    DEFAULT,
    /** Publication after the operation represented by an event. */
    POST
}
