package dev.melontricks.eventfw.annotation;

import java.io.Serial;

/**
 * Signals that an annotated listener method has an invalid signature or cannot be accessed.
 *
 * <p>Annotated registration validates the complete listener class before installing any subscriptions, so this
 * exception cannot leave a partially registered listener.
 */
public final class InvalidListenerException extends IllegalArgumentException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception describing a validation failure.
     *
     * @param message non-null diagnostic message
     */
    public InvalidListenerException(String message) {
        super(message);
    }

    /**
     * Creates an exception describing an access or reflective validation failure.
     *
     * @param message non-null diagnostic message
     * @param cause underlying failure
     */
    public InvalidListenerException(String message, Throwable cause) {
        super(message, cause);
    }
}
