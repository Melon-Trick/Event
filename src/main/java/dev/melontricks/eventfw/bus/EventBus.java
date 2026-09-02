package dev.melontricks.eventfw.bus;

import dev.melontricks.eventfw.event.Event;
import dev.melontricks.eventfw.listener.ContextualEventHandler;
import dev.melontricks.eventfw.listener.EventHandler;
import dev.melontricks.eventfw.listener.EventSubscriptionBuilder;
import dev.melontricks.eventfw.listener.Registration;
import dev.melontricks.eventfw.listener.Subscription;

public interface EventBus extends EventPublisher, AutoCloseable {
    <E extends Event> Subscription subscribe(Class<E> eventType, EventHandler<? super E> handler);

    <E extends Event> Subscription subscribe(Class<E> eventType, ContextualEventHandler<E> handler);

    <E extends Event> EventSubscriptionBuilder<E> on(Class<E> eventType);

    Registration register(Object listener);

    int unregister(Object owner);

    int clear();

    int subscriptionCount();

    EventBusMetrics metrics();

    boolean closed();

    @Override
    void close();
}
