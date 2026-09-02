package dev.melontricks.eventfw.listener;

import java.util.List;

public interface Registration extends AutoCloseable {
    boolean active();

    List<Subscription> subscriptions();

    @Override
    void close();
}
