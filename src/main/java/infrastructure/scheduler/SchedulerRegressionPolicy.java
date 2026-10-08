package infrastructure.scheduler;

import java.util.Set;

/** Pure selection policy. The default can read a stand but cannot pause jobs or replace pods. */
public final class SchedulerRegressionPolicy {
    public enum Phase { READ_ONLY, FIXTURES, JOBS, FULL }
    private static final Set<String> JOBS = Set.of("unsupportedCj", "unsupportedSplit",
            "unsupportedPilot", "unsupportedAction", "cleanupHung", "cleanupOutdated");
    private SchedulerRegressionPolicy() { }

    public static Phase phase(String value) {
        return switch (value) {
            case "read-only" -> Phase.READ_ONLY;
            case "fixtures" -> Phase.FIXTURES;
            case "jobs" -> Phase.JOBS;
            case "full" -> Phase.FULL;
            default -> throw new IllegalArgumentException("SCHEDULER_REGRESSION_PHASE must be read-only, fixtures, jobs or full");
        };
    }
    public static String setting(String property, String environment, String fallback) {
        String value = System.getProperty(property);
        if (value == null) value = System.getenv(environment);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
    public static Phase selected() {
        return phase(setting("scheduler.regression.phase", "SCHEDULER_REGRESSION_PHASE", "read-only"));
    }
    public static boolean mutationWindowApproved() {
        String value = setting("scheduler.mutation.window.approved", "SCHEDULER_MUTATION_WINDOW_APPROVED", "false");
        if (!Set.of("true", "false").contains(value))
            throw new IllegalArgumentException("SCHEDULER_MUTATION_WINDOW_APPROVED must be true or false");
        return Boolean.parseBoolean(value);
    }
    public static Phase group(String type, String method) {
        if (type.equals("SchedulerReadOnlyRegressionFlowTest")
                || type.equals("SchedulerPreflightFlowTest")
                || type.equals("SchedulerApiRegressionFlowTest") && method.equals("sch135"))
            return Phase.READ_ONLY;
        if (type.equals("SchedulerWorkloadRegressionFlowTest")
                || type.equals("SchedulerRealStandRegressionFlowTest") && JOBS.contains(method))
            return Phase.JOBS;
        if (Set.of("SchedulerApiRegressionFlowTest", "SchedulerManagedRegressionFlowTest",
                "SchedulerRealStandRegressionFlowTest").contains(type)) return Phase.FIXTURES;
        throw new IllegalArgumentException("UNCLASSIFIED_SCHEDULER_SCENARIO: " + type + "." + method);
    }
    /** Null permits preparation. An exclusion is not coverage and not a service failure. */
    public static String refusal(Phase selected, Phase group, boolean windowApproved) {
        if (selected != Phase.FULL && selected != group) return "PHASE_NOT_SELECTED";
        if (group != Phase.READ_ONLY && !windowApproved) return "MUTATION_WINDOW_REQUIRED";
        return null;
    }
    public static boolean identityRequired(String type, String method) {
        return type.equals("SchedulerApiRegressionFlowTest") && method.equals("sch013");
    }
    public static boolean anonymousContractRequired(String type, String method) {
        return type.equals("SchedulerManagedRegressionFlowTest") && method.equals("sch014");
    }
    public static String anonymousContract() {
        String value = setting("scheduler.mytasks.header-free.contract",
                "SCHEDULER_MYTASKS_HEADER_FREE_CONTRACT", "unconfirmed");
        if (!Set.of("unconfirmed", "deny-or-empty").contains(value))
            throw new IllegalArgumentException("Header-free MY_TASKS contract must be unconfirmed or deny-or-empty");
        return value;
    }
}
