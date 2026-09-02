package dev.melontricks.eventfw.internal.listener;

import dev.melontricks.eventfw.listener.Registration;
import dev.melontricks.eventfw.listener.Subscription;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CompositeRegistration implements Registration {
    private final List<Subscription> subscriptions;
    private final AtomicBoolean closed = new AtomicBoolean();

    public CompositeRegistration(List<Subscription> subscriptions) {
        this.subscriptions = List.copyOf(subscriptions);
    }

    @Override
    public boolean active() {
        return !closed.get() && subscriptions.stream().anyMatch(Subscription::active);
    }

    @Override
    public List<Subscription> subscriptions() {
        return subscriptions;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (int index = subscriptions.size() - 1; index >= 0; index--) {
            subscriptions.get(index).close();
        }
    }
}
