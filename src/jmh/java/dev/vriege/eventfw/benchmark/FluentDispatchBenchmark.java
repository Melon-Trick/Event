package dev.vriege.eventfw.benchmark;

import dev.vriege.eventfw.annotation.Subscribe;
import dev.vriege.eventfw.bus.EventBus;
import dev.vriege.eventfw.bus.EventBuses;
import dev.vriege.eventfw.dispatch.DispatchResult;
import dev.vriege.eventfw.event.Event;
import dev.vriege.eventfw.scope.SubscriptionScope;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class FluentDispatchBenchmark {
    @Benchmark
    public DispatchResult<BenchmarkEvent> filtered(DispatchState state) {
        return state.filtered.publish(state.event);
    }

    @Benchmark
    public DispatchResult<BenchmarkEvent> annotated(DispatchState state, Blackhole blackhole) {
        DispatchResult<BenchmarkEvent> result = state.annotated.publish(state.event);
        blackhole.consume(state.listener.total);
        return result;
    }

    @Benchmark
    public DispatchResult<BenchmarkEvent> asynchronous(DispatchState state) {
        return state.asynchronous
                .publishAsync(state.event)
                .toCompletableFuture()
                .join();
    }

    @State(Scope.Thread)
    public static class DispatchState {
        private EventBus filtered;
        private EventBus annotated;
        private EventBus asynchronous;
        private SubscriptionScope scope;
        private BenchmarkEvent event;
        private AnnotatedListener listener;

        @Setup
        public void setUp() {
            event = new BenchmarkEvent(1);
            scope = new SubscriptionScope();
            filtered = EventBuses.create();
            filtered.on(BenchmarkEvent.class)
                    .scope(scope)
                    .filter(value -> value.number() > 0)
                    .filter((_, context) -> !context.propagationStopped())
                    .subscribe(_ -> {});
            annotated = EventBuses.create();
            listener = new AnnotatedListener();
            annotated.register(listener);
            asynchronous = EventBuses.builder().asyncExecutor(Runnable::run).build();
            asynchronous.on(BenchmarkEvent.class).subscribeAsync(_ -> CompletableFuture.completedFuture(null));
        }

        @TearDown
        public void tearDown() {
            scope.close();
            filtered.close();
            annotated.close();
            asynchronous.close();
        }
    }

    private static final class AnnotatedListener {
        private long total;

        @Subscribe
        private void accept(BenchmarkEvent event) {
            total += event.number();
        }
    }

    private record BenchmarkEvent(int number) implements Event {}
}
