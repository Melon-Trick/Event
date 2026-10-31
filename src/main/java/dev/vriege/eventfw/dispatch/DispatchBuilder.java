package dev.vriege.eventfw.dispatch;

import dev.vriege.eventfw.bus.EventPublisher;
import dev.vriege.eventfw.event.Event;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Configures publications without dispatching until a terminal method is called. Builders are reusable, mutable, and
 * not thread-safe. Each terminal call captures an immutable options snapshot.
 *
 * @param <E> published event type
 */
public final class DispatchBuilder<E extends Event> {
    private final EventPublisher publisher;
    private final E event;
    private final Map<AttributeKey<?>, Object> attributes = new HashMap<>();
    private EventPhase phase = EventPhase.DEFAULT;
    private String channel = "";

    /**
     * Creates a builder without publishing.
     *
     * @param publisher non-null target publisher
     * @param event non-null event retained by reference
     */
    public DispatchBuilder(EventPublisher publisher, E event) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.event = Objects.requireNonNull(event, "event");
    }

    /**
     * Selects one phase; it does not run a multiphase pipeline.
     *
     * @param value non-null phase
     * @return this builder
     */
    public DispatchBuilder<E> phase(EventPhase value) {
        phase = Objects.requireNonNull(value, "phase");
        return this;
    }

    /**
     * Selects a channel, replacing the previous selection.
     *
     * @param value non-null channel name
     * @return this builder
     */
    public DispatchBuilder<E> channel(String value) {
        channel = Objects.requireNonNull(value, "channel");
        return this;
    }

    /**
     * Adds or replaces one typed attribute.
     *
     * @param key non-null attribute identity
     * @param value non-null value checked against the key's reference type
     * @param <T> value type
     * @return this builder
     */
    public <T> DispatchBuilder<E> attribute(AttributeKey<T> key, T value) {
        attributes.put(Objects.requireNonNull(key, "key"), key.type().cast(Objects.requireNonNull(value, "value")));
        return this;
    }

    /**
     * Publishes on the calling thread using a snapshot of the current options.
     *
     * @return completed dispatch summary
     */
    public DispatchResult<E> publish() {
        return publisher.publish(event, options());
    }

    /**
     * Starts ordered asynchronous dispatch, waiting for every invoked handler's completion stage.
     *
     * @return completion of the entire dispatch
     */
    public CompletionStage<DispatchResult<E>> publishAsync() {
        return publisher.publishAsync(event, options());
    }

    private DispatchOptions options() {
        return new DispatchOptions(phase, channel, attributes);
    }
}
