package dev.vriege.eventfw.internal.diagnostic;

import dev.vriege.eventfw.diagnostic.DispatchObserver;
import dev.vriege.eventfw.dispatch.DispatchResult;
import dev.vriege.eventfw.dispatch.EventContext;
import java.util.List;
import java.util.function.Consumer;

public final class DispatchObservers {
    private static final System.Logger LOGGER = System.getLogger("dev.vriege.eventfw.observers");
    private final List<DispatchObserver> observers;

    public DispatchObservers(List<DispatchObserver> observers) {
        this.observers = List.copyOf(observers);
    }

    public void completed(EventContext<?> context, DispatchResult<?> result) {
        if (!observers.isEmpty()) {
            notifyObservers(observer -> observer.completed(context, result));
        }
    }

    public void failed(EventContext<?> context, Throwable cause) {
        if (!observers.isEmpty()) {
            notifyObservers(observer -> observer.failed(context, cause));
        }
    }

    private void notifyObservers(Consumer<DispatchObserver> callback) {
        for (DispatchObserver observer : observers) {
            try {
                callback.accept(observer);
            } catch (RuntimeException failure) {
                LOGGER.log(System.Logger.Level.WARNING, "Dispatch observer failed", failure);
            }
        }
    }
}
