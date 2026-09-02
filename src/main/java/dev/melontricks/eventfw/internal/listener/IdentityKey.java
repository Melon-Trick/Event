package dev.melontricks.eventfw.internal.listener;

import java.util.Objects;

public final class IdentityKey {
    private final Object value;
    private final int hashCode;

    public IdentityKey(Object value) {
        this.value = Objects.requireNonNull(value, "value");
        hashCode = System.identityHashCode(value);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof IdentityKey key && value == key.value;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
