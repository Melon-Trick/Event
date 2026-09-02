package dev.melontricks.eventfw.listener;

import dev.melontricks.eventfw.dispatch.EventPhase;
import dev.melontricks.eventfw.event.Event;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface Subscription extends Registration {
    long id();

    Class<? extends Event> eventType();

    int priority();

    boolean receivesCancelledEvents();

    boolean exactTypeOnly();

    Set<EventPhase> phases();

    boolean singleUse();

    boolean paused();

    void pause();

    void resume();

    Optional<Object> owner();

    @Override
    default List<Subscription> subscriptions() {
        return List.of(this);
    }
}
