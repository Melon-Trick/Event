package dev.melontricks.eventfw.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.melontricks.eventfw.bus.EventBus;
import dev.melontricks.eventfw.bus.EventBuses;
import dev.melontricks.eventfw.dispatch.EventContext;
import dev.melontricks.eventfw.dispatch.EventPhase;
import dev.melontricks.eventfw.event.Event;
import dev.melontricks.eventfw.listener.Registration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AnnotatedListenerTest {
    @Test
    void registersPrivateAndInheritedHandlersWithContext() {
        EventBus bus = EventBuses.create();
        ChildListener listener = new ChildListener();

        Registration registration = bus.register(listener);
        bus.publish(new TestEvent("first"));
        registration.close();
        bus.publish(new TestEvent("second"));

        assertEquals(List.of("child:first:0", "base:first"), listener.calls);
        assertEquals(2, registration.subscriptions().size());
        assertFalse(registration.active());
    }

    @Test
    void annotationSupportsExactTypeDelivery() {
        EventBus bus = EventBuses.create();
        ExactListener listener = new ExactListener();
        bus.register(listener);

        bus.publish(new ChildEvent());
        bus.publish(new ParentEvent());

        assertEquals(1, listener.calls);
    }

    @Test
    void unannotatedOverrideSuppressesAnAnnotatedParentMethod() {
        EventBus bus = EventBuses.create();
        OverridingListener listener = new OverridingListener();

        Registration registration = bus.register(listener);
        bus.publish(new TestEvent("value"));

        assertEquals(0, registration.subscriptions().size());
        assertEquals(List.of(), listener.calls);
    }

    @Test
    void annotationCanRestrictDeliveryPhases() {
        EventBus bus = EventBuses.create();
        PhasedListener listener = new PhasedListener();
        bus.register(listener);

        bus.publish(new TestEvent("pre"), EventPhase.PRE);
        bus.publish(new TestEvent("post"), EventPhase.POST);

        assertEquals(List.of("post"), listener.calls);
    }

    @Test
    void annotationSupportsSingleUseHandlers() {
        EventBus bus = EventBuses.create();
        SingleUseListener listener = new SingleUseListener();
        bus.register(listener);

        bus.publish(new TestEvent("first"));
        bus.publish(new TestEvent("second"));

        assertEquals(List.of("first"), listener.calls);
    }

    @Test
    void rejectsStaticHandlers() {
        EventBus bus = EventBuses.create();
        StaticListener listener = new StaticListener();
        assertThrows(InvalidListenerException.class, () -> bus.register(listener));
    }

    @Test
    void rejectsHandlersWithInvalidParameters() {
        EventBus bus = EventBuses.create();
        InvalidParameterListener listener = new InvalidParameterListener();
        assertThrows(InvalidListenerException.class, () -> bus.register(listener));
    }

    @Test
    void rejectsHandlersWithReturnValues() {
        EventBus bus = EventBuses.create();
        ReturningListener listener = new ReturningListener();
        assertThrows(InvalidListenerException.class, () -> bus.register(listener));
    }

    private static class BaseListener {
        protected final List<String> calls = new ArrayList<>();

        @Subscribe(priority = -10)
        private void base(TestEvent event) {
            calls.add("base:" + event.value());
        }
    }

    private static final class ChildListener extends BaseListener {
        @Subscribe(priority = 10)
        private void child(TestEvent event, EventContext<TestEvent> context) {
            calls.add("child:" + event.value() + ':' + context.nestingDepth());
        }
    }

    private static class OverriddenBase {
        protected final List<String> calls = new ArrayList<>();

        @Subscribe
        protected void handle(TestEvent event) {
            calls.add(event.value());
        }
    }

    private static final class OverridingListener extends OverriddenBase {
        @Override
        protected void handle(TestEvent event) {
            calls.add("override:" + event.value());
        }
    }

    private static final class ExactListener {
        private int calls;

        @Subscribe(exactTypeOnly = true)
        private void handle(ParentEvent event) {
            calls++;
        }
    }

    private static final class PhasedListener {
        private final List<String> calls = new ArrayList<>();

        @Subscribe(phases = EventPhase.POST)
        private void handle(TestEvent event) {
            calls.add(event.value());
        }
    }

    private static final class SingleUseListener {
        private final List<String> calls = new ArrayList<>();

        @Subscribe(once = true)
        private void handle(TestEvent event) {
            calls.add(event.value());
        }
    }

    private static final class StaticListener {
        @Subscribe
        private static void handle(TestEvent event) {
            throw new AssertionError(event);
        }
    }

    private static final class InvalidParameterListener {
        @Subscribe
        private void handle(String value) {
            throw new AssertionError(value);
        }
    }

    private static final class ReturningListener {
        @Subscribe
        private int handle(TestEvent event) {
            return 1;
        }
    }

    private record TestEvent(String value) implements Event {}

    private static class ParentEvent implements Event {}

    private static final class ChildEvent extends ParentEvent {}
}
