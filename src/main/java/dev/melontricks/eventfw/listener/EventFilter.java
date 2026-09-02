package dev.melontricks.eventfw.listener;

import dev.melontricks.eventfw.dispatch.EventContext;
import dev.melontricks.eventfw.event.Event;

@FunctionalInterface
public interface EventFilter<E extends Event> {
    boolean test(E event, EventContext<E> context) throws Exception;
}
