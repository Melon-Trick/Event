package dev.vriege.eventfw.internal.bus;

import dev.vriege.eventfw.await.AwaitBuilder;
import dev.vriege.eventfw.bus.EventBus;
import dev.vriege.eventfw.bus.EventBusMetrics;
import dev.vriege.eventfw.bus.TypeMatching;
import dev.vriege.eventfw.diagnostic.Eligibility;
import dev.vriege.eventfw.diagnostic.SubscriptionDiagnostic;
import dev.vriege.eventfw.dispatch.DispatchOptions;
import dev.vriege.eventfw.dispatch.DispatchResult;
import dev.vriege.eventfw.dispatch.EventDispatchException;
import dev.vriege.eventfw.dispatch.EventExceptionHandler;
import dev.vriege.eventfw.dispatch.EventPhase;
import dev.vriege.eventfw.dispatch.FailurePolicy;
import dev.vriege.eventfw.dispatch.FailureStage;
import dev.vriege.eventfw.dispatch.ListenerFailure;
import dev.vriege.eventfw.event.CancellableEvent;
import dev.vriege.eventfw.event.Event;
import dev.vriege.eventfw.internal.annotation.AnnotatedListenerScanner;
import dev.vriege.eventfw.internal.annotation.AnnotatedSubscriptionFactory;
import dev.vriege.eventfw.internal.diagnostic.DispatchObservers;
import dev.vriege.eventfw.internal.listener.CompositeRegistration;
import dev.vriege.eventfw.internal.listener.DefaultSubscription;
import dev.vriege.eventfw.internal.listener.DefaultSubscriptionBuilder;
import dev.vriege.eventfw.internal.listener.IdentityKey;
import dev.vriege.eventfw.internal.listener.SubscriptionConfiguration;
import dev.vriege.eventfw.listener.ContextualEventHandler;
import dev.vriege.eventfw.listener.EventHandler;
import dev.vriege.eventfw.listener.EventSubscriptionBuilder;
import dev.vriege.eventfw.listener.Registration;
import dev.vriege.eventfw.listener.Subscription;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

public final class DefaultEventBus implements EventBus {
    private static final String EVENT_PARAMETER = "event";
    private static final String OPTIONS_PARAMETER = "options";
    private static final Comparator<Candidate> CANDIDATE_ORDER = Comparator.<Candidate>comparingInt(
                    candidate -> candidate.subscription().priority())
            .reversed()
            .thenComparingInt(Candidate::typeDistance)
            .thenComparingLong(candidate -> candidate.subscription().id());

    private final TypeMatching typeMatching;
    private final FailurePolicy failurePolicy;
    private final EventExceptionHandler exceptionHandler;
    private final Executor asyncExecutor;
    private final int maximumNestingDepth;
    private final ScheduledExecutorService scheduler;
    private final Map<String, Executor> namedExecutors;
    private final DispatchObservers observers;
    private final ConcurrentMap<Class<? extends Event>, CopyOnWriteArrayList<DefaultSubscription<?>>> subscriptions =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<IdentityKey, CopyOnWriteArrayList<DefaultSubscription<?>>> subscriptionsByOwner =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<? extends Event>, ResolvedCandidates> candidateCache = new ConcurrentHashMap<>();
    private final AtomicReference<FastCandidates> fastCandidates = new AtomicReference<>();
    private final AtomicLong nextSubscriptionId = new AtomicLong();
    private final AtomicLong nextDispatchSequence = new AtomicLong();
    private final AtomicLong subscriptionRevision = new AtomicLong();
    private final AtomicInteger activeSubscriptions = new AtomicInteger();
    private final LongAdder publishedEvents = new LongAdder();
    private final LongAdder listenerInvocations = new LongAdder();
    private final LongAdder skippedListeners = new LongAdder();
    private final LongAdder listenerFailures = new LongAdder();
    private final ThreadLocal<DispatchNesting> dispatchNesting = ThreadLocal.withInitial(DispatchNesting::new);
    private final AtomicBoolean closed = new AtomicBoolean();

