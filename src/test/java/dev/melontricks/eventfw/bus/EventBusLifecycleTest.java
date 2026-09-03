package dev.melontricks.eventfw.bus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.melontricks.eventfw.dispatch.EventPhase;
import dev.melontricks.eventfw.event.Event;
import dev.melontricks.eventfw.listener.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;
import org.junit.jupiter.api.Test;

final class EventBusLifecycleTest {
    @Test
    void closingOneSubscriptionKeepsItsPeersRegistered() {
        EventBus bus = EventBuses.create();
        List<String> calls = new ArrayList<>();
        Subscription first = bus.subscribe(TestEvent.class, event -> calls.add("first"));
        Subscription second = bus.subscribe(TestEvent.class, event -> calls.add("second"));

        first.close();
        bus.publish(new TestEvent(1));

        assertFalse(first.active());
        assertTrue(second.active());
        assertEquals(List.of("second"), calls);
        assertEquals(1, bus.subscriptionCount());
    }

    @Test
    void ownersUseIdentityInsteadOfEquality() {
        EventBus bus = EventBuses.create();
        String firstOwner = new String("owner");
        String equalOwner = new String("owner");
        bus.on(TestEvent.class).owner(firstOwner).subscribe(event -> {});

        assertEquals(0, bus.unregister(equalOwner));
        assertEquals(1, bus.unregister(firstOwner));
    }

    @Test
    void clearAndCloseAreIdempotent() {
        EventBus bus = EventBuses.create();
        bus.subscribe(TestEvent.class, event -> {});
        bus.subscribe(TestEvent.class, event -> {});

        assertEquals(2, bus.clear());
        assertEquals(0, bus.clear());
        bus.close();
        bus.close();

        assertTrue(bus.closed());
        TestEvent event = new TestEvent(1);
        assertThrows(IllegalStateException.class, () -> bus.publish(event));
        assertThrows(IllegalStateException.class, () -> bus.subscribe(TestEvent.class, ignoredEvent -> {}));
    }

    @Test
    void publishesAsynchronouslyOnTheConfiguredExecutor() {
        try (ExecutorService executor = Executors.newSingleThreadExecutor(
                runnable -> Thread.ofPlatform().name("event-test").unstarted(runnable))) {
            EventBus bus = EventBuses.builder().asyncExecutor(executor).build();
            List<String> threads = new ArrayList<>();
            bus.subscribe(
                    TestEvent.class, event -> threads.add(Thread.currentThread().getName()));

            bus.publishAsync(new TestEvent(1)).toCompletableFuture().join();

            assertEquals(List.of("event-test"), threads);
        }
    }

    @Test
    void metricsTrackDispatchActivity() {
        EventBus bus = EventBuses.create();
        bus.on(TestEvent.class).filter((event, context) -> false).subscribe(event -> {});
        bus.subscribe(TestEvent.class, event -> {});

        bus.publish(new TestEvent(1));
        EventBusMetrics metrics = bus.metrics();

        assertEquals(1, metrics.publishedEvents());
        assertEquals(1, metrics.listenerInvocations());
        assertEquals(1, metrics.skippedListeners());
        assertEquals(0, metrics.listenerFailures());
        assertEquals(2, metrics.activeSubscriptions());
    }

    @Test
    void subscriptionsCanBePausedResumedAndClosed() {
        EventBus bus = EventBuses.create();
        List<Integer> calls = new ArrayList<>();
        Subscription subscription = bus.subscribe(TestEvent.class, event -> calls.add(event.value()));

        subscription.pause();
        bus.publish(new TestEvent(1));
        subscription.resume();
        bus.publish(new TestEvent(2));
        subscription.close();
        subscription.resume();
        bus.publish(new TestEvent(3));

        assertEquals(List.of(2), calls);
        assertFalse(subscription.active());
        assertFalse(subscription.paused());
        assertFalse(subscription.receivesCancelledEvents());
        assertEquals(Set.of(EventPhase.values()), subscription.phases());
        assertTrue(subscription.owner().isEmpty());
    }

    @Test
    void singleUseSubscriptionsAreAtomicAcrossPublishers() throws Exception {
        EventBus bus = EventBuses.create();
        LongAdder calls = new LongAdder();
        Subscription subscription = bus.on(TestEvent.class).once().subscribe(event -> calls.increment());

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < 100; index++) {
                int value = index;
                futures.add(executor.submit(() -> bus.publish(new TestEvent(value))));
            }
            for (java.util.concurrent.Future<?> future : futures) {
                future.get();
            }
        }

        assertEquals(1, calls.sum());
        assertFalse(subscription.active());
        assertTrue(subscription.singleUse());
        assertEquals(0, bus.subscriptionCount());
    }

    @Test
    void supportsConcurrentPublication() throws Exception {
        EventBus bus = EventBuses.create();
        LongAdder calls = new LongAdder();
        bus.subscribe(TestEvent.class, event -> calls.increment());

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < 1_000; index++) {
                int value = index;
                futures.add(executor.submit(() -> bus.publish(new TestEvent(value))));
            }
            for (java.util.concurrent.Future<?> future : futures) {
                future.get();
            }
        }

        assertEquals(1_000, calls.sum());
        assertEquals(1_000, bus.metrics().publishedEvents());
    }

    private record TestEvent(int value) implements Event {}
}
