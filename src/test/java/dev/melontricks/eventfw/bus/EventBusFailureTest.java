package dev.melontricks.eventfw.bus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.melontricks.eventfw.dispatch.DispatchResult;
import dev.melontricks.eventfw.dispatch.EventDispatchException;
import dev.melontricks.eventfw.dispatch.FailurePolicy;
import dev.melontricks.eventfw.dispatch.FailureStage;
import dev.melontricks.eventfw.dispatch.ListenerFailure;
import dev.melontricks.eventfw.event.Event;
import dev.melontricks.eventfw.listener.EventPriority;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class EventBusFailureTest {
    @Test
    void continuePolicyContainsFailuresAndRunsLaterListeners() {
        List<ListenerFailure> reported = new ArrayList<>();
        EventBus bus = EventBuses.builder().exceptionHandler(reported::add).build();
        IllegalArgumentException cause = new IllegalArgumentException("failure");
        List<String> calls = new ArrayList<>();
        bus.on(TestEvent.class).priority(EventPriority.HIGH).subscribe(event -> {
            throw cause;
        });
        bus.subscribe(TestEvent.class, event -> calls.add("continued"));

        DispatchResult<TestEvent> result = bus.publish(new TestEvent());

        assertEquals(List.of("continued"), calls);
        assertEquals(1, result.failures().size());
        assertSame(cause, result.failures().getFirst().cause());
        assertEquals(result.failures(), reported);
        assertEquals(FailureStage.HANDLER, reported.getFirst().stage());
    }

    @Test
    void failFastPolicyThrowsAndStopsDispatch() {
        EventBus bus = EventBuses.builder()
                .failurePolicy(FailurePolicy.FAIL_FAST)
                .exceptionHandler(failure -> {})
                .build();
        List<String> calls = new ArrayList<>();
        bus.on(TestEvent.class).priority(EventPriority.HIGH).subscribe(event -> {
            throw new IllegalStateException("stop");
        });
        bus.subscribe(TestEvent.class, event -> calls.add("unexpected"));

        EventDispatchException exception =
                assertThrows(EventDispatchException.class, () -> bus.publish(new TestEvent()));

        assertEquals(FailureStage.HANDLER, exception.failure().stage());
        assertEquals(List.of(), calls);
    }

    @Test
    void reportsFilterFailuresSeparately() {
        List<ListenerFailure> reported = new ArrayList<>();
        EventBus bus = EventBuses.builder().exceptionHandler(reported::add).build();
        bus.on(TestEvent.class)
                .filter((event, context) -> {
                    throw new IllegalArgumentException("filter");
                })
                .subscribe(event -> {});

        DispatchResult<TestEvent> result = bus.publish(new TestEvent());

        assertEquals(1, result.skippedListeners());
        assertEquals(1, result.failures().size());
        assertEquals(FailureStage.FILTER, reported.getFirst().stage());
    }

    private record TestEvent() implements Event {}
}
