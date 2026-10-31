package dev.vriege.eventfw.bus;

import static org.junit.jupiter.api.Assertions.*;

import dev.vriege.eventfw.dispatch.FailurePolicy;
import dev.vriege.eventfw.dispatch.FailureStage;
import dev.vriege.eventfw.event.Event;
import dev.vriege.eventfw.execution.ExecutionBuilder;
import dev.vriege.eventfw.execution.ExecutionOptions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
final class AsyncDispatchTest {
    private static EventBus bus() {
        return EventBuses.builder()
                .asyncExecutor(Runnable::run)
                .exceptionHandler(_ -> {})
                .build();
    }

    @Test
    void waitsForStagesInPriorityOrderWithoutBlockingPublisher() {
        try (EventBus bus = bus()) {
            CompletableFuture<Void> gate = new CompletableFuture<>();
            List<String> calls = new ArrayList<>();
            bus.on(NumberEvent.class).priority(10).subscribeAsync(event -> {
                calls.add("async");
                return gate;
            });
            bus.subscribe(NumberEvent.class, _ -> calls.add("sync"));
            var result = bus.publishAsync(new NumberEvent(1)).toCompletableFuture();
            assertFalse(result.isDone());
            assertEquals(List.of("async"), calls);
            gate.complete(null);
            assertEquals(2, result.join().invokedListeners());
            assertEquals(List.of("async", "sync"), calls);
            assertEquals(2, bus.metrics().listenerInvocations());
        }
    }

    @Test
    void propagationChangesBeforeStageCompletionAffectLaterListeners() {
        try (EventBus bus = bus()) {
            CompletableFuture<Void> gate = new CompletableFuture<>();
            bus.on(NumberEvent.class).subscribeAsync((_, context) -> gate.thenRun(context::stopPropagation));
            bus.subscribe(NumberEvent.class, _ -> fail("propagation was stopped"));
            var result = bus.publishAsync(new NumberEvent(1)).toCompletableFuture();
            gate.complete(null);
            assertTrue(result.join().propagationStopped());
            assertEquals(1, result.join().skippedListeners());
        }
    }

    @Test
    void synchronousPublicationRejectsAsyncHandlersWithoutConsumingOnce() {
        try (EventBus bus = bus()) {
            AtomicInteger calls = new AtomicInteger();
            var subscription = bus.on(NumberEvent.class).once().subscribeAsync(_ -> {
                calls.incrementAndGet();
                return CompletableFuture.completedFuture("ignored result");
            });
            var sync = bus.publish(new NumberEvent(1));
            assertEquals(FailureStage.EXECUTION, sync.failures().getFirst().stage());
            assertEquals(0, calls.get());
            assertTrue(subscription.active());
            assertTrue(subscription.asynchronous());
            assertTrue(bus.publishAsync(new NumberEvent(1))
                    .toCompletableFuture()
                    .join()
                    .successful());
            assertEquals(1, calls.get());
            assertFalse(subscription.active());
        }
    }

    @Test
    void asynchronousFiltersAndStateChangesShareSynchronousRules() {
        try (EventBus bus = bus()) {
            bus.on(NumberEvent.class).filter(_ -> false).subscribe(_ -> fail("rejected filter"));
            bus.on(NumberEvent.class)
                    .filter(_ -> {
                        throw new IllegalArgumentException("filter");
                    })
                    .subscribe(_ -> fail("failed filter"));
            var paused = bus.subscribe(NumberEvent.class, _ -> fail("paused"));
            paused.pause();
            bus.on(NumberEvent.class).channel("other").subscribe(_ -> fail("other channel"));
            var result =
                    bus.publishAsync(new NumberEvent(1)).toCompletableFuture().join();
            assertEquals(4, result.skippedListeners());
            assertEquals(FailureStage.FILTER, result.failures().getFirst().stage());
        }
    }

    @Test
    void asynchronousFailuresAreUnwrappedAndContinuePolicyRunsPeers() {
        try (EventBus bus = bus()) {
            IllegalStateException cause = new IllegalStateException("failed work");
            bus.on(NumberEvent.class)
                    .subscribeAsync(_ -> CompletableFuture.failedFuture(new CompletionException(cause)));
            bus.on(NumberEvent.class).subscribeAsync(_ -> {
                throw new IllegalArgumentException("failed start");
            });
            bus.on(NumberEvent.class).subscribeAsync(_ -> null);
            AtomicInteger calls = new AtomicInteger();
            bus.subscribe(NumberEvent.class, _ -> calls.incrementAndGet());
            var result =
                    bus.publishAsync(new NumberEvent(1)).toCompletableFuture().join();
            assertEquals(3, result.failures().size());
            assertSame(cause, result.failures().getFirst().cause());
            assertEquals(1, calls.get());
            assertEquals(3, bus.metrics().listenerFailures());
        }
    }

