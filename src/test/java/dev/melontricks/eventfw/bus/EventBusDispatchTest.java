package dev.melontricks.eventfw.bus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.melontricks.eventfw.dispatch.DispatchResult;
import dev.melontricks.eventfw.dispatch.EventPhase;
import dev.melontricks.eventfw.event.AbstractCancellableEvent;
import dev.melontricks.eventfw.event.Event;
import dev.melontricks.eventfw.listener.EventPriority;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class EventBusDispatchTest {
    @Test
    void ordersByPriorityThenSpecificityThenRegistration() {
        EventBus bus = EventBuses.create();
        List<String> calls = new ArrayList<>();
        bus.subscribe(Event.class, event -> calls.add("root"));
        bus.subscribe(ParentEvent.class, event -> calls.add("parent-first"));
        bus.subscribe(ParentEvent.class, event -> calls.add("parent-second"));
        bus.on(ChildEvent.class).priority(EventPriority.HIGH).subscribe(event -> calls.add("child-high"));
        bus.subscribe(ChildEvent.class, event -> calls.add("child-normal"));

        DispatchResult<ChildEvent> result = bus.publish(new ChildEvent("value"));

        assertEquals(List.of("child-high", "child-normal", "parent-first", "parent-second", "root"), calls);
        assertEquals(5, result.matchedListeners());
        assertEquals(5, result.invokedListeners());
        assertTrue(result.successful());
        assertTrue(result.delivered());
    }

    @Test
    void supportsExactSubscriptionsOnAPolymorphicBus() {
        EventBus bus = EventBuses.create();
        List<String> calls = new ArrayList<>();
        bus.on(ParentEvent.class).exactTypeOnly().subscribe(event -> calls.add("exact"));
        bus.subscribe(ParentEvent.class, event -> calls.add("polymorphic"));

        bus.publish(new ChildEvent("child"));
        bus.publish(new ParentEvent("parent"));

        assertEquals(List.of("polymorphic", "exact", "polymorphic"), calls);
    }

    @Test
    void supportsAnExactBusPolicy() {
        EventBus bus = EventBuses.builder().typeMatching(TypeMatching.EXACT).build();
        List<String> calls = new ArrayList<>();
        bus.subscribe(ParentEvent.class, event -> calls.add("parent"));
        bus.subscribe(ChildEvent.class, event -> calls.add("child"));

        bus.publish(new ChildEvent("value"));

        assertEquals(List.of("child"), calls);
    }

    @Test
    void cancellationSkipsNormalListenersButAllowsObservers() {
        EventBus bus = EventBuses.create();
        List<String> calls = new ArrayList<>();
        bus.on(CancelEvent.class).priority(EventPriority.HIGH).subscribe(event -> {
            calls.add("cancel");
            event.cancel("handled");
        });
        bus.subscribe(CancelEvent.class, event -> calls.add("normal"));
        bus.on(CancelEvent.class)
                .priority(EventPriority.MONITOR)
                .receiveCancelledEvents()
                .subscribe(event -> calls.add(event.cancellationReason().orElseThrow()));

        DispatchResult<CancelEvent> result = bus.publish(new CancelEvent());

        assertEquals(List.of("cancel", "handled"), calls);
        assertTrue(result.cancelled());
        assertEquals(2, result.invokedListeners());
        assertEquals(1, result.skippedListeners());
    }

    @Test
    void contextCanStopPropagation() {
        EventBus bus = EventBuses.create();
        List<Integer> depths = new ArrayList<>();
        bus.on(ChildEvent.class).priority(EventPriority.HIGH).subscribe((event, context) -> {
            depths.add(context.nestingDepth());
            context.stopPropagation();
        });
        bus.subscribe(ChildEvent.class, event -> depths.add(99));

        DispatchResult<ChildEvent> result = bus.publish(new ChildEvent("value"));

        assertEquals(List.of(0), depths);
        assertTrue(result.propagationStopped());
        assertEquals(1, result.skippedListeners());
    }

    @Test
    void filtersUseTheDispatchContext() {
        EventBus bus = EventBuses.create();
        List<String> calls = new ArrayList<>();
        bus.on(ChildEvent.class)
                .filter((event, context) -> event.value().startsWith("yes") && context.sequence() > 0)
                .subscribe(event -> calls.add(event.value()));

        DispatchResult<ChildEvent> rejected = bus.publish(new ChildEvent("no"));
        DispatchResult<ChildEvent> accepted = bus.publish(new ChildEvent("yes"));

        assertEquals(List.of("yes"), calls);
        assertEquals(1, rejected.skippedListeners());
        assertFalse(rejected.delivered());
        assertEquals(1, accepted.invokedListeners());
    }

    @Test
    void phasesRestrictDeliveryAndReachTheContext() {
        EventBus bus = EventBuses.create();
        List<String> calls = new ArrayList<>();
        bus.on(ChildEvent.class).phase(EventPhase.PRE).subscribe(event -> calls.add("pre"));
        bus.on(ChildEvent.class)
                .phase(EventPhase.POST)
                .subscribe((event, context) -> calls.add(context.phase().name()));
        bus.subscribe(ChildEvent.class, event -> calls.add("all"));

        DispatchResult<ChildEvent> pre = bus.publish(new ChildEvent("value"), EventPhase.PRE);
        DispatchResult<ChildEvent> post = bus.publish(new ChildEvent("value"), EventPhase.POST);

        assertEquals(List.of("pre", "all", "POST", "all"), calls);
        assertEquals(EventPhase.PRE, pre.phase());
        assertEquals(EventPhase.POST, post.phase());
        assertEquals(1, pre.skippedListeners());
        assertEquals(1, post.skippedListeners());
    }

    @Test
    void nestedPublicationsAreDepthFirstAndExposeDepth() {
        EventBus bus = EventBuses.create();
        List<String> calls = new ArrayList<>();
        bus.subscribe(ChildEvent.class, (event, context) -> {
            calls.add(event.value() + ':' + context.nestingDepth());
            if (event.value().equals("outer")) {
                context.publisher().publish(new ChildEvent("inner"));
            }
        });
        bus.on(ChildEvent.class).priority(EventPriority.LOW).subscribe(event -> calls.add("after-" + event.value()));

        bus.publish(new ChildEvent("outer"));

        assertEquals(List.of("outer:0", "inner:1", "after-inner", "after-outer"), calls);
    }

    @Test
    void topLevelDepthIsResetAfterNestedPublication() {
        EventBus bus = EventBuses.create();
        List<Integer> depths = new ArrayList<>();
        bus.subscribe(ChildEvent.class, (event, context) -> {
            depths.add(context.nestingDepth());
            if (event.value().equals("outer")) {
                context.publisher().publish(new ChildEvent("inner"));
            }
        });

        bus.publish(new ChildEvent("outer"));
        bus.publish(new ChildEvent("next"));

        assertEquals(List.of(0, 1, 0), depths);
    }

    private static class ParentEvent implements Event {
        private final String value;

        private ParentEvent(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }

    private static final class ChildEvent extends ParentEvent {
        private ChildEvent(String value) {
            super(value);
        }
    }

    private static final class CancelEvent extends AbstractCancellableEvent {}
}
