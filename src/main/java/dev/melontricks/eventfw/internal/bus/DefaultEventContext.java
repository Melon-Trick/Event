package dev.melontricks.eventfw.internal.bus;

import dev.melontricks.eventfw.bus.EventPublisher;
import dev.melontricks.eventfw.dispatch.EventContext;
import dev.melontricks.eventfw.dispatch.EventPhase;
import dev.melontricks.eventfw.event.Event;
import java.time.Instant;
import java.util.Objects;

final class DefaultEventContext<E extends Event> implements EventContext<E> {
    private final E event;
    private final EventPublisher publisher;
    private final long sequence;
    private final Instant publishedAt;
    private final EventPhase phase;
    private final int nestingDepth;
    private volatile boolean propagationStopped;

    DefaultEventContext(
            E event, EventPublisher publisher, long sequence, Instant publishedAt, EventPhase phase, int nestingDepth) {
        this.event = Objects.requireNonNull(event, "event");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.sequence = sequence;
        this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.nestingDepth = nestingDepth;
    }

    @Override
    public E event() {
        return event;
    }

    @Override
    public EventPublisher publisher() {
        return publisher;
    }

    @Override
    public long sequence() {
        return sequence;
    }

    @Override
    public Instant publishedAt() {
        return publishedAt;
    }

    @Override
    public EventPhase phase() {
        return phase;
    }

    @Override
    public int nestingDepth() {
        return nestingDepth;
    }

    @Override
    public boolean propagationStopped() {
        return propagationStopped;
    }

    @Override
    public void stopPropagation() {
        propagationStopped = true;
    }
}
