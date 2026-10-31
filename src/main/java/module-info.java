/**
 * Provides a type-safe event framework for synchronous and asynchronous in-process communication.
 *
 * <p>The module exports contracts for events, buses, listeners, dispatch results, and annotated subscribers. Runtime
 * implementation packages remain encapsulated and may change without affecting source or binary compatibility of the
 * exported API.
 */
module dev.vriege.eventfw {
    requires jdk.jfr;

    exports dev.vriege.eventfw.annotation;
    exports dev.vriege.eventfw.bus;
    exports dev.vriege.eventfw.dispatch;
    exports dev.vriege.eventfw.event;
    exports dev.vriege.eventfw.listener;
    exports dev.vriege.eventfw.await;
    exports dev.vriege.eventfw.diagnostic;
    exports dev.vriege.eventfw.execution;
    exports dev.vriege.eventfw.scope;
}
