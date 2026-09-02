/**
 * Provides declarative listener registration through {@link dev.melontricks.eventfw.annotation.Subscribe}.
 *
 * <p>Annotated methods are discovered across class and interface hierarchies, validated before any subscription is
 * installed, and cached per listener class. Private methods are supported when the Java module containing the listener
 * allows reflective access.
 */
package dev.melontricks.eventfw.annotation;
