package dev.melontricks.eventfw.event;

/**
 * Marks a value as eligible for publication through an event bus.
 *
 * <p>The framework imposes no fields, timestamps, identifiers, or mutability requirements on an event. Records and
 * other immutable value types are recommended, especially when {@code publishAsync} or concurrent publishers are used.
 * The same object reference is delivered to every matching listener; the bus neither clones nor serializes event
 * instances.
 */
public interface Event {}
