package infrastructure.kubernetes;

/** Scenario attribution shared by service-specific adapters. */
public final class WorkloadReportContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    private WorkloadReportContext() { }
    public static void bind(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Scenario fullName required");
        CURRENT.set(name);
    }
    public static String currentFullName() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
