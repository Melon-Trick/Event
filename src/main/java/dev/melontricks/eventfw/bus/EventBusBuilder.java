package dev.melontricks.eventfw.bus;

import dev.melontricks.eventfw.dispatch.EventExceptionHandler;
import dev.melontricks.eventfw.dispatch.FailurePolicy;
import dev.melontricks.eventfw.internal.bus.DefaultEventBus;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Configures immutable policies shared by every dispatch on a newly created bus.
 *
 * <p>The builder is mutable and not thread-safe. Reusing it after {@link #build()} does not alter buses already created
 * from it. The configured executor is borrowed: closing the resulting bus never shuts it down.
 */
public final class EventBusBuilder {
    private static final String VALUE_PARAMETER = "value";

    private TypeMatching typeMatching = TypeMatching.POLYMORPHIC;
    private FailurePolicy failurePolicy = FailurePolicy.CONTINUE;
    private EventExceptionHandler exceptionHandler =
            EventExceptionHandler.logging(System.getLogger("dev.melontricks.eventfw"));
    private Executor asyncExecutor = ForkJoinPool.commonPool();
    private int maximumNestingDepth = 64;

    EventBusBuilder() {}

    /**
     * Selects exact or polymorphic event-type matching.
     *
     * @param value non-null matching policy
     * @return this builder
     * @throws NullPointerException when {@code value} is {@code null}
     */
    public EventBusBuilder typeMatching(TypeMatching value) {
        typeMatching = Objects.requireNonNull(value, VALUE_PARAMETER);
        return this;
    }

    /**
     * Selects whether dispatch continues after a filter or handler throws a runtime exception.
     *
     * @param value non-null failure policy
     * @return this builder
     * @throws NullPointerException when {@code value} is {@code null}
     */
    public EventBusBuilder failurePolicy(FailurePolicy value) {
        failurePolicy = Objects.requireNonNull(value, VALUE_PARAMETER);
        return this;
    }

    /**
     * Selects the callback notified for every contained filter or handler failure.
     *
     * <p>The callback runs synchronously on the publishing thread before the failure policy is applied. If the callback
     * itself throws, that exception escapes publication immediately.
     *
     * @param value non-null exception handler
     * @return this builder
     * @throws NullPointerException when {@code value} is {@code null}
     */
    public EventBusBuilder exceptionHandler(EventExceptionHandler value) {
        exceptionHandler = Objects.requireNonNull(value, VALUE_PARAMETER);
        return this;
    }

    /**
     * Selects the executor used by asynchronous publication methods.
     *
     * <p>Executor rejection is reported through the returned completion stage according to the executor's own contract.
     * The framework does not close or otherwise manage this executor.
     *
     * @param value non-null executor
     * @return this builder
     * @throws NullPointerException when {@code value} is {@code null}
     */
    public EventBusBuilder asyncExecutor(Executor value) {
        asyncExecutor = Objects.requireNonNull(value, VALUE_PARAMETER);
        return this;
    }

    /**
     * Sets the maximum number of simultaneously nested synchronous publications on one thread.
     *
     * <p>The top-level dispatch has nesting depth {@code 0}. A listener can synchronously publish another event until
     * this limit would be exceeded, at which point publication throws an {@link IllegalStateException}. The limit
     * prevents accidental unbounded recursive event cycles.
     *
     * @param value strictly positive maximum depth
     * @return this builder
     * @throws IllegalArgumentException when {@code value} is less than {@code 1}
     */
    public EventBusBuilder maximumNestingDepth(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("maximumNestingDepth must be positive");
        }
        maximumNestingDepth = value;
        return this;
    }

    /**
     * Creates an independent bus using a snapshot of the current policies.
     *
     * @return a new open event bus
     */
    public EventBus build() {
        return new DefaultEventBus(typeMatching, failurePolicy, exceptionHandler, asyncExecutor, maximumNestingDepth);
    }
}
