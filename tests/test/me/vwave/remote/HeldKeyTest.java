package me.vwave.remote;

import java.util.ArrayList;
import java.util.List;

/** Deterministic key-hold tests; no Android device, TV, or wall-clock sleeps needed. */
public final class HeldKeyTest {
    private static final class Clock implements HeldKey.Scheduler {
        long time, due;
        Runnable pending;
        final List<Long> events = new ArrayList<>();
        public long now() { return time; }
        public void post(Runnable task, long delay) {
            check(pending == null, "at most one queued repeat");
            pending = task;
            due = time + delay;
        }
        public void remove(Runnable task) { if (pending == task) pending = null; }
        void until(long end) {
            while (pending != null && due <= end) {
                time = due;
                Runnable task = pending;
                pending = null;
                task.run();
            }
            time = end;
        }
        int count(long start, long end) {
            return (int) events.stream().filter(t -> t >= start && t < end).count();
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Clock c = new Clock();
        HeldKey direction = new HeldKey(c, () -> c.events.add(c.time), true);
        direction.start();
        c.until(449);
        check(c.events.size() == 1 && c.events.get(0) == 0, "tap sends exactly one immediate key");
        direction.stop();
        c.until(1000);
        direction.run(); // Even an already-dispatched callback must do nothing after release.
        check(c.events.size() == 1, "release cancels repeats");

        direction.start(); // Fresh hold starts at t=1000.
        c.until(1450);
        check(c.events.size() == 3 && c.events.get(2) == 1450, "fresh hold waits 450 ms");
        c.until(6000);
        int slow = c.count(1000, 2000), fast = c.count(5000, 6000);
        check(slow <= 5 && fast >= 12 && fast <= 13, "slow precision, then bounded focus navigation");
        for (int i = 1; i < c.events.size(); i++) {
            check(c.events.get(i) - c.events.get(i - 1) >= 80, "never exceed the key rate cap");
        }
        direction.stop();
        int released = c.events.size();
        c.until(10000);
        check(c.events.size() == released, "no queued tail after release");
        direction.start();
        check(c.due == 10450, "repress resets acceleration");
        direction.stop();

        Clock volumeClock = new Clock();
        HeldKey volume = new HeldKey(volumeClock, () -> volumeClock.events.add(volumeClock.time), false);
        volume.start();
        volumeClock.until(5000);
        check(volumeClock.events.get(1) == 450, "ordinary keys retain initial delay");
        for (int i = 2; i < volumeClock.events.size(); i++) {
            check(volumeClock.events.get(i) - volumeClock.events.get(i - 1) == 130,
                    "volume/channel retain constant cadence");
        }
        volume.stop();

        Clock stalled = new Clock();
        HeldKey late = new HeldKey(stalled, () -> stalled.events.add(stalled.time), true);
        late.start();
        Runnable queued = stalled.pending;
        stalled.pending = null;
        stalled.time = 5000;
        queued.run();
        check(stalled.events.size() == 2 && stalled.due == 5080,
                "a stalled UI does not send a catch-up burst");
        late.stop();

        System.out.println("PASS: taps, cancellation, ramp, rate caps, restart, ordinary keys, stalled UI");
        System.out.println("Direction events/sec: slow=" + slow + ", capped=" + fast);
    }
}
