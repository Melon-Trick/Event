package dev.melontricks.eventfw.internal.bus;

import dev.melontricks.eventfw.bus.EventBus;
import dev.melontricks.eventfw.bus.EventBusMetrics;
import dev.melontricks.eventfw.bus.TypeMatching;
import dev.melontricks.eventfw.dispatch.DispatchResult;
import dev.melontricks.eventfw.dispatch.EventDispatchException;
import dev.melontricks.eventfw.dispatch.EventExceptionHandler;
import dev.melontricks.eventfw.dispatch.EventPhase;
import dev.melontricks.eventfw.dispatch.FailurePolicy;
import dev.melontricks.eventfw.dispatch.FailureStage;
import dev.melontricks.eventfw.dispatch.ListenerFailure;
import dev.melontricks.eventfw.event.CancellableEvent;
import dev.melontricks.eventfw.event.Event;
import dev.melontricks.eventfw.internal.annotation.AnnotatedListenerScanner;
import dev.melontricks.eventfw.internal.listener.CompositeRegistration;
import dev.melontricks.eventfw.internal.listener.DefaultSubscription;
import dev.melontricks.eventfw.internal.listener.DefaultSubscriptionBuilder;
import dev.melontricks.eventfw.internal.listener.IdentityKey;
import dev.melontricks.eventfw.internal.listener.SubscriptionConfiguration;
import dev.melontricks.eventfw.listener.ContextualEventHandler;
import dev.melontricks.eventfw.listener.EventHandler;
import dev.melontricks.eventfw.listener.EventSubscriptionBuilder;
import dev.melontricks.eventfw.listener.Registration;
import dev.melontricks.eventfw.listener.Subscription;
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class DefaultEventBus implements EventBus {
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
    private final ConcurrentMap<Class<? extends Event>, CopyOnWriteArrayList<DefaultSubscription<?>>> subscriptions =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<IdentityKey, CopyOnWriteArrayList<DefaultSubscription<?>>> subscriptionsByOwner =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<? extends Event>, ResolvedCandidates> candidateCache = new ConcurrentHashMap<>();
    private final AtomicLong nextSubscriptionId = new AtomicLong();
    private final AtomicLong nextDispatchSequence = new AtomicLong();
    private final AtomicLong subscriptionRevision = new AtomicLong();
    private final AtomicInteger activeSubscriptions = new AtomicInteger();
    private final LongAdder publishedEvents = new LongAdder();
    private final LongAdder listenerInvocations = new LongAdder();
    private final LongAdder skippedListeners = new LongAdder();
    private final LongAdder listenerFailures = new LongAdder();
    private final ThreadLocal<Integer> nestingDepth = ThreadLocal.withInitial(() -> 0);
    private final AtomicBoolean closed = new AtomicBoolean();

    public DefaultEventBus(
            TypeMatching typeMatching,
            FailurePolicy failurePolicy,
            EventExceptionHandler exceptionHandler,
            Executor asyncExecutor,
            int maximumNestingDepth) {
        this.typeMatching = Objects.requireNonNull(typeMatching, "typeMatching");
        this.failurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy");
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler");
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor");
        this.maximumNestingDepth = maximumNestingDepth;
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

    public <E extends Event> Subscription add(SubscriptionConfiguration<E> configuration) {
        requireOpen();
        DefaultSubscription<E> subscription =
                new DefaultSubscription<>(this, nextSubscriptionId.incrementAndGet(), configuration);
        subscriptions.compute(configuration.eventType(), (eventType, current) -> {
            CopyOnWriteArrayList<DefaultSubscription<?>> updated =
                    current == null ? new CopyOnWriteArrayList<>() : current;
            updated.add(subscription);
            return updated;
        });
        if (configuration.owner() != null) {
            subscriptionsByOwner.compute(new IdentityKey(configuration.owner()), (owner, current) -> {
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
                registered.add(registerMethod(checkedListener, method));
            }
            return new CompositeRegistration(registered);
        } catch (RuntimeException failure) {
            new CompositeRegistration(registered).close();
            throw failure;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Subscription registerMethod(Object listener, AnnotatedListenerScanner.AnnotatedMethod method) {
        Class eventType = method.eventType();
        EventSubscriptionBuilder builder =
                on(eventType).priority(method.priority()).owner(listener);
        if (method.receivesCancelledEvents()) {
            builder.receiveCancelledEvents();
        }
        if (method.exactTypeOnly()) {
            builder.exactTypeOnly();
        }
        if (!method.phases().isEmpty()) {
            builder.phases(method.phases());
        }
        if (method.singleUse()) {
            builder.once();
        }
        return builder.subscribe(
                (ContextualEventHandler<Event>) (event, context) -> method.invoke(listener, event, context));
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

    public void remove(DefaultSubscription<?> subscription) {
        subscriptions.computeIfPresent(subscription.eventType(), (eventType, current) -> {
            current.remove(subscription);
            return current.isEmpty() ? null : current;
        });
        Object owner = subscription.rawOwner();
        if (owner != null) {
            IdentityKey ownerKey = new IdentityKey(owner);
            subscriptionsByOwner.computeIfPresent(ownerKey, (key, current) -> {
                current.remove(subscription);
                return current.isEmpty() ? null : current;
            });
        }
        activeSubscriptions.decrementAndGet();
        invalidateCandidates();
    }

    @Override
    public <E extends Event> DispatchResult<E> publish(E event) {
        return publish(event, EventPhase.DEFAULT);
    }

    @Override
    public <E extends Event> DispatchResult<E> publish(E event, EventPhase phase) {
        requireOpen();
        E checkedEvent = Objects.requireNonNull(event, "event");
        EventPhase checkedPhase = Objects.requireNonNull(phase, "phase");
        int parentDepth = nestingDepth.get();
        if (parentDepth >= maximumNestingDepth) {
            throw new IllegalStateException("maximum nested event dispatch depth exceeded");
        }
        nestingDepth.set(parentDepth + 1);
        try {
            return dispatch(checkedEvent, checkedPhase, parentDepth);
        } finally {
            if (parentDepth == 0) {
                nestingDepth.remove();
            } else {
                nestingDepth.set(parentDepth);
            }
        }
    }

    private <E extends Event> DispatchResult<E> dispatch(E event, EventPhase phase, int depth) {
        long startedNanos = System.nanoTime();
        long sequence = nextDispatchSequence.incrementAndGet();
        publishedEvents.increment();
        DefaultEventContext<E> context = new DefaultEventContext<>(event, this, sequence, Instant.now(), phase, depth);
        List<Candidate> candidates = candidatesFor(eventType(event));
        DispatchAccumulator accumulator = new DispatchAccumulator();
        for (int index = 0; index < candidates.size(); index++) {
            if (context.propagationStopped()) {
                accumulator.skip(candidates.size() - index);
                break;
            }
            dispatchTo(candidates.get(index).subscription(), event, context, accumulator);
        }
        return new DispatchResult<>(
                event,
                sequence,
                phase,
                candidates.size(),
                accumulator.invoked(),
                accumulator.skipped(),
                accumulator.failures(),
                isCancelled(event),
                context.propagationStopped(),
                Duration.ofNanos(System.nanoTime() - startedNanos));
    }

    @SuppressWarnings("unchecked")
    private <E extends Event> void dispatchTo(
            DefaultSubscription<?> rawSubscription,
            E event,
            DefaultEventContext<E> context,
            DispatchAccumulator accumulator) {
        DefaultSubscription<E> subscription = (DefaultSubscription<E>) rawSubscription;
        if (!subscription.active()
                || subscription.paused()
                || !subscription.phases().contains(context.phase())
                || isCancelled(event) && !subscription.receivesCancelledEvents()) {
            accumulator.skip(1);
            return;
        }
        try {
            if (!subscription.test(event, context)) {
                accumulator.skip(1);
                return;
            }
        } catch (RuntimeException exception) {
            accumulator.skip(1);
            handleFailure(event, subscription, FailureStage.FILTER, exception, accumulator);
            return;
        }
        if (!subscription.acquireForInvocation()) {
            accumulator.skip(1);
            return;
        }
        accumulator.invoke();
        try {
            subscription.invoke(event, context);
        } catch (RuntimeException exception) {
            handleFailure(event, subscription, FailureStage.HANDLER, exception, accumulator);
        }
    }

    private void handleFailure(
            Event event,
            Subscription subscription,
            FailureStage stage,
            RuntimeException cause,
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
        return publishAsync(event, EventPhase.DEFAULT);
    }

    @Override
    public <E extends Event> CompletionStage<DispatchResult<E>> publishAsync(E event, EventPhase phase) {
        E checkedEvent = Objects.requireNonNull(event, "event");
        EventPhase checkedPhase = Objects.requireNonNull(phase, "phase");
        requireOpen();
        return CompletableFuture.supplyAsync(() -> publish(checkedEvent, checkedPhase), asyncExecutor);
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
        }
    }

    private List<Candidate> candidatesFor(Class<? extends Event> concreteType) {
        while (true) {
            long revision = subscriptionRevision.get();
            ResolvedCandidates cached = candidateCache.get(concreteType);
            if (cached != null && cached.revision() == revision) {
                return cached.candidates();
            }
            List<Candidate> resolved = resolveCandidates(concreteType);
            if (subscriptionRevision.get() == revision) {
                candidateCache.put(concreteType, new ResolvedCandidates(revision, resolved));
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

    private record ResolvedCandidates(long revision, List<Candidate> candidates) {}

    private record TypeDistance(Class<?> type, int distance) {}

    private final class DispatchAccumulator {
        private final List<ListenerFailure> failures = new ArrayList<>();
        private int invoked;
        private int skipped;

        void invoke() {
            invoked++;
            listenerInvocations.increment();
        }

        void skip(int count) {
            skipped += count;
            skippedListeners.add(count);
        }

        void fail(ListenerFailure failure) {
            failures.add(failure);
            listenerFailures.increment();
        }

        int invoked() {
            return invoked;
        }

        int skipped() {
            return skipped;
        }

        List<ListenerFailure> failures() {
            return List.copyOf(failures);
        }
    }
}
