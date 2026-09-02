package dev.melontricks.eventfw.listener;

import dev.melontricks.eventfw.event.Event;

@FunctionalInterface
public interface EventHandler<E extends Event> {
    void handle(E event) throws Exception;
}
