/**
 * Provides a type-safe event framework for synchronous and asynchronous in-process communication.
 *
 * <p>The module exports contracts for events, buses, listeners, dispatch results, and annotated subscribers. Runtime
 * implementation packages remain encapsulated and may change without affecting source or binary compatibility of the
 * exported API.
 */
module dev.melontricks.eventfw {
    exports dev.melontricks.eventfw.annotation;
    exports dev.melontricks.eventfw.bus;
    exports dev.melontricks.eventfw.dispatch;
    exports dev.melontricks.eventfw.event;
    exports dev.melontricks.eventfw.listener;
}