    public DefaultEventBus(BusConfiguration configuration) {
        typeMatching = configuration.typeMatching();
        failurePolicy = configuration.failurePolicy();
        exceptionHandler = configuration.exceptionHandler();
        asyncExecutor = configuration.asyncExecutor();
        maximumNestingDepth = configuration.maximumNestingDepth();
        scheduler = configuration.scheduler();
        namedExecutors = configuration.namedExecutors();
        observers = new DispatchObservers(configuration.observers());
    }

    @Override
    public <E extends Event> Subscription subscribe(Class<E> eventType, EventHandler<? super E> handler) {
        return on(eventType).subscribe(handler);
    }

    @Override
    public <E extends Event> Subscription subscribe(Class<E> eventType, ContextualEventHandler<E> handler) {
        return on(eventType).subscribe(handler);
    }

    @Override
    public <E extends Event> EventSubscriptionBuilder<E> on(Class<E> eventType) {
        requireOpen();
        return new DefaultSubscriptionBuilder<>(this, Objects.requireNonNull(eventType, "eventType"));
    }

    @Override
    public <E extends Event> AwaitBuilder<E> await(Class<E> eventType) {
        requireOpen();
        return new AwaitBuilder<>(this, eventType, scheduler);
    }

    public synchronized <E extends Event> Subscription add(SubscriptionConfiguration<E> configuration) {
        requireOpen();
        DefaultSubscription<E> subscription =
                new DefaultSubscription<>(this, nextSubscriptionId.incrementAndGet(), configuration);
        subscriptions.compute(configuration.eventType(), (_, current) -> {
            CopyOnWriteArrayList<DefaultSubscription<?>> updated =
                    current == null ? new CopyOnWriteArrayList<>() : current;
            updated.add(subscription);
            return updated;
        });
        if (configuration.owner() != null) {
            subscriptionsByOwner.compute(new IdentityKey(configuration.owner()), (_, current) -> {
                CopyOnWriteArrayList<DefaultSubscription<?>> updated =
                        current == null ? new CopyOnWriteArrayList<>() : current;
                updated.add(subscription);
                return updated;
            });
        }
        activeSubscriptions.incrementAndGet();
        invalidateCandidates();
        if (closed()) {
            subscription.close();
            throw new IllegalStateException("event bus is closed");
        }
        if (configuration.lifetime() != null) {
            try {
                var expiration = scheduler.schedule(
                        subscription::close, configuration.lifetime().toNanos(), TimeUnit.NANOSECONDS);
                subscription.whenClosed().thenRun(() -> expiration.cancel(false));
            } catch (RuntimeException failure) {
                subscription.close();
                throw failure;
            }
        }
        return subscription;
    }

    @Override
    public Registration register(Object listener) {
        requireOpen();
        Object checkedListener = Objects.requireNonNull(listener, "listener");
        List<AnnotatedListenerScanner.AnnotatedMethod> methods =
                AnnotatedListenerScanner.methods(checkedListener.getClass());
        List<Subscription> registered = new ArrayList<>(methods.size());
        try {
            for (AnnotatedListenerScanner.AnnotatedMethod method : methods) {
                registered.add(AnnotatedSubscriptionFactory.install(this, namedExecutors, checkedListener, method));
            }
            return new CompositeRegistration(registered);
        } catch (RuntimeException failure) {
            new CompositeRegistration(registered).close();
            throw failure;
        }
    }

    @Override
    public int unregister(Object owner) {
        if (owner == null) {
            return 0;
        }
        List<DefaultSubscription<?>> owned = subscriptionsByOwner.remove(new IdentityKey(owner));
        if (owned == null) {
            return 0;
        }
        int removed = 0;
        for (DefaultSubscription<?> subscription : owned) {
            if (subscription.active()) {
                subscription.close();
                removed++;
            }
        }
        return removed;
    }

