package dev.vriege.eventfw.bus;

import static org.junit.jupiter.api.Assertions.*;

import dev.vriege.eventfw.dispatch.EventPhase;
import dev.vriege.eventfw.event.AbstractCancellableEvent;
import dev.vriege.eventfw.event.Event;
import dev.vriege.eventfw.event.EventKey;
import dev.vriege.eventfw.scope.SubscriptionScope;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
final class EventAwaitTest {
    @Test
    void startsLazilyAndEachStartCapturesIndependentFilters() {
        try (EventBus bus = EventBuses.create()) {
            var builder = bus.await(NumberEvent.class).filter(event -> event.value() > 0);
            assertEquals(0, bus.subscriptionCount());
            var first = builder.start();
            var second = builder.filter((event, context) -> context.phase() == EventPhase.POST)
                    .start();
            bus.publish(new NumberEvent(-1));
            assertFalse(first.isDone());
            NumberEvent event = new NumberEvent(1);
            bus.publish(event);
            assertSame(event, first.join());
            assertFalse(second.isDone());
            bus.publish(new NumberEvent(2), EventPhase.POST);
            assertEquals(2, second.join().value());
            assertEquals(0, bus.subscriptionCount());
        }
    }

    @Test
    @SuppressWarnings("try")
    void cancellationExternalCompletionAndBusRemovalCleanUp() {
        try (EventBus bus = EventBuses.create()) {
            var cancelled = bus.await(NumberEvent.class).start();
            cancelled.cancel(false);
            assertEquals(0, bus.subscriptionCount());
            var completed = bus.await(NumberEvent.class).start();
            completed.complete(new NumberEvent(2));
            assertEquals(0, bus.subscriptionCount());
            var cleared = bus.await(NumberEvent.class).start();
            bus.clear();
            assertTrue(cleared.isCancelled());
            var closed = bus.await(NumberEvent.class).start();
            bus.close();
            assertTrue(closed.isCancelled());
            assertThrows(IllegalStateException.class, () -> bus.await(NumberEvent.class));
        }
    }

    @Test
    @SuppressWarnings("try")
    void scopeClosureCancelsWaitAndDetachesIt() {
        try (EventBus bus = EventBuses.create();
                SubscriptionScope scope = new SubscriptionScope()) {
            var future = bus.await(NumberEvent.class).scope(scope).start();
            assertTrue(scope.active());
            scope.close();
            assertTrue(future.isCancelled());
            assertEquals(0, bus.subscriptionCount());
            var rejected = bus.await(NumberEvent.class).scope(scope).start();
            assertTrue(rejected.isDone());
            assertEquals(0, bus.subscriptionCount());
        }
    }

    @Test
    void timeoutCompletesExceptionallyAndRemovesItsListener() throws Exception {
        try (EventBus bus = EventBuses.create()) {
            var future =
                    bus.await(NumberEvent.class).timeout(Duration.ofMillis(1)).start();
            var failure =
                    assertThrows(java.util.concurrent.ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
            assertInstanceOf(TimeoutException.class, failure.getCause());
            for (var subscription : bus.subscriptions()) {
                subscription.whenClosed().toCompletableFuture().get(5, TimeUnit.SECONDS);
            }
            assertEquals(0, bus.subscriptionCount());
        }
    }

    @Test
    void successCancelsLongTimeoutTask() {
        try (var scheduler = new java.util.concurrent.ScheduledThreadPoolExecutor(1);
                EventBus bus = EventBuses.builder().scheduler(scheduler).build()) {
            scheduler.setRemoveOnCancelPolicy(true);
            var future =
                    bus.await(NumberEvent.class).timeout(Duration.ofDays(1)).start();
            assertEquals(1, scheduler.getQueue().size());
            bus.publish(new NumberEvent(1));
            assertEquals(1, future.join().value());
            assertTrue(scheduler.getQueue().isEmpty());
        }
    }

    @Test
    void schedulerRejectionAndFilterFailuresCompleteWaits() {
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.shutdown();
        try (EventBus bus = EventBuses.builder()
                .scheduler(scheduler)
                .exceptionHandler(_ -> {})
                .build()) {
            var rejected =
                    bus.await(NumberEvent.class).timeout(Duration.ofSeconds(1)).start();
            assertTrue(rejected.isCompletedExceptionally());
            assertEquals(0, bus.subscriptionCount());
            var failed = bus.await(NumberEvent.class)
                    .filter(_ -> {
                        throw new IllegalArgumentException("filter failure");
                    })
                    .start();
            bus.publish(new NumberEvent(1));
            assertTrue(failed.isCompletedExceptionally());
            assertEquals(0, bus.subscriptionCount());
        }
    }

    @Test
    void waitsRespectTypedChannelsPhasesAndCancellation() {
        try (EventBus bus = EventBuses.create()) {
            var key = new EventKey<>(CancelEvent.class, "audit");
            var future = bus.await(key)
                    .phase(EventPhase.POST)
                    .receiveCancelledEvents()
                    .start();
            CancelEvent event = new CancelEvent();
            event.cancel();
            bus.publish(event);
            bus.dispatch(key, event).publish();
            assertFalse(future.isDone());
            bus.dispatch(key, event).phase(EventPhase.POST).publish();
            assertSame(event, future.join());
        }
    }

    @Test
    void validatesTimeoutsBeforeInstallingAnything() {
        try (EventBus bus = EventBuses.create()) {
            var builder = bus.await(NumberEvent.class);
            assertThrows(IllegalArgumentException.class, () -> builder.timeout(Duration.ZERO));
            Duration negative = Duration.ofSeconds(-1);
            Duration excessive = Duration.ofSeconds(Long.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> builder.timeout(negative));
            assertThrows(ArithmeticException.class, () -> builder.timeout(excessive));
            assertThrows(
                    NullPointerException.class, () -> builder.filter((java.util.function.Predicate<NumberEvent>) null));
            assertEquals(0, bus.subscriptionCount());
        }
    }

    private record NumberEvent(int value) implements Event {}

    private static final class CancelEvent extends AbstractCancellableEvent {}
}
