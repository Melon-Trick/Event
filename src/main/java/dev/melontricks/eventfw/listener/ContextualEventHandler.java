package dev.melontricks.eventfw.listener;

import dev.melontricks.eventfw.dispatch.EventContext;
import dev.melontricks.eventfw.event.Event;

@FunctionalInterface
public interface ContextualEventHandler<E extends Event> {
    void handle(E event, EventContext<E> context);
}
