package dev.melontricks.eventfw.bus;

import dev.melontricks.eventfw.dispatch.DispatchResult;
import dev.melontricks.eventfw.dispatch.EventPhase;
import dev.melontricks.eventfw.event.Event;
import java.util.concurrent.CompletionStage;

public interface EventPublisher {
    <E extends Event> DispatchResult<E> publish(E event);

    <E extends Event> DispatchResult<E> publish(E event, EventPhase phase);

    <E extends Event> CompletionStage<DispatchResult<E>> publishAsync(E event);

    <E extends Event> CompletionStage<DispatchResult<E>> publishAsync(E event, EventPhase phase);
}
