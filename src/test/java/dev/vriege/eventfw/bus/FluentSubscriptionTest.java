package dev.vriege.eventfw.bus;

import static org.junit.jupiter.api.Assertions.*;

import dev.vriege.eventfw.diagnostic.Eligibility;
import dev.vriege.eventfw.dispatch.AttributeKey;
import dev.vriege.eventfw.dispatch.DispatchOptions;
import dev.vriege.eventfw.dispatch.EventPhase;
import dev.vriege.eventfw.event.AbstractCancellableEvent;
import dev.vriege.eventfw.event.Event;
import dev.vriege.eventfw.event.EventKey;
import dev.vriege.eventfw.listener.EventFilter;
import dev.vriege.eventfw.scope.SubscriptionScope;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
final class FluentSubscriptionTest {
    @Test
    void filtersAccumulateAndShortCircuitWithoutConsumingLimits() {
        try (EventBus bus = EventBuses.create()) {
            List<Integer> calls = new ArrayList<>();
            AtomicInteger checks = new AtomicInteger();
            var subscription = bus.on(NumberEvent.class)
                    .filter(event -> event.value() > 0)
                    .filter((event, _) -> {
                        checks.incrementAndGet();
                        return event.value() % 2 == 0;
                    })
                    .limit(2)
                    .subscribe(event -> calls.add(event.value()));
            for (int value : new int[] {-2, 1, 2, 4, 6}) {
                bus.publish(new NumberEvent(value));
            }
            assertEquals(List.of(2, 4), calls);
            assertEquals(3, checks.get());
            assertEquals(2, subscription.invocationLimit());
            assertFalse(subscription.singleUse());
            assertFalse(subscription.active());
        }
    }

    @Test
    void filterCompositionSupportsShortCircuitDisjunctionAndNegation() {
        try (EventBus bus = EventBuses.create()) {
            EventFilter<NumberEvent> positive = (event, _) -> event.value() > 0;
            EventFilter<NumberEvent> even = (event, _) -> event.value() % 2 == 0;
            List<Integer> calls = new ArrayList<>();
            bus.on(NumberEvent.class).filter(positive.negate().or(even)).subscribe(event -> calls.add(event.value()));
            bus.publish(new NumberEvent(-1));
            bus.publish(new NumberEvent(2));
            bus.publish(new NumberEvent(3));
            assertEquals(List.of(-1, 2), calls);
            assertThrows(NullPointerException.class, () -> positive.and(null));
            assertThrows(NullPointerException.class, () -> positive.or(null));
        }
    }

    @Test
    @SuppressWarnings("try")
    void scopesCloseDetachAndRejectNewRegistrations() {
        try (EventBus bus = EventBuses.create();
                SubscriptionScope scope = new SubscriptionScope()) {
            AtomicInteger calls = new AtomicInteger();
            var first = bus.on(NumberEvent.class).scope(scope).subscribe(_ -> calls.incrementAndGet());
            var second = bus.on(NumberEvent.class).scope(scope).once().subscribe(_ -> calls.incrementAndGet());
            assertTrue(scope.active());
            assertEquals(2, scope.subscriptions().size());
            scope.pause();
            bus.publish(new NumberEvent(1));
            scope.resume();
            bus.publish(new NumberEvent(2));
            assertEquals(2, calls.get());
            assertEquals(List.of(first), scope.subscriptions());
            assertTrue(second.whenClosed().toCompletableFuture().isDone());
            scope.close();
            scope.close();
            assertTrue(scope.closed());
            assertFalse(scope.active());
            var rejectedBuilder = bus.on(NumberEvent.class).scope(scope);
            assertThrows(IllegalStateException.class, () -> rejectedBuilder.subscribe(_ -> calls.incrementAndGet()));
            assertEquals(0, bus.subscriptionCount());
        }
    }

    @Test
    void limitsAreAtomicAcrossConcurrentPublishers() throws Exception {
        try (EventBus bus = EventBuses.create();
                var executor = Executors.newFixedThreadPool(8)) {
            AtomicInteger calls = new AtomicInteger();
            bus.on(NumberEvent.class).limit(17).subscribe(_ -> calls.incrementAndGet());
            List<java.util.concurrent.Future<?>> work = new ArrayList<>();
            for (int index = 0; index < 200; index++) {
                work.add(executor.submit(() -> bus.publish(new NumberEvent(1))));
            }
            for (var task : work) {
                task.get();
            }
            assertEquals(17, calls.get());
            assertEquals(0, bus.subscriptionCount());
        }
    }

    @Test
    void registrationAndSingleUseConsumptionMayRaceWithoutLosingSubscriptions() throws Exception {
        try (EventBus bus = EventBuses.create();
                var executor = Executors.newFixedThreadPool(2)) {
            Object owner = new Object();
            AtomicInteger calls = new AtomicInteger();
            var installed = new java.util.concurrent.atomic.AtomicBoolean();
            var registration = executor.submit(() -> {
                for (int index = 0; index < 1000; index++) {
                    bus.on(NumberEvent.class).owner(owner).once().subscribe(_ -> calls.incrementAndGet());
                }
                installed.set(true);
            });
            var publication = executor.submit(() -> {
                do {
                    bus.publish(new NumberEvent(1));
                    assertTrue(bus.subscriptionCount() >= 0);
                } while (!installed.get());
            });
            registration.get(5, TimeUnit.SECONDS);
            publication.get(5, TimeUnit.SECONDS);
            bus.publish(new NumberEvent(1));
            assertEquals(1000, calls.get());
            assertEquals(0, bus.subscriptionCount());
            assertTrue(bus.subscriptions().isEmpty());
            assertEquals(0, bus.unregister(owner));
        }
    }

