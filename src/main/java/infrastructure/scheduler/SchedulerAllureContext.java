package infrastructure.scheduler;

/** Stable JUnit scenario identity; intentionally not an Allure current-step/fixture UUID. */
public final class SchedulerAllureContext {
    private SchedulerAllureContext() { }
    public static void bind(String fullName) {
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("Scenario fullName required");
        infrastructure.kubernetes.WorkloadReportContext.bind(fullName);
    }
    public static String currentFullName() { return infrastructure.kubernetes.WorkloadReportContext.currentFullName(); }
    public static void clear() { infrastructure.kubernetes.WorkloadReportContext.clear(); }
}
