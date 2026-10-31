package dev.vriege.eventfw.diagnostic;

import dev.vriege.eventfw.dispatch.DispatchResult;
import dev.vriege.eventfw.dispatch.EventContext;

/**
 * Observes dispatch outcomes without participating in delivery decisions. Callbacks run on the dispatch completion
 * thread and must support concurrent calls. Runtime exceptions thrown by observers are logged and isolated from event
 * delivery; JVM errors are not suppressed. Observers should not block or recursively publish without a recursion guard.
 */
public interface DispatchObserver {
    /**
     * Receives every normally completed dispatch, including those containing failures or no invoked listeners.
     *
     * @param context immutable metadata and final propagation control
     * @param result completed dispatch summary
     */
    void completed(EventContext<?> context, DispatchResult<?> result);

    /**
     * Receives a dispatch that failed before producing a result, for example under fail-fast delivery. Validation and
     * initial executor-rejection failures that occur before a dispatch starts do not produce observer notifications.
     *
     * @param context metadata of the started dispatch
     * @param cause original failure, unwrapped from asynchronous completion wrappers
     */
    void failed(EventContext<?> context, Throwable cause);
}
