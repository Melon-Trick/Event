package dev.vriege.eventfw.internal.bus;

import dev.vriege.eventfw.bus.EventPublisher;
import dev.vriege.eventfw.dispatch.AttributeKey;
import dev.vriege.eventfw.dispatch.DispatchOptions;
import dev.vriege.eventfw.dispatch.EventContext;
import dev.vriege.eventfw.dispatch.EventPhase;
import dev.vriege.eventfw.event.Event;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

final class DefaultEventContext<E extends Event> implements EventContext<E> {
    private final E event;
    private final EventPublisher publisher;
    private final long sequence;
    private final Instant publishedAt;
    private final EventPhase phase;
    private final DispatchOptions options;
    private final int nestingDepth;
    private volatile boolean propagationStopped;

    DefaultEventContext(
            E event,
            EventPublisher publisher,
            long sequence,
            Instant publishedAt,
            DispatchOptions options,
            int nestingDepth) {
        this.event = Objects.requireNonNull(event, "event");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.sequence = sequence;
        this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
        this.options = Objects.requireNonNull(options, "options");
        this.phase = options.phase();
        this.nestingDepth = nestingDepth;
    }

    @Override
    public E event() {
        return event;
    }

    @Override
    public String channel() {
        return options.channel();
    }

    @Override
    public Map<AttributeKey<?>, Object> attributes() {
        return options.attributes();
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
