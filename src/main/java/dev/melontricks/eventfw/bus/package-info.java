/**
 * Provides event-bus creation, configuration, publication, registration, lifecycle, and metrics.
 *
 * <p>A default bus is created with {@link dev.melontricks.eventfw.bus.EventBuses#create()}. Advanced policies are
 * selected through {@link dev.melontricks.eventfw.bus.EventBuses#builder()}. Bus instances are safe for concurrent
 * publication and subscription changes. Synchronous publication executes on the caller's thread; asynchronous
 * publication executes on the configured executor.
 *
 * <p>Listener order is deterministic: higher priority first, then the most specific subscribed event type, then
 * registration order. Each dispatch observes a stable candidate snapshot while still checking the live state of every
 * subscription before invoking it.
 */
package dev.melontricks.eventfw.bus;