    @Test
    void failFastAndFatalErrorsCompleteExceptionally() {
        try (EventBus bus = EventBuses.builder()
                .asyncExecutor(Runnable::run)
                .exceptionHandler(_ -> {})
                .failurePolicy(FailurePolicy.FAIL_FAST)
                .build()) {
            bus.on(NumberEvent.class)
                    .subscribeAsync(_ -> CompletableFuture.failedFuture(new IllegalArgumentException("bad")));
            bus.subscribe(NumberEvent.class, _ -> fail("fail-fast must stop dispatch"));
            var result = bus.publishAsync(new NumberEvent(1)).toCompletableFuture();
            assertThrows(CompletionException.class, result::join);
            assertEquals(1, bus.metrics().listenerInvocations());
        }
        try (EventBus bus = bus()) {
            AssertionError fatal = new AssertionError("fatal");
            bus.on(NumberEvent.class).subscribeAsync(_ -> CompletableFuture.failedFuture(fatal));
            var result = bus.publishAsync(new NumberEvent(1)).toCompletableFuture();
            assertSame(
                    fatal, assertThrows(CompletionException.class, result::join).getCause());
        }
    }

    @Test
    void namedExecutorPolicyMovesHandlersAndWaitsForCompletion() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor(
                        task -> Thread.ofPlatform().name("handler-test").unstarted(task));
                EventBus bus = bus()) {
            CompletableFuture<String> thread = new CompletableFuture<>();
            bus.on(NumberEvent.class)
                    .execution(options -> options.executor(executor))
                    .subscribe(_ -> thread.complete(Thread.currentThread().getName()));
            assertTrue(bus.publishAsync(new NumberEvent(1))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS)
                    .successful());
            assertEquals("handler-test", thread.join());
        }
    }

    @Test
    void serialQueuesWaitForActualCompletionAndRejectExcessWork() {
        try (EventBus bus = bus()) {
            CompletableFuture<Void> first = new CompletableFuture<>();
            List<Integer> calls = new ArrayList<>();
            bus.on(NumberEvent.class)
                    .execution(options -> options.sequential().queueCapacity(2))
                    .subscribeAsync(event -> {
                        calls.add(event.value());
                        return event.value() == 1 ? first : CompletableFuture.completedFuture(null);
                    });
            var one = bus.publishAsync(new NumberEvent(1)).toCompletableFuture();
            var two = bus.publishAsync(new NumberEvent(2)).toCompletableFuture();
            var three =
                    bus.publishAsync(new NumberEvent(3)).toCompletableFuture().join();
            assertEquals(List.of(1), calls);
            assertEquals(FailureStage.EXECUTION, three.failures().getFirst().stage());
            assertFalse(two.isDone());
            first.completeExceptionally(new IllegalStateException("first failed"));
            assertFalse(one.join().successful());
            assertTrue(two.join().successful());
            assertEquals(List.of(1, 2), calls);
            assertTrue(bus.publishAsync(new NumberEvent(4))
                    .toCompletableFuture()
                    .join()
                    .successful());
        }
    }

    @Test
    void independentKeysOverlapWhileEqualKeysRemainOrdered() {
        try (EventBus bus = bus()) {
            CompletableFuture<Void> gate = new CompletableFuture<>();
            List<Integer> calls = new ArrayList<>();
            bus.on(NumberEvent.class)
                    .execution(options -> options.serialBy(event -> event.value() % 2))
                    .subscribeAsync(event -> {
                        calls.add(event.value());
                        return event.value() == 1 ? gate : CompletableFuture.completedFuture(null);
                    });
            var one = bus.publishAsync(new NumberEvent(1));
            var three = bus.publishAsync(new NumberEvent(3)).toCompletableFuture();
            var two = bus.publishAsync(new NumberEvent(2)).toCompletableFuture();
            assertTrue(two.isDone());
            assertFalse(three.isDone());
            assertEquals(List.of(1, 2), calls);
            gate.complete(null);
            assertTrue(one.toCompletableFuture().join().successful());
            assertTrue(three.join().successful());
            assertEquals(List.of(1, 2, 3), calls);
        }
    }

    @Test
    void timeoutDoesNotReleaseQueueSlotOrInterruptWork() throws Exception {
        try (EventBus bus = bus()) {
            CompletableFuture<Void> gate = new CompletableFuture<>();
            bus.on(NumberEvent.class)
                    .execution(options -> options.sequential().queueCapacity(1).timeout(Duration.ofMillis(1)))
                    .subscribeAsync(_ -> gate);
            var expired =
                    bus.publishAsync(new NumberEvent(1)).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(FailureStage.TIMEOUT, expired.failures().getFirst().stage());
            assertFalse(gate.isDone());
            var rejected =
                    bus.publishAsync(new NumberEvent(2)).toCompletableFuture().join();
            assertEquals(FailureStage.EXECUTION, rejected.failures().getFirst().stage());
            gate.complete(null);
            assertTrue(bus.publishAsync(new NumberEvent(3))
                    .toCompletableFuture()
                    .join()
                    .successful());
        }
    }

    @Test
    void executorRejectionAndInvalidKeysFailWithoutStrandingQueues() {
        try (EventBus bus = bus()) {
            bus.on(NumberEvent.class)
                    .execution(options -> options.executor(_ -> {
                                throw new RejectedExecutionException("executor full");
                            })
                            .sequential())
                    .subscribe(_ -> fail("rejected executor"));
            bus.on(NumberEvent.class)
                    .execution(options -> options.serialBy(_ -> null))
                    .subscribe(_ -> fail("null key"));
            var result =
                    bus.publishAsync(new NumberEvent(1)).toCompletableFuture().join();
            assertEquals(2, result.failures().size());
            assertEquals(FailureStage.EXECUTION, result.failures().getFirst().stage());
            assertInstanceOf(
                    NullPointerException.class, result.failures().getLast().cause());
        }
        try (EventBus bus = EventBuses.builder()
                .asyncExecutor(_ -> {
                    throw new RejectedExecutionException("dispatch executor full");
                })
                .build()) {
            var result = bus.publishAsync(new NumberEvent(1)).toCompletableFuture();
            assertTrue(result.isCompletedExceptionally());
        }
    }

    @Test
    void longSequentialBacklogsDrainWithoutRecursiveExecutorCalls() throws Exception {
        try (EventBus bus = bus()) {
            CompletableFuture<Void> gate = new CompletableFuture<>();
            AtomicInteger calls = new AtomicInteger();
            bus.on(NumberEvent.class)
                    .execution(options -> options.sequential().queueCapacity(3000))
                    .subscribeAsync(event -> {
                        calls.incrementAndGet();
                        return event.value() == 0 ? gate : CompletableFuture.completedFuture(null);
                    });
            List<CompletableFuture<dev.vriege.eventfw.dispatch.DispatchResult<NumberEvent>>> results =
                    new ArrayList<>();
            for (int index = 0; index < 2000; index++) {
                results.add(bus.publishAsync(new NumberEvent(index)).toCompletableFuture());
            }
            assertEquals(1, calls.get());
            gate.complete(null);
            CompletableFuture.allOf(results.toArray(CompletableFuture<?>[]::new))
                    .get(5, TimeUnit.SECONDS);
            assertEquals(2000, calls.get());
            assertTrue(results.stream().allMatch(result -> result.join().successful()));
        }
    }

    @Test
    void releasedCapacityIsAvailableInsideCompletionCallbacks() {
        try (EventBus bus = bus()) {
            CompletableFuture<Void> gate = new CompletableFuture<>();
            bus.on(NumberEvent.class)
                    .execution(options -> options.sequential().queueCapacity(1))
                    .subscribeAsync(event -> event.value() == 1 ? gate : CompletableFuture.completedFuture(null));
            var first = bus.publishAsync(new NumberEvent(1));
            var next = first.thenCompose(_ -> bus.publishAsync(new NumberEvent(2)));
            gate.complete(null);
            assertTrue(next.toCompletableFuture().join().successful());
        }
    }

    @Test
    void executionBuildersRejectInvalidLimitsAndTimeouts() {
        ExecutionBuilder<NumberEvent> builder = new ExecutionBuilder<>();
        Duration negative = Duration.ofSeconds(-1);
        Duration excessive = Duration.ofSeconds(Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> builder.queueCapacity(0));
        assertThrows(IllegalArgumentException.class, () -> builder.timeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> builder.timeout(negative));
        assertThrows(ArithmeticException.class, () -> builder.timeout(excessive));
        assertThrows(
                IllegalArgumentException.class, () -> new ExecutionOptions<NumberEvent>(Runnable::run, null, 0, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExecutionOptions<NumberEvent>(Runnable::run, null, 1, Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExecutionOptions<NumberEvent>(Runnable::run, null, 1, negative));
        assertEquals(256, builder.build().capacity());
        var busBuilder = EventBuses.builder();
        assertThrows(IllegalArgumentException.class, () -> busBuilder.executor(" ", Runnable::run));
    }

    private record NumberEvent(int value) implements Event {}
}
