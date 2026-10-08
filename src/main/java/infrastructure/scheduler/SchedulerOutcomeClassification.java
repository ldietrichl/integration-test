package infrastructure.scheduler;

/** A reporting classification, not a change to test status or an assertion bypass. */
public final class SchedulerOutcomeClassification {
    private SchedulerOutcomeClassification() { }
    public static String classify(String status, String message) {
        String text = message == null ? "" : message;
        if ("passed".equals(status)) return "PASSED";
        if (text.contains("PHASE_NOT_SELECTED")) return "NOT_SELECTED";
        if (text.matches("(?s).*(FOREIGN_RUNNABLE_TASKS|DEPENDENCY_BLOCKED|MUTATION_WINDOW_REQUIRED|"
                + "KUBECONFIG_USER_AUTH_REJECTED|KUBECONFIG_AUTH|WORKLOAD_READ_AUTH_REQUIRED|"
                + "INGRESS_UPSTREAM_UNAVAILABLE|NO_READY_BACKEND|WORKLOAD_READINESS_|JAVA_TRUST_ANCHORS_EMPTY|"
                + "PKI_CHAIN_UNTRUSTED|JAVA_PKIX_PATH_BUILDING_FAILED).*")) return "BLOCKED_ENVIRONMENT";
        if ("skipped".equals(status)) return "SKIPPED";
        if ("failed".equals(status)) return "ASSERTION_FAILURE";
        return "EXECUTION_ERROR";
    }
}
