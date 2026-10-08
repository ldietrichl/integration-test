package util.concurrent;

import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reusable scenario operations; test cases remain in their ticket package. */
public final class ScenarioConcurrency {
    private ScenarioConcurrency() { }
    public static void waitForStart(CountDownLatch start) {
        try { assertTrue(start.await(10, TimeUnit.SECONDS), "Parallel start timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    public static void await(List<Future<?>> futures) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        for (Future<?> future : futures) {
            try { future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
            catch (ExecutionException | TimeoutException e) { throw new AssertionError("Concurrent scenario failed", e); }
        }
    }
}
