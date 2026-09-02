package dev.melontricks.eventfw.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AbstractCancellableEventTest {
    @Test
    void cancellationIsMonotonicAndKeepsTheFirstReason() {
        TestEvent event = new TestEvent();

        assertTrue(event.cancel("first"));
        assertFalse(event.cancel("second"));
        assertFalse(event.cancel());

        assertTrue(event.cancelled());
        assertEquals("first", event.cancellationReason().orElseThrow());
    }

    @Test
    void validatesCancellationReasons() {
        TestEvent event = new TestEvent();

        assertThrows(NullPointerException.class, () -> event.cancel(null));
        assertThrows(IllegalArgumentException.class, () -> event.cancel("  "));
        assertFalse(event.cancelled());
    }

    private static final class TestEvent extends AbstractCancellableEvent {}
}
