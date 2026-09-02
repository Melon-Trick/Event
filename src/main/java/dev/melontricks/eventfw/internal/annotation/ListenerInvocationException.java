package dev.melontricks.eventfw.internal.annotation;

import java.io.Serial;
import java.lang.reflect.Method;

final class ListenerInvocationException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    ListenerInvocationException(Method method, Throwable cause) {
        super("Annotated event handler failed: " + method, cause);
    }
}
