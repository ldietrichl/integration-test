package infrastructure.scheduler;

/** Keep the original scenario failure; cleanup failure remains a separate suppressed cause. */
public final class SchedulerCleanupFailures {
    private SchedulerCleanupFailures() { }
    public static void run(Throwable primary, Runnable cleanup) {
        try { cleanup.run(); }
        catch (RuntimeException | Error failure) {
            if (primary == null) throw failure;
            if (primary != failure) primary.addSuppressed(failure);
        }
    }
}
