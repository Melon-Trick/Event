package dev.vriege.eventfw.dispatch;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable selection and metadata for a single publication. Attribute values are retained without deep copying.
 *
 * @param phase non-null selection phase; publication does not automatically advance to other phases
 * @param channel non-null channel, empty for the default channel
 * @param attributes non-null map of non-null, runtime-type-checked attributes
 */
public record DispatchOptions(EventPhase phase, String channel, Map<AttributeKey<?>, Object> attributes) {
    /** Default-phase publication on the default channel without attributes. */
    public static final DispatchOptions DEFAULT = new DispatchOptions(EventPhase.DEFAULT, "", Map.of());

    /**
     * Copies and validates attributes before any dispatch begins.
     *
     * @throws NullPointerException if any component, key, or value is null
     * @throws ClassCastException if an attribute does not match its key's type
     */
    public DispatchOptions {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(channel, "channel");
        attributes = Map.copyOf(attributes);
        attributes.forEach((key, value) -> key.type().cast(value));
    }
}
