package dev.vriege.eventfw.event;

import java.util.Objects;

/**
 * Identifies a typed channel. Keys with equal names and event classes are interchangeable. The empty name represents
 * the default channel; channels never implicitly receive each other's events.
 *
 * @param eventType non-null event contract accepted by this key
 * @param name non-null channel name
 * @param <E> event contract
 */
public record EventKey<E extends Event>(Class<E> eventType, String name) {
    /** Validates the event contract and channel name. */
    public EventKey {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(name, "name");
    }
}
