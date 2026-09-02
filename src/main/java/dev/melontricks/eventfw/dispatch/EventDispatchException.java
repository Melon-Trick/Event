package dev.melontricks.eventfw.dispatch;

@SuppressWarnings("serial")
public final class EventDispatchException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final transient ListenerFailure failure;

    public EventDispatchException(ListenerFailure failure) {
        super(
                "Event subscription " + failure.subscription().id() + " failed during " + failure.stage(),
                failure.cause());
        this.failure = failure;
    }

    public ListenerFailure failure() {
        return failure;
    }
}
