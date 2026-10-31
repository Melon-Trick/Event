package dev.vriege.eventfw.internal.execution;

import java.util.concurrent.ScheduledThreadPoolExecutor;

public final class EventTimers {
    private static final ScheduledThreadPoolExecutor SCHEDULER = create();

    private EventTimers() {}

    public static ScheduledThreadPoolExecutor scheduler() {
        return SCHEDULER;
    }

    private static ScheduledThreadPoolExecutor create() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(
                1, task -> Thread.ofPlatform().daemon().name("eventfw-timer").unstarted(task));
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
