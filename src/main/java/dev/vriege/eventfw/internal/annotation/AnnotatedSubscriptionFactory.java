package dev.vriege.eventfw.internal.annotation;

import dev.vriege.eventfw.annotation.Channel;
import dev.vriege.eventfw.annotation.Execution;
import dev.vriege.eventfw.annotation.ExpireAfter;
import dev.vriege.eventfw.annotation.FilterWith;
import dev.vriege.eventfw.annotation.InvalidListenerException;
import dev.vriege.eventfw.annotation.InvocationLimit;
import dev.vriege.eventfw.bus.EventBus;
import dev.vriege.eventfw.event.Event;
import dev.vriege.eventfw.listener.ContextualAsyncEventHandler;
import dev.vriege.eventfw.listener.ContextualEventHandler;
import dev.vriege.eventfw.listener.EventFilter;
import dev.vriege.eventfw.listener.EventSubscriptionBuilder;
import dev.vriege.eventfw.listener.Subscription;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public final class AnnotatedSubscriptionFactory {
    private AnnotatedSubscriptionFactory() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Subscription install(
            EventBus bus,
            Map<String, Executor> executors,
            Object listener,
            AnnotatedListenerScanner.AnnotatedMethod descriptor) {
        EventSubscriptionBuilder<Event> builder = bus.on((Class) descriptor.eventType())
                .priority(descriptor.priority())
                .owner(listener);
        if (descriptor.receivesCancelledEvents()) {
            builder.receiveCancelledEvents();
        }
        if (descriptor.exactTypeOnly()) {
            builder.exactTypeOnly();
        }
        if (!descriptor.phases().isEmpty()) {
            builder.phases(descriptor.phases());
        }
        if (descriptor.singleUse()) {
            builder.once();
        }
        configureLifecycle(builder, descriptor);
        configureExecution(builder, descriptor, listener.getClass(), executors);
        Channel channel = descriptor.method().getAnnotation(Channel.class);
        if (channel == null) {
            channel = listener.getClass().getAnnotation(Channel.class);
        }
        if (channel != null) {
            builder.channel(channel.value());
        }
        FilterWith filters = descriptor.method().getAnnotation(FilterWith.class);
        if (filters != null) {
            for (Class<? extends EventFilter<?>> type : filters.value()) {
                builder.filter((EventFilter<Event>) createFilter(type));
            }
        }
        if (CompletionStage.class.isAssignableFrom(descriptor.method().getReturnType())) {
            return builder.subscribeAsync((ContextualAsyncEventHandler<Event>)
                    (event, context) -> (CompletionStage<?>) descriptor.invoke(listener, event, context));
        }
        return builder.subscribe(
                (ContextualEventHandler<Event>) (event, context) -> descriptor.invoke(listener, event, context));
    }

    private static void configureLifecycle(
            EventSubscriptionBuilder<Event> builder, AnnotatedListenerScanner.AnnotatedMethod descriptor) {
        InvocationLimit limit = descriptor.method().getAnnotation(InvocationLimit.class);
        if (limit != null) {
            builder.limit(limit.value());
        }
        ExpireAfter expiration = descriptor.method().getAnnotation(ExpireAfter.class);
        if (expiration != null) {
            builder.expireAfter(
                    AnnotatedListenerScanner.duration(descriptor.method(), expiration.value(), expiration.unit()));
        }
    }

    private static void configureExecution(
            EventSubscriptionBuilder<Event> builder,
            AnnotatedListenerScanner.AnnotatedMethod descriptor,
            Class<?> listenerType,
            Map<String, Executor> executors) {
        Execution methodExecution = descriptor.method().getAnnotation(Execution.class);
        Execution execution = methodExecution == null ? listenerType.getAnnotation(Execution.class) : methodExecution;
        if (execution == null) {
            return;
        }
        AnnotatedListenerScanner.validateExecution(descriptor.method(), execution);
        Executor executor = execution.executor().isEmpty() ? Runnable::run : executors.get(execution.executor());
        if (executor == null) {
            throw new InvalidListenerException("Unknown executor: " + execution.executor());
        }
        builder.execution(options -> {
            options.executor(executor).queueCapacity(execution.capacity());
            if (execution.sequential()) {
                options.sequential();
            }
            if (execution.timeout() > 0) {
                options.timeout(AnnotatedListenerScanner.duration(
                        descriptor.method(), execution.timeout(), execution.timeoutUnit()));
            }
        });
    }

    private static EventFilter<?> createFilter(Class<? extends EventFilter<?>> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            if (!constructor.trySetAccessible()) {
                throw new InvalidListenerException("Cannot access filter constructor: " + type.getName());
            }
            return constructor.newInstance();
        } catch (ReflectiveOperationException failure) {
            throw new InvalidListenerException("Cannot construct filter: " + type.getName(), failure);
        }
    }
}
