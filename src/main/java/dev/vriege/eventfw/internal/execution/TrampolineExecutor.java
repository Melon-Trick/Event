package dev.vriege.eventfw.internal.execution;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;

public final class TrampolineExecutor implements Executor {
    private static final ThreadLocal<ArrayDeque<Runnable>> PENDING = new ThreadLocal<>();
    private final Executor delegate;

    public TrampolineExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(() -> executeInline(command));
    }

    public static void executeInline(Runnable command) {
        ArrayDeque<Runnable> pending = PENDING.get();
        if (pending != null) {
            pending.addLast(command);
            return;
        }
        pending = new ArrayDeque<>();
        PENDING.set(pending);
        pending.addLast(command);
        try {
            Runnable next;
            while ((next = pending.pollFirst()) != null) {
                next.run();
            }
        } finally {
            PENDING.remove();
        }
    }
}
