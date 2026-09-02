package dev.melontricks.eventfw.listener;

import dev.melontricks.eventfw.event.Event;
import java.util.List;
import java.util.Optional;

public interface Subscription extends Registration {
    long id();

    Class<? extends Event> eventType();

    int priority();

    boolean receivesCancelledEvents();

    boolean exactTypeOnly();

    Optional<Object> owner();

    @Override
    default List<Subscription> subscriptions() {
        return List.of(this);
    }
}
