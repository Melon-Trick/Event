/**
 * Describes an event dispatch and its failure, phase, timing, and propagation semantics.
 *
 * <p>Every completed publication returns a {@link dev.vriege.eventfw.dispatch.DispatchResult}. Listener and filter
 * failures are represented by {@link dev.vriege.eventfw.dispatch.ListenerFailure}. A bus configured for fail-fast
 * behavior throws {@link dev.vriege.eventfw.dispatch.EventDispatchException} instead of returning normally after the
 * first failure.
 */
package dev.vriege.eventfw.dispatch;
