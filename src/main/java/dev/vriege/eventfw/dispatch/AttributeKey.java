package dev.vriege.eventfw.dispatch;

import java.util.Objects;

/**
 * Names a runtime-checked dispatch attribute. Equal names with different types identify different attributes.
 *
 * @param name non-blank diagnostic name
 * @param type non-null reference type of stored values
 * @param <T> attribute value type
 */
public record AttributeKey<T>(String name, Class<T> type) {
    /**
     * Validates the key.
     *
     * @throws IllegalArgumentException if the name is blank or the type is primitive
     * @throws NullPointerException if either component is null
     */
    public AttributeKey {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (name.isBlank() || type.isPrimitive()) {
            throw new IllegalArgumentException("attribute requires a non-blank name and reference type");
        }
    }
}
