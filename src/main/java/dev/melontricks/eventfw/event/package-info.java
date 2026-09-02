/**
 * Defines event payload contracts and reusable cancellation state.
 *
 * <p>Application events implement {@link dev.melontricks.eventfw.event.Event}. Immutable records are recommended
 * because the same event instance may be observed by multiple listeners or published from an asynchronous executor.
 * Events that model vetoable operations may implement {@link dev.melontricks.eventfw.event.CancellableEvent} directly
 * or extend {@link dev.melontricks.eventfw.event.AbstractCancellableEvent}.
 */
package dev.melontricks.eventfw.event;
