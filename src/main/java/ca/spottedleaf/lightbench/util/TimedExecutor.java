package ca.spottedleaf.lightbench.util;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

public final class TimedExecutor implements Executor {

    private final AtomicLong totalTime = new AtomicLong();
    private final Executor wrapped;

    public TimedExecutor(final Executor over) {
        this.wrapped = over;
    }

    @Override
    public void execute(final Runnable command) {
        final Runnable wrap = () -> {
            final long start = System.nanoTime();
            command.run();
            final long end = System.nanoTime();
            this.totalTime.getAndAdd(end - start);
        };

        this.wrapped.execute(wrap);
    }

    public final long getTotalTime() {
        return this.totalTime.getOpaque();
    }
}
