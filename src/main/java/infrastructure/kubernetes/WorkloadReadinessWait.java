package infrastructure.kubernetes;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Requires consecutive samples, with a monotonic deadline and interrupt preservation. */
public final class WorkloadReadinessWait {
    private WorkloadReadinessWait() { }

    public static void await(BooleanSupplier sample, int timeoutSeconds, int pollMillis) {
        if (timeoutSeconds < 1 || timeoutSeconds > 600 || pollMillis < 10 || pollMillis > 10000)
            throw new IllegalArgumentException("WORKLOAD_READINESS_SETTINGS");
        long started = System.nanoTime();
        long deadline = started + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        int consecutive = 0;
        int attempts = 0;
        while (System.nanoTime() < deadline) {
            if (Thread.currentThread().isInterrupted()) throw failure("WORKLOAD_READINESS_INTERRUPTED");
            consecutive = sample.getAsBoolean() ? consecutive + 1 : 0;
            System.out.println("[workload-readiness] attempt=" + (++attempts) + "; consecutive=" + consecutive
                    + "; elapsedMillis=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            if (consecutive >= 2 && System.nanoTime() < deadline) return;
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            try { TimeUnit.NANOSECONDS.sleep(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(pollMillis))); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw failure("WORKLOAD_READINESS_INTERRUPTED");
            }
        }
        throw failure("WORKLOAD_READINESS_TIMEOUT");
    }

    static KubernetesDiagnosticException failure(String code) {
        return new KubernetesDiagnosticException(code, "WORKLOAD_AVAILABILITY", null);
    }
}
