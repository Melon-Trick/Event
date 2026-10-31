package dev.vriege.eventfw.annotation;

import static org.junit.jupiter.api.Assertions.*;

import dev.vriege.eventfw.bus.EventBus;
import dev.vriege.eventfw.bus.EventBuses;
import dev.vriege.eventfw.dispatch.EventContext;
import dev.vriege.eventfw.event.Event;
import dev.vriege.eventfw.listener.EventFilter;
import dev.vriege.eventfw.scope.SubscriptionScope;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
final class DeclarativeDslTest {
    @Test
    void annotatedPoliciesUseTheSameLimitFilterAndChannelRulesAsTheDsl() {
        try (EventBus bus = EventBuses.create();
                SubscriptionScope scope = new SubscriptionScope()) {
            ConfiguredListener listener = new ConfiguredListener();
            var registration = bus.register(listener, scope);
            assertEquals(1, registration.subscriptions().size());
            var subscription = registration.subscriptions().getFirst();
            assertEquals(2, subscription.invocationLimit());
            assertEquals("audit", subscription.channel());
            bus.publish(new NumberEvent(2));
            bus.dispatch(new NumberEvent(1)).channel("audit").publish();
            bus.dispatch(new NumberEvent(2)).channel("audit").publish();
            bus.dispatch(new NumberEvent(4)).channel("audit").publish();
            bus.dispatch(new NumberEvent(6)).channel("audit").publish();
            assertEquals(List.of(2, 4), listener.calls);
            assertFalse(scope.active());
        }
    }

    @Test
    void annotationStagesAreAwaitedAndMethodPoliciesOverrideClassDefaults() {
        try (EventBus bus = EventBuses.builder()
                .asyncExecutor(Runnable::run)
                .executor("named", Runnable::run)
                .build()) {
            AsyncListener listener = new AsyncListener();
            bus.register(listener);
            var result = bus.dispatch(new NumberEvent(1))
                    .channel("method")
                    .publishAsync()
                    .toCompletableFuture();
            assertFalse(result.isDone());
            assertEquals(List.of("first"), listener.calls);
            listener.gate.complete(null);
            assertTrue(result.join().successful());
            assertEquals(List.of("first", "second"), listener.calls);
            assertEquals(0, bus.publish(new NumberEvent(2)).invokedListeners());
        }
    }

    @Test
    void annotatedExpirationClosesItsRegistration() throws Exception {
        try (EventBus bus = EventBuses.create()) {
            var registration = bus.register(new ExpiringListener());
            registration
                    .subscriptions()
                    .getFirst()
                    .whenClosed()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertFalse(registration.active());
        }
    }

    @Test
    void registrationFailuresRollBackEarlierMethods() {
        try (EventBus bus = EventBuses.create()) {
            UnknownExecutorListener unknownExecutor = new UnknownExecutorListener();
            BrokenFilterListener brokenFilter = new BrokenFilterListener();
            MissingFilterConstructorListener missingConstructor = new MissingFilterConstructorListener();
            assertThrows(InvalidListenerException.class, () -> bus.register(unknownExecutor));
            assertEquals(0, bus.subscriptionCount());
            assertThrows(InvalidListenerException.class, () -> bus.register(brokenFilter));
            assertEquals(0, bus.subscriptionCount());
            assertThrows(InvalidListenerException.class, () -> bus.register(missingConstructor));
            assertEquals(0, bus.subscriptionCount());
        }
    }

    @Test
    void invalidDeclarativePoliciesFailBeforeInstallation() {
        try (EventBus bus = EventBuses.create()) {
            List<Object> listeners = List.of(
                    new ZeroLimitListener(),
                    new ConflictingLimitListener(),
                    new ZeroExpirationListener(),
                    new OverflowExpirationListener(),
                    new ZeroCapacityListener(),
                    new NegativeTimeoutListener(),
                    new InvalidClassPolicyListener());
            for (Object listener : listeners) {
                assertThrows(InvalidListenerException.class, () -> bus.register(listener));
                assertEquals(0, bus.subscriptionCount());
            }
        }
    }

    @Test
    void classDefaultsSupportSynchronousMethodsOnAsyncExecutors() {
        try (EventBus bus = EventBuses.builder().asyncExecutor(Runnable::run).build()) {
            ClassPolicyListener listener = new ClassPolicyListener();
            bus.register(listener);
            assertTrue(bus.dispatch(new NumberEvent(1))
                    .channel("class")
                    .publishAsync()
                    .toCompletableFuture()
                    .join()
                    .successful());
            assertEquals(1, listener.calls);
            assertThrows(NullPointerException.class, () -> bus.register(listener, null));
        }
    }

