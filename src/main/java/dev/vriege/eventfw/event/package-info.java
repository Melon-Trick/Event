/**
 * Defines event payload contracts and reusable cancellation state.
 *
 * <p>Application events implement {@link dev.vriege.eventfw.event.Event}. Immutable records are recommended because the
 * same event instance may be observed by multiple listeners or published from an asynchronous executor. Events that
 * model vetoable operations may implement {@link dev.vriege.eventfw.event.CancellableEvent} directly or extend
 * {@link dev.vriege.eventfw.event.AbstractCancellableEvent}.
 */
package dev.vriege.eventfw.event;
