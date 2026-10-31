package dev.vriege.eventfw.execution;

import dev.vriege.eventfw.event.Event;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Mutable, reusable, non-thread-safe builder for a subscription's asynchronous execution policy. Defaults are direct
 * execution, no serialization, capacity 256, and no timeout. Configuration has no side effects.
 *
 * @param <E> event type
 */
public final class ExecutionBuilder<E extends Event> {
    private Executor executor;
    private Function<? super E, ?> serialKey;
    private int capacity = 256;
    private Duration timeout;

    /** Creates a builder with the documented defaults. */
    public ExecutionBuilder() {
        executor = Runnable::run;
    }

    /**
     * Selects the borrowed executor used to start handlers; continuations may run on stage-completion threads.
     *
     * @param value non-null executor
     * @return this builder
     */
    public ExecutionBuilder<E> executor(Executor value) {
        executor = Objects.requireNonNull(value, "executor");
        return this;
    }

    /**
     * Serializes all invocations of this subscription, including asynchronous work until its actual completion.
     *
     * @return this builder
     */
    public ExecutionBuilder<E> sequential() {
        return serialBy(_ -> Boolean.TRUE);
    }

    /**
     * Serializes invocations with equal keys. A null extracted key fails that invocation before enqueueing.
     *
     * @param value non-null key extractor; invoked before executor submission
     * @return this builder
     */
    public ExecutionBuilder<E> serialBy(Function<? super E, ?> value) {
        serialKey = Objects.requireNonNull(value, "serialKey");
        return this;
    }

    /**
     * Bounds running plus queued invocations; excess work is rejected without dropping existing work.
     *
     * @param value positive capacity
     * @return this builder
     * @throws IllegalArgumentException if capacity is not positive
     */
    public ExecutionBuilder<E> queueCapacity(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        capacity = value;
        return this;
    }

    /**
     * Bounds observed invocation duration, including queue time, without interrupting or cancelling user work.
     *
     * @param value positive duration representable in nanoseconds
     * @return this builder
     * @throws IllegalArgumentException if duration is not positive
     * @throws ArithmeticException if duration exceeds the nanosecond range
     */
    public ExecutionBuilder<E> timeout(Duration value) {
        Objects.requireNonNull(value, "timeout");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        timeout = Duration.ofNanos(value.toNanos());
        return this;
    }

    /**
     * Captures an immutable policy; later changes to this builder do not affect it.
     *
     * @return validated execution policy
     */
    public ExecutionOptions<E> build() {
        return new ExecutionOptions<>(executor, serialKey, capacity, timeout);
    }
}