    @Channel("audit")
    private static final class ConfiguredListener {
        private final List<Integer> calls = new ArrayList<>();

        @Subscribe
        @InvocationLimit(2)
        @FilterWith(EvenFilter.class)
        private void handle(NumberEvent event) {
            calls.add(event.value());
        }
    }

    public static final class EvenFilter implements EventFilter<NumberEvent> {
        @Override
        public boolean test(NumberEvent event, EventContext<NumberEvent> context) {
            return event.value() % 2 == 0;
        }
    }

    @Channel("class")
    @Execution(executor = "named", sequential = true, timeout = 1, timeoutUnit = TimeUnit.DAYS)
    private static final class AsyncListener {
        private final List<String> calls = new ArrayList<>();
        private final CompletableFuture<Void> gate = new CompletableFuture<>();

        @Subscribe(priority = 10)
        @Channel("method")
        private CompletionStage<Void> first(NumberEvent event, EventContext<NumberEvent> context) {
            calls.add("first");
            assertEquals("method", context.channel());
            return gate;
        }

        @Subscribe
        @Channel("method")
        @Execution
        private CompletableFuture<String> second(NumberEvent event) {
            calls.add("second");
            return CompletableFuture.completedFuture("result is intentionally ignored");
        }
    }

    private static final class ExpiringListener {
        @Subscribe
        @ExpireAfter(1)
        private void handle(NumberEvent event) {
            fail("not published");
        }
    }

    private static final class UnknownExecutorListener {
        @Subscribe
        private void a(NumberEvent event) {
            fail("not published");
        }

        @Subscribe
        @Execution(executor = "missing")
        private void b(NumberEvent event) {
            fail("not published");
        }
    }

    public static final class BrokenFilter implements EventFilter<NumberEvent> {
        public BrokenFilter() {
            throw new IllegalStateException("cannot construct filter");
        }

        @Override
        public boolean test(NumberEvent event, EventContext<NumberEvent> context) {
            return true;
        }
    }

    public static final class MissingConstructorFilter implements EventFilter<NumberEvent> {
        private final boolean accepted;

        public MissingConstructorFilter(boolean accepted) {
            this.accepted = accepted;
        }

        @Override
        public boolean test(NumberEvent event, EventContext<NumberEvent> context) {
            return accepted;
        }
    }

    private static final class BrokenFilterListener {
        @Subscribe
        @FilterWith(BrokenFilter.class)
        private void handle(NumberEvent event) {
            fail("not published");
        }
    }

    private static final class MissingFilterConstructorListener {
        @Subscribe
        @FilterWith(MissingConstructorFilter.class)
        private void handle(NumberEvent event) {
            fail("not published");
        }
    }

    private static final class ZeroLimitListener {
        @Subscribe
        @InvocationLimit(0)
        private void handle(NumberEvent event) {
            fail("not published");
        }
    }

    private static final class ConflictingLimitListener {
        @Subscribe(once = true)
        @InvocationLimit(2)
        private void handle(NumberEvent event) {
            fail("not published");
        }
    }

    private static final class ZeroExpirationListener {
        @Subscribe
        @ExpireAfter(0)
        private void handle(NumberEvent event) {
            fail("not published");
        }
    }

    private static final class OverflowExpirationListener {
        @Subscribe
        @ExpireAfter(value = Long.MAX_VALUE, unit = TimeUnit.DAYS)
        private void handle(NumberEvent event) {
            fail("not published");
        }
    }

    private static final class ZeroCapacityListener {
        @Subscribe
        @Execution(capacity = 0)
        private void handle(NumberEvent event) {
            fail("not published");
        }
    }

    private static final class NegativeTimeoutListener {
        @Subscribe
        @Execution(timeout = -1)
        private void handle(NumberEvent event) {
            fail("not published");
        }
    }

    @Execution(capacity = -1)
    private static final class InvalidClassPolicyListener {
        @Subscribe
        private void handle(NumberEvent event) {
            fail("not published");
        }
    }

    @Channel("class")
    @Execution
    private static final class ClassPolicyListener {
        private int calls;

        @Subscribe
        private void handle(NumberEvent event) {
            calls++;
        }
    }

    public record NumberEvent(int value) implements Event {}
}
