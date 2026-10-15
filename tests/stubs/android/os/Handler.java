package android.os;
import java.util.concurrent.*;
/** JVM stand-in: one shared "main" thread. */
public class Handler {
    public static final ScheduledExecutorService MAIN = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "main"); t.setDaemon(true); return t; });
    public Handler(Looper l) {}
    public boolean post(Runnable r) { MAIN.execute(r); return true; }
    public boolean postDelayed(Runnable r, long ms) { MAIN.schedule(r, ms, TimeUnit.MILLISECONDS); return true; }
    public void removeCallbacks(Runnable r) {}
    public void removeCallbacksAndMessages(Object o) {}
}