    @Override
    public int clear() {
        List<DefaultSubscription<?>> snapshot =
                subscriptions.values().stream().flatMap(List::stream).toList();
        int removed = 0;
        for (DefaultSubscription<?> subscription : snapshot) {
            if (subscription.active()) {
                subscription.close();
                removed++;
            }
        }
        return removed;
    }

    public synchronized void remove(DefaultSubscription<?> subscription) {
        subscriptions.computeIfPresent(subscription.eventType(), (_, current) -> {
            current.remove(subscription);
            return current.isEmpty() ? null : current;
        });
        Object owner = subscription.rawOwner();
        if (owner != null) {
            IdentityKey ownerKey = new IdentityKey(owner);
            subscriptionsByOwner.computeIfPresent(ownerKey, (_, current) -> {
                current.remove(subscription);
                return current.isEmpty() ? null : current;
            });
        }
        activeSubscriptions.decrementAndGet();
        invalidateCandidates();
    }

    @Override
    public <E extends Event> DispatchResult<E> publish(E event) {
        return publish(event, DispatchOptions.DEFAULT);
    }

    @Override
    public <E extends Event> DispatchResult<E> publish(E event, EventPhase phase) {
        return publish(event, optionsFor(phase));
    }

    @Override
    public <E extends Event> DispatchResult<E> publish(E event, DispatchOptions options) {
        requireOpen();
        E checkedEvent = Objects.requireNonNull(event, EVENT_PARAMETER);
        DispatchOptions checkedOptions = Objects.requireNonNull(options, OPTIONS_PARAMETER);
        DispatchNesting nesting = dispatchNesting.get();
        int parentDepth = nesting.depth;
        if (parentDepth >= maximumNestingDepth) {
            throw new IllegalStateException("maximum nested event dispatch depth exceeded");
        }
        nesting.depth = parentDepth + 1;
        try {
            return dispatch(checkedEvent, checkedOptions, parentDepth);
        } finally {
            nesting.depth = parentDepth;
        }
    }

    private <E extends Event> DispatchResult<E> dispatch(E event, DispatchOptions options, int depth) {
        long startedNanos = System.nanoTime();
        long sequence = nextDispatchSequence.incrementAndGet();
        publishedEvents.increment();
        DefaultEventContext<E> context = new DefaultEventContext<>(
                event, this, sequence, Instant.ofEpochMilli(System.currentTimeMillis()), options, depth);
        List<Candidate> candidates = candidatesFor(eventType(event));
        DispatchAccumulator accumulator = new DispatchAccumulator();
        try {
            for (int index = 0; index < candidates.size(); index++) {
                if (context.propagationStopped()) {
                    accumulator.skip(candidates.size() - index);
                    break;
                }
                dispatchTo(candidates.get(index).subscription(), event, context, accumulator);
            }
            DispatchResult<E> result = new DispatchResult<>(
                    event,
                    sequence,
                    options.phase(),
                    candidates.size(),
                    accumulator.invoked(),
                    accumulator.skipped(),
                    accumulator.failures(),
                    isCancelled(event),
                    context.propagationStopped(),
                    Duration.ofNanos(System.nanoTime() - startedNanos));
            observers.completed(context, result);
            return result;
        } catch (RuntimeException failure) {
            observers.failed(context, failure);
            throw failure;
        } finally {
            accumulator.recordMetrics();
        }
    }

    @SuppressWarnings("unchecked")
    private <E extends Event> void dispatchTo(
            DefaultSubscription<?> rawSubscription,
            E event,
            DefaultEventContext<E> context,
            DispatchAccumulator accumulator) {
        DefaultSubscription<E> subscription = (DefaultSubscription<E>) rawSubscription;
        if (!claim(subscription, event, context, accumulator, false)) {
            return;
        }
        try {
            subscription.invoke(event, context);
        } catch (RuntimeException exception) {
            handleFailure(event, subscription, FailureStage.HANDLER, exception, accumulator);
        }
    }

