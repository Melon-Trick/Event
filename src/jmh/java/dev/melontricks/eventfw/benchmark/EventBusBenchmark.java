package dev.melontricks.eventfw.benchmark;

import dev.melontricks.eventfw.bus.EventBus;
import dev.melontricks.eventfw.bus.EventBuses;
import dev.melontricks.eventfw.dispatch.DispatchResult;
import dev.melontricks.eventfw.event.Event;
import dev.melontricks.eventfw.listener.Subscription;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
public class EventBusBenchmark {
    @Benchmark
    public DispatchResult<BenchmarkEvent> publish(DispatchState state) {
        return state.bus.publish(state.event);
    }

    @Benchmark
    public Subscription subscribeAndClose(MutationState state) {
        Subscription subscription = state.bus.subscribe(BenchmarkEvent.class, _ -> {});
        subscription.close();
        return subscription;
    }

    @State(Scope.Thread)
    public static class DispatchState {
        @Param({"0", "1", "8", "32", "128"})
        public int listenerCount;

        private EventBus bus;
        private BenchmarkEvent event;

        @Setup(Level.Iteration)
        public void setUp() {
            bus = EventBuses.builder().build();
            event = new BenchmarkEvent();
            for (int index = 0; index < listenerCount; index++) {
                bus.subscribe(BenchmarkEvent.class, _ -> {});
            }
            bus.publish(event);
        }

        @TearDown(Level.Iteration)
        public void tearDown() {
            bus.close();
        }
    }

    @State(Scope.Thread)
    public static class MutationState {
        private EventBus bus;

        @Setup(Level.Iteration)
        public void setUp() {
            bus = EventBuses.builder().build();
        }

        @TearDown(Level.Iteration)
        public void tearDown() {
            bus.close();
        }
    }

    private record BenchmarkEvent() implements Event {}
}
