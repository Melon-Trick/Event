package dev.vriege.eventfw.internal.execution;

import dev.vriege.eventfw.event.Event;
import dev.vriege.eventfw.execution.ExecutionOptions;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class InvocationDispatcher<E extends Event> {
    private final ExecutionOptions<E> options;
    private final Executor executor;
    private final Map<Object, CompletableFuture<Void>> tails = new HashMap<>();
    private int outstanding;

    public InvocationDispatcher(ExecutionOptions<E> options) {
        this.options = options;
        executor = new TrampolineExecutor(options.executor());
    }

    public CompletionStage<Void> submit(E event, Supplier<CompletionStage<?>> invocation) {
        Object key = options.serialKey() == null
                ? null
                : Objects.requireNonNull(options.serialKey().apply(event), "serial key");
        CompletableFuture<Void> work = new CompletableFuture<>();
        CompletableFuture<Void> predecessor;
        synchronized (this) {
            if (outstanding >= options.capacity()) {
                return CompletableFuture.failedFuture(new RejectedExecutionException("subscription queue is full"));
            }
            outstanding++;
            predecessor = key == null ? null : tails.put(key, work);
        }
        CompletableFuture<Void> result = work.copy();
        if (options.timeout() != null) {
            result.orTimeout(options.timeout().toNanos(), TimeUnit.NANOSECONDS);
        }
        if (predecessor == null) {
            execute(invocation, key, work);
        } else {
            predecessor.whenComplete((_, _) -> execute(invocation, key, work));
        }
        return result;
    }

    private synchronized void release(Object key, CompletableFuture<Void> work) {
        outstanding--;
        if (key != null) {
            tails.remove(key, work);
        }
    }

    private void execute(Supplier<CompletionStage<?>> invocation, Object key, CompletableFuture<Void> work) {
        try {
            CompletableFuture.supplyAsync(invocation, executor)
                    .thenCompose(stage -> Objects.requireNonNull(stage, "handler stage"))
                    .whenComplete((_, failure) -> finish(key, work, failure));
        } catch (RuntimeException failure) {
            finish(key, work, failure);
        }
    }

    private void finish(Object key, CompletableFuture<Void> work, Throwable failure) {
        TrampolineExecutor.executeInline(() -> complete(key, work, failure));
    }

    private void complete(Object key, CompletableFuture<Void> work, Throwable failure) {
        release(key, work);
        if (failure == null) {
            work.complete(null);
        } else {
            work.completeExceptionally(failure);
        }
    }
}
