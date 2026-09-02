package dev.melontricks.eventfw.bus;

import dev.melontricks.eventfw.dispatch.EventExceptionHandler;
import dev.melontricks.eventfw.dispatch.FailurePolicy;
import dev.melontricks.eventfw.internal.bus.DefaultEventBus;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public final class EventBusBuilder {
    private static final String VALUE_PARAMETER = "value";

    private TypeMatching typeMatching = TypeMatching.POLYMORPHIC;
    private FailurePolicy failurePolicy = FailurePolicy.CONTINUE;
    private EventExceptionHandler exceptionHandler =
            EventExceptionHandler.logging(System.getLogger("dev.melontricks.eventfw"));
    private Executor asyncExecutor = ForkJoinPool.commonPool();
    private int maximumNestingDepth = 64;

    EventBusBuilder() {}

    public EventBusBuilder typeMatching(TypeMatching value) {
        typeMatching = Objects.requireNonNull(value, VALUE_PARAMETER);
        return this;
    }

    public EventBusBuilder failurePolicy(FailurePolicy value) {
        failurePolicy = Objects.requireNonNull(value, VALUE_PARAMETER);
        return this;
    }

    public EventBusBuilder exceptionHandler(EventExceptionHandler value) {
        exceptionHandler = Objects.requireNonNull(value, VALUE_PARAMETER);
        return this;
    }

    public EventBusBuilder asyncExecutor(Executor value) {
        asyncExecutor = Objects.requireNonNull(value, VALUE_PARAMETER);
        return this;
    }

    public EventBusBuilder maximumNestingDepth(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("maximumNestingDepth must be positive");
        }
        maximumNestingDepth = value;
        return this;
    }

    public EventBus build() {
        return new DefaultEventBus(typeMatching, failurePolicy, exceptionHandler, asyncExecutor, maximumNestingDepth);
    }
}
