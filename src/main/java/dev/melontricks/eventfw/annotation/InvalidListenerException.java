package dev.melontricks.eventfw.annotation;

public final class InvalidListenerException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public InvalidListenerException(String message) {
        super(message);
    }

    public InvalidListenerException(String message, Throwable cause) {
        super(message, cause);
    }
}
