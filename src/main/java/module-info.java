/**
 * Provides a type-safe event framework for synchronous and asynchronous in-process communication.
 *
 * <p>The module exports contracts for events, buses, listeners, dispatch results, and annotated subscribers. Runtime
 * implementation packages remain encapsulated and may change without affecting source or binary compatibility of the
 * exported API.
 */
module dev.vriege.eventfw {
    exports dev.vriege.eventfw.annotation;
    exports dev.vriege.eventfw.bus;
    exports dev.vriege.eventfw.dispatch;
    exports dev.vriege.eventfw.event;
    exports dev.vriege.eventfw.listener;
}
