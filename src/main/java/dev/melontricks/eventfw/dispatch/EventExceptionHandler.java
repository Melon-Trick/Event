package dev.melontricks.eventfw.dispatch;

@FunctionalInterface
public interface EventExceptionHandler {
    void handle(ListenerFailure failure);

    static EventExceptionHandler ignoring() {
        return failure -> {};
    }

    static EventExceptionHandler logging(System.Logger logger) {
        return failure -> logger.log(
                System.Logger.Level.ERROR,
                "Event listener {0} failed during {1}",
                failure.subscription().id(),
                failure.stage(),
                failure.cause());
    }
}
