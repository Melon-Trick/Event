package dev.melontricks.eventfw.bus;

/** Selects how a bus relates a runtime event class to subscribed types. */
public enum TypeMatching {
    /** Delivers only when the runtime class and subscribed class are identical. */
    EXACT,
    /** Delivers to the runtime class and all subscribed assignable superclasses and interfaces. */
    POLYMORPHIC
}