    private <E extends Event> boolean claim(
            DefaultSubscription<E> subscription,
            E event,
            DefaultEventContext<E> context,
            DispatchAccumulator accumulator,
            boolean asynchronous) {
        if (context.propagationStopped()
                || !subscription.channel().equals(context.channel())
                || !subscription.eligible(context.phase(), isCancelled(event))) {
            accumulator.skip(1);
            return false;
        }
        try {
            if (!subscription.test(event, context)) {
                accumulator.skip(1);
                return false;
            }
        } catch (RuntimeException exception) {
            accumulator.skip(1);
            handleFailure(event, subscription, FailureStage.FILTER, exception, accumulator);
            return false;
        }
        if (!asynchronous && subscription.asynchronous()) {
            accumulator.skip(1);
            handleFailure(
                    event,
                    subscription,
                    FailureStage.EXECUTION,
                    new IllegalStateException("subscription requires asynchronous publication"),
                    accumulator);
            return false;
        }
        if (!subscription.acquireForInvocation()) {
            accumulator.skip(1);
            return false;
        }
        accumulator.invoke();
        return true;
    }

    private void handleFailure(
            Event event,
            Subscription subscription,
            FailureStage stage,
            Throwable cause,
            DispatchAccumulator accumulator) {
        ListenerFailure failure = new ListenerFailure(event, subscription, stage, cause);
        accumulator.fail(failure);
        exceptionHandler.handle(failure);
        if (failurePolicy == FailurePolicy.FAIL_FAST) {
            throw new EventDispatchException(failure);
        }
    }

    @Override
    public <E extends Event> CompletionStage<DispatchResult<E>> publishAsync(E event) {
        return publishAsync(event, DispatchOptions.DEFAULT);
    }

    @Override
    public <E extends Event> CompletionStage<DispatchResult<E>> publishAsync(E event, EventPhase phase) {
        return publishAsync(event, optionsFor(phase));
    }

    private static DispatchOptions optionsFor(EventPhase phase) {
        return phase == EventPhase.DEFAULT ? DispatchOptions.DEFAULT : new DispatchOptions(phase, "", Map.of());
    }