    @Test
    void expirationRemovesSubscriptionsAndCancelsTimersOnEarlyClose() throws Exception {
        try (var scheduler = new java.util.concurrent.ScheduledThreadPoolExecutor(1);
                EventBus bus = EventBuses.builder().scheduler(scheduler).build()) {
            scheduler.setRemoveOnCancelPolicy(true);
            var expired =
                    bus.on(NumberEvent.class).expireAfter(Duration.ofMillis(1)).subscribe(_ -> {});
            expired.whenClosed().toCompletableFuture().get(5, TimeUnit.SECONDS);
            var early =
                    bus.on(NumberEvent.class).expireAfter(Duration.ofDays(1)).subscribe(_ -> {});
            assertEquals(1, scheduler.getQueue().size());
            early.close();
            assertTrue(scheduler.getQueue().isEmpty());
            assertEquals(0, bus.subscriptionCount());
        }
    }

    @Test
    void schedulerRejectionRollsBackSubscriptionInstallation() {
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.shutdown();
        try (EventBus bus = EventBuses.builder().scheduler(scheduler).build()) {
            var builder = bus.on(NumberEvent.class).expireAfter(Duration.ofSeconds(1));
            assertThrows(java.util.concurrent.RejectedExecutionException.class, () -> builder.subscribe(_ -> {}));
            assertEquals(0, bus.subscriptionCount());
        }
    }

    @Test
    void typedChannelsAndPublicationSnapshotsCarryCheckedAttributes() {
        try (EventBus bus = EventBuses.builder().asyncExecutor(Runnable::run).build()) {
            EventKey<NumberEvent> key = new EventKey<>(NumberEvent.class, "orders");
            AttributeKey<String> trace = new AttributeKey<>("trace", String.class);
            List<String> observed = new ArrayList<>();
            bus.subscribe(NumberEvent.class, _ -> fail("named publication reached default channel"));
            var subscription = bus.on(key).subscribe((_, context) -> {
                observed.add(context.attribute(trace).orElse("missing"));
                assertEquals("orders", context.channel());
                var attributes = context.attributes();
                assertThrows(UnsupportedOperationException.class, attributes::clear);
            });
            var dispatch =
                    bus.dispatch(key, new NumberEvent(1)).phase(EventPhase.POST).attribute(trace, "first");
            assertEquals(1, dispatch.publish().invokedListeners());
            dispatch.attribute(trace, "second")
                    .publishAsync()
                    .toCompletableFuture()
                    .join();
            assertEquals(List.of("first", "second"), observed);
            assertEquals("orders", subscription.channel());
            assertEquals(List.of(subscription), bus.subscriptions().subList(1, 2));
        }
    }

    @Test
    void diagnosticsExplainSelectionWithoutCallingFilters() {
        try (EventBus bus = EventBuses.create()) {
            var eligible = bus.on(CancelEvent.class)
                    .receiveCancelledEvents()
                    .filter(_ -> {
                        fail("inspection executed a filter");
                        return false;
                    })
                    .subscribe(_ -> {});
            var paused = bus.subscribe(CancelEvent.class, _ -> {});
            paused.pause();
            bus.on(CancelEvent.class).channel("other").subscribe(_ -> {});
            bus.on(CancelEvent.class).phase(EventPhase.POST).subscribe(_ -> {});
            bus.subscribe(CancelEvent.class, _ -> {});
            CancelEvent event = new CancelEvent();
            event.cancel();
            var diagnostics = bus.inspect(event, DispatchOptions.DEFAULT);
            assertEquals(
                    List.of(
                            Eligibility.ELIGIBLE,
                            Eligibility.PAUSED,
                            Eligibility.CHANNEL_MISMATCH,
                            Eligibility.PHASE_MISMATCH,
                            Eligibility.CANCELLED),
                    diagnostics.stream().map(item -> item.eligibility()).toList());
            assertEquals(eligible, diagnostics.getFirst().subscription());
            assertEquals(0, bus.metrics().publishedEvents());
            assertThrows(UnsupportedOperationException.class, diagnostics::clear);
        }
    }

    @Test
    void buildersRejectInvalidConfigurationBeforeInstallation() {
        try (EventBus bus = EventBuses.create()) {
            var builder = bus.on(NumberEvent.class);
            assertThrows(IllegalArgumentException.class, () -> builder.limit(0));
            assertThrows(IllegalArgumentException.class, () -> builder.expireAfter(Duration.ZERO));
            Duration negative = Duration.ofSeconds(-1);
            Duration excessive = Duration.ofSeconds(Long.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> builder.expireAfter(negative));
            assertThrows(ArithmeticException.class, () -> builder.expireAfter(excessive));
            assertThrows(NullPointerException.class, () -> builder.channel(null));
            assertThrows(NullPointerException.class, () -> builder.scope(null));
            assertThrows(
                    NullPointerException.class, () -> builder.filter((java.util.function.Predicate<NumberEvent>) null));
            assertThrows(IllegalArgumentException.class, () -> new AttributeKey<>(" ", String.class));
            assertThrows(IllegalArgumentException.class, () -> new AttributeKey<>("id", int.class));
            Map<AttributeKey<?>, Object> invalidAttributes = Map.of(new AttributeKey<>("id", String.class), 1);
            assertThrows(
                    ClassCastException.class, () -> new DispatchOptions(EventPhase.DEFAULT, "", invalidAttributes));
            assertEquals(0, bus.subscriptionCount());
        }
    }

    private record NumberEvent(int value) implements Event {}

    private static final class CancelEvent extends AbstractCancellableEvent {}
}
