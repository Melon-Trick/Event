package dev.vriege.eventfw.bus;

import static org.junit.jupiter.api.Assertions.*;

import dev.vriege.eventfw.diagnostic.DispatchObserver;
import dev.vriege.eventfw.diagnostic.FlightRecordingObserver;
import dev.vriege.eventfw.dispatch.DispatchResult;
import dev.vriege.eventfw.dispatch.EventContext;
import dev.vriege.eventfw.dispatch.FailurePolicy;
import dev.vriege.eventfw.event.Event;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DispatchObservationTest {
    @Test
    void observersReceiveSyncAndAsyncOutcomesIncludingDeadLetters() {
        List<String> outcomes = new ArrayList<>();
        DispatchObserver observer = observer(outcomes, false);
        try (EventBus bus = EventBuses.builder()
                .asyncExecutor(Runnable::run)
                .observe(observer)
                .build()) {
            bus.publish(new TestEvent());
            bus.publishAsync(new TestEvent()).toCompletableFuture().join();
            assertEquals(List.of("completed:0", "completed:0"), outcomes);
        }
    }

    @Test
    void observerFailuresAreIsolatedAndLaterObserversStillRun() {
        List<String> outcomes = new ArrayList<>();
        try (EventBus bus = EventBuses.builder()
                .asyncExecutor(Runnable::run)
                .exceptionHandler(_ -> {})
                .failurePolicy(FailurePolicy.FAIL_FAST)
                .observe(observer(new ArrayList<>(), true))
                .observe(observer(outcomes, false))
                .build()) {
            bus.publish(new TestEvent());
            bus.on(TestEvent.class)
                    .subscribeAsync(_ -> CompletableFuture.failedFuture(new IllegalStateException("failed")));
            var failedDispatch = bus.publishAsync(new TestEvent()).toCompletableFuture();
            assertThrows(CompletionException.class, failedDispatch::join);
            assertEquals(List.of("completed:0", "failed"), outcomes);
            assertThrows(dev.vriege.eventfw.dispatch.EventDispatchException.class, () -> bus.publish(new TestEvent()));
            assertEquals(List.of("completed:0", "failed", "failed"), outcomes);
        }
    }

    @Test
    void flightRecorderCapturesBothSuccessfulAndFailedDispatches(@TempDir Path directory) throws Exception {
        FlightRecordingObserver observer = new FlightRecordingObserver();
        Path recordingPath = directory.resolve("dispatch.jfr");
        try (Recording recording = new Recording();
                EventBus bus = EventBuses.builder()
                        .observe(observer)
                        .exceptionHandler(_ -> {})
                        .failurePolicy(FailurePolicy.FAIL_FAST)
                        .build()) {
            recording.enable(observer.eventName());
            recording.start();
            bus.publish(new TestEvent());
            bus.subscribe(TestEvent.class, _ -> {
                throw new IllegalStateException("failed");
            });
            assertThrows(dev.vriege.eventfw.dispatch.EventDispatchException.class, () -> bus.publish(new TestEvent()));
            recording.stop();
            recording.dump(recordingPath);
        }
        var events = RecordingFile.readAllEvents(recordingPath).stream()
                .filter(event -> event.getEventType().getName().equals(observer.eventName()))
                .toList();
        assertEquals(2, events.size());
        assertEquals(TestEvent.class.getName(), events.getFirst().getString("eventType"));
        assertEquals("DEFAULT", events.getFirst().getString("phase"));
        assertTrue(events.getFirst().getLong("elapsedNanos") >= 0);
        assertNotNull(events.getLast().getString("failureType"));
    }

    private static DispatchObserver observer(List<String> outcomes, boolean fail) {
        return new DispatchObserver() {
            @Override
            public void completed(EventContext<?> context, DispatchResult<?> result) {
                if (fail) {
                    throw new IllegalStateException("observer failure");
                }
                outcomes.add("completed:" + result.invokedListeners());
            }

            @Override
            public void failed(EventContext<?> context, Throwable cause) {
                if (fail) {
                    throw new IllegalStateException("observer failure");
                }
                outcomes.add("failed");
            }
        };
    }

    private record TestEvent() implements Event {}
}