    @Override
    public <E extends Event> CompletionStage<DispatchResult<E>> publishAsync(E event, DispatchOptions options) {
        E checkedEvent = Objects.requireNonNull(event, EVENT_PARAMETER);
        DispatchOptions checkedOptions = Objects.requireNonNull(options, OPTIONS_PARAMETER);
        requireOpen();
        try {
            return CompletableFuture.supplyAsync(() -> dispatchAsync(checkedEvent, checkedOptions), asyncExecutor)
                    .thenCompose(stage -> stage);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private <E extends Event> CompletionStage<DispatchResult<E>> dispatchAsync(E event, DispatchOptions options) {
        requireOpen();
        long startedNanos = System.nanoTime();
        long sequence = nextDispatchSequence.incrementAndGet();
        publishedEvents.increment();
        int depth = dispatchNesting.get().depth;
        if (depth >= maximumNestingDepth) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("maximum nested event dispatch depth exceeded"));
        }
        DefaultEventContext<E> context =
                new DefaultEventContext<>(event, this, sequence, Instant.now(), options, depth);
        List<Candidate> candidates = candidatesFor(eventType(event));
        DispatchAccumulator accumulator = new DispatchAccumulator();
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (Candidate candidate : candidates) {
            chain = chain.thenCompose(_ -> dispatchToAsync(candidate.subscription(), event, context, accumulator));
        }
        return chain.thenApply(_ -> new DispatchResult<>(
                        event,
                        sequence,
                        options.phase(),
                        candidates.size(),
                        accumulator.invoked(),
                        accumulator.skipped(),
                        accumulator.failures(),
                        isCancelled(event),
                        context.propagationStopped(),
                        Duration.ofNanos(System.nanoTime() - startedNanos)))
                .whenComplete((result, failure) -> {
                    accumulator.recordMetrics();
                    if (failure == null) {
                        observers.completed(context, result);
                    } else {
                        observers.failed(context, unwrap(failure));
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private <E extends Event> CompletionStage<Void> dispatchToAsync(
            DefaultSubscription<?> raw, E event, DefaultEventContext<E> context, DispatchAccumulator accumulator) {
        DefaultSubscription<E> subscription = (DefaultSubscription<E>) raw;
        if (!claim(subscription, event, context, accumulator, true)) {
            return CompletableFuture.completedFuture(null);
        }
        CompletionStage<?> invocation;
        DispatchNesting nesting = dispatchNesting.get();
        int previousDepth = nesting.depth;
        nesting.depth = Math.max(previousDepth, context.nestingDepth() + 1);
        try {
            invocation = subscription.invokeAsync(event, context);
        } catch (RuntimeException failure) {
            invocation = CompletableFuture.failedFuture(failure);
        } finally {
            nesting.depth = previousDepth;
        }
        return invocation.handle((_, failure) -> {
            if (failure != null) {
                Throwable cause = unwrap(failure);
                if (cause instanceof Error error) {
                    throw error;
                }
                handleFailure(event, subscription, failureStage(cause), cause, accumulator);
            }
            return null;
        });
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static FailureStage failureStage(Throwable cause) {
        if (cause instanceof TimeoutException) {
            return FailureStage.TIMEOUT;
        }
        return cause instanceof RejectedExecutionException ? FailureStage.EXECUTION : FailureStage.HANDLER;
    }

    @Override
    public List<Subscription> subscriptions() {
        return subscriptions.values().stream()
                .flatMap(List::stream)
                .filter(Subscription::active)
                .sorted(Comparator.comparingLong(Subscription::id))
                .map(Subscription.class::cast)
                .toList();
    }

    @Override
    public List<SubscriptionDiagnostic> inspect(Event event, DispatchOptions options) {
        requireOpen();
        Objects.requireNonNull(options, OPTIONS_PARAMETER);
        return candidatesFor(eventType(Objects.requireNonNull(event, EVENT_PARAMETER))).stream()
                .map(candidate -> new SubscriptionDiagnostic(
                        candidate.subscription(), eligibility(candidate.subscription(), event, options)))
                .toList();
    }

    private static Eligibility eligibility(Subscription subscription, Event event, DispatchOptions options) {
        if (!subscription.active()) {
            return Eligibility.CLOSED;
        }
        if (subscription.paused()) {
            return Eligibility.PAUSED;
        }
        if (!subscription.channel().equals(options.channel())) {
            return Eligibility.CHANNEL_MISMATCH;
        }
        if (!subscription.phases().contains(options.phase())) {
            return Eligibility.PHASE_MISMATCH;
        }
        return isCancelled(event) && !subscription.receivesCancelledEvents()
                ? Eligibility.CANCELLED
                : Eligibility.ELIGIBLE;
    }

    @Override
    public int subscriptionCount() {
        return activeSubscriptions.get();
    }

    @Override
    public EventBusMetrics metrics() {
        return new EventBusMetrics(
                publishedEvents.sum(),
                listenerInvocations.sum(),
                skippedListeners.sum(),
                listenerFailures.sum(),
                activeSubscriptions.get());
    }

    @Override
    public boolean closed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            clear();
            candidateCache.clear();
            fastCandidates.set(null);
        }
        dispatchNesting.remove();
    }

    private List<Candidate> candidatesFor(Class<? extends Event> concreteType) {
        while (true) {
            long revision = subscriptionRevision.get();
            FastCandidates fast = fastCandidates.get();
            if (fast != null
                    && fast.concreteType() == concreteType
                    && fast.resolved().revision() == revision) {
                return fast.resolved().candidates();
            }
            ResolvedCandidates cached = candidateCache.get(concreteType);
            if (cached != null && cached.revision() == revision) {
                fastCandidates.set(new FastCandidates(concreteType, cached));
                return cached.candidates();
            }
            List<Candidate> resolved = resolveCandidates(concreteType);
            if (subscriptionRevision.get() == revision) {
                ResolvedCandidates current = new ResolvedCandidates(revision, resolved);
                candidateCache.put(concreteType, current);
                fastCandidates.set(new FastCandidates(concreteType, current));
                return resolved;
            }
        }
    }

    private List<Candidate> resolveCandidates(Class<? extends Event> concreteType) {
        Map<Class<? extends Event>, Integer> hierarchy =
                typeMatching == TypeMatching.EXACT ? Map.of(concreteType, 0) : eventHierarchy(concreteType);
        List<Candidate> candidates = new ArrayList<>();
        hierarchy.forEach((type, distance) -> {
            List<DefaultSubscription<?>> typedSubscriptions = subscriptions.get(type);
            if (typedSubscriptions == null) {
                return;
            }
            for (DefaultSubscription<?> subscription : typedSubscriptions) {
                if (distance == 0 || !subscription.exactTypeOnly()) {
                    candidates.add(new Candidate(subscription, distance));
                }
            }
        });
        candidates.sort(CANDIDATE_ORDER);
        return List.copyOf(candidates);
    }

    private static Map<Class<? extends Event>, Integer> eventHierarchy(Class<? extends Event> concreteType) {
        Map<Class<? extends Event>, Integer> distances = new HashMap<>();
        ArrayDeque<TypeDistance> pending = new ArrayDeque<>();
        pending.add(new TypeDistance(concreteType, 0));
        while (!pending.isEmpty()) {
            TypeDistance current = pending.removeFirst();
            if (Event.class.isAssignableFrom(current.type())) {
                Class<? extends Event> eventType = eventType(current.type());
                Integer previous = distances.putIfAbsent(eventType, current.distance());
                if (previous == null || previous > current.distance()) {
                    addParentTypes(pending, current);
                }
            }
        }
        return distances;
    }

    private static void addParentTypes(ArrayDeque<TypeDistance> pending, TypeDistance current) {
        Class<?> superclass = current.type().getSuperclass();
        if (superclass != null) {
            pending.addLast(new TypeDistance(superclass, current.distance() + 1));
        }
        for (Class<?> interfaceType : current.type().getInterfaces()) {
            pending.addLast(new TypeDistance(interfaceType, current.distance() + 1));
        }
    }

    private void invalidateCandidates() {
        subscriptionRevision.incrementAndGet();
        fastCandidates.set(null);
        candidateCache.clear();
    }

    private void requireOpen() {
        if (closed()) {
            throw new IllegalStateException("event bus is closed");
        }
    }

    private static boolean isCancelled(Event event) {
        return event instanceof CancellableEvent cancellable && cancellable.cancelled();
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Event> eventType(Class<?> type) {
        return (Class<? extends Event>) type;
    }

    private static Class<? extends Event> eventType(Event event) {
        return eventType(event.getClass());
    }

    private record Candidate(DefaultSubscription<?> subscription, int typeDistance) {}

    private record FastCandidates(Class<? extends Event> concreteType, ResolvedCandidates resolved) {}

    private record ResolvedCandidates(long revision, List<Candidate> candidates) {}

    private record TypeDistance(Class<?> type, int distance) {}

    private static final class DispatchNesting {
        private int depth;
    }

    private final class DispatchAccumulator {
        private List<ListenerFailure> failures;
        private int invoked;
        private int skipped;

        void invoke() {
            invoked++;
        }

        void skip(int count) {
            skipped += count;
        }

        void fail(ListenerFailure failure) {
            if (failures == null) {
                failures = new ArrayList<>();
            }
            failures.add(failure);
        }

        int invoked() {
            return invoked;
        }

        int skipped() {
            return skipped;
        }

        List<ListenerFailure> failures() {
            return failures == null ? List.of() : failures;
        }

        void recordMetrics() {
            if (invoked != 0) {
                listenerInvocations.add(invoked);
            }
            if (skipped != 0) {
                skippedListeners.add(skipped);
            }
            if (failures != null) {
                listenerFailures.add(failures.size());
            }
        }
    }
}
