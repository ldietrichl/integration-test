package config.services.core;

/** Confirmed effective stand settings, never inferred from an archived application default. */
public final class SchedulerJobSettings {
    private static final long DAY = 86_400_000L;
    private SchedulerJobSettings() { }

    public static void requireEnabled() {
        StandSettings stand = new StandSettings();
        if (!stand.flag("workloads.scheduler.regression.jobs.enabled"))
            throw new IllegalStateException("REAL_JOB_APPROVAL_REQUIRED: configure stand." + stand.environment
                    + ".workloads.scheduler.regression.jobs.enabled only after dedicated-stand approval");
        stand.integer("workloads.scheduler.regression.timeout.seconds", 240, 30, 600);
    }

    public static long cleanupAgeMillis(boolean hung) {
        StandSettings stand = new StandSettings();
        String key = "workloads.scheduler.regression." + (hung ? "execution-timeout.millis" : "overdue.minutes");
        String raw = stand.required(key);
        final long configured;
        try { configured = Long.parseLong(raw); }
        catch (NumberFormatException invalid) {
            throw new IllegalStateException("Expected a confirmed positive integer for stand." + stand.environment + "." + key);
        }
        if (configured <= 0 || configured > (hung ? 30 * DAY : 43_200L))
            throw new IllegalStateException("Out-of-range confirmed cleanup threshold: stand." + stand.environment + "." + key);
        return Math.addExact(hung ? configured : Math.multiplyExact(configured, 60_000L), DAY);
    }
}
