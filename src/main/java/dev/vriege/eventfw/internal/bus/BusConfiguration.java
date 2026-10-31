package dev.vriege.eventfw.internal.bus;

import dev.vriege.eventfw.bus.TypeMatching;
import dev.vriege.eventfw.diagnostic.DispatchObserver;
import dev.vriege.eventfw.dispatch.EventExceptionHandler;
import dev.vriege.eventfw.dispatch.FailurePolicy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

public record BusConfiguration(
        TypeMatching typeMatching,
        FailurePolicy failurePolicy,
        EventExceptionHandler exceptionHandler,
        Executor asyncExecutor,
        int maximumNestingDepth,
        ScheduledExecutorService scheduler,
        Map<String, Executor> namedExecutors,
        List<DispatchObserver> observers) {
    public BusConfiguration {
        namedExecutors = Map.copyOf(namedExecutors);
        observers = List.copyOf(observers);
    }
}
