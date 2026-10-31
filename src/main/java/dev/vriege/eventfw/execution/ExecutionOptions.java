package dev.vriege.eventfw.execution;

import dev.vriege.eventfw.event.Event;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Immutable per-subscription execution policy, applied only by asynchronous publication.
 *
 * <p>Capacity counts queued and running invocations across all keys of one subscription. Saturation fails the new
 * invocation with a rejection; existing work is preserved. Equal non-null keys execute in arrival order, waiting for
 * each handler's stage. Different keys may overlap. Timeout includes queue time and does not interrupt user work; a
 * timed-out invocation retains its queue slot and ordering position until its actual work ends.
 *
 * @param executor non-null borrowed executor, never shut down by the framework
 * @param serialKey optional key extractor, null for no cross-publication serialization
 * @param capacity positive maximum accepted outstanding invocations per subscription
 * @param timeout optional positive invocation timeout, null for none
 * @param <E> event type
 */
public record ExecutionOptions<E extends Event>(
        Executor executor, Function<? super E, ?> serialKey, int capacity, Duration timeout) {
    /** Validates the executor, capacity, and optional timeout. */
    public ExecutionOptions {
        Objects.requireNonNull(executor, "executor");
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (timeout != null && (timeout.isNegative() || timeout.isZero())) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (timeout != null) {
            timeout = Duration.ofNanos(timeout.toNanos());
        }
    }
}
