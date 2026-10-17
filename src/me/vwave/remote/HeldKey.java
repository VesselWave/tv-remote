package me.vwave.remote;

/** Repeats a held key, with a bounded direction-key ramp inspired by the physical remote. */
final class HeldKey implements Runnable {
    /** Repeats stop after this even if the release never arrives, so a lost touch can't max out the volume. */
    static final long MAX_HOLD_MS = 10_000;

    interface Scheduler {
        long now();
        void post(Runnable task, long delayMs);
        void remove(Runnable task);
    }

    private final Scheduler scheduler;
    private final Runnable action;
    private final boolean accelerate;
    private boolean running;
    private long started;

    HeldKey(Scheduler scheduler, Runnable action, boolean accelerate) {
        this.scheduler = scheduler;
        this.action = action;
        this.accelerate = accelerate;
    }

    void start() {
        stop();
        started = scheduler.now();
        running = true;
        action.run();
        if (running) scheduler.post(this, 450);
    }

    void stop() {
        running = false;
        scheduler.remove(this);
    }

    @Override public void run() {
        if (!running) return;
        if (scheduler.now() - started >= MAX_HOLD_MS) {
            running = false;
            return;
        }
        action.run();
        if (running) scheduler.post(this, interval(scheduler.now() - started));
    }

    private static double ramp(long heldMs) {
        return Math.max(0, Math.min(1, (heldMs - 1800) / 1200.0));
    }

    private long interval(long heldMs) {
        if (!accelerate) return 130;
        // Focus navigation must stay below the TV's individual-key processing limit.
        return Math.max(80, Math.round(250 * Math.pow(80.0 / 250, ramp(heldMs))));
    }
}
