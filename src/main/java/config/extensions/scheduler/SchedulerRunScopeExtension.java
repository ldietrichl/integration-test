package config.extensions.scheduler;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opentest4j.TestAbortedException;
import steps.flow.scheduler.SchedulerFixtureStandSteps;
import steps.flow.scheduler.SchedulerPreflightSteps;

/** The selected JUnit root owns the pause, including IDE package and single-class runs. */
public final class SchedulerRunScopeExtension implements BeforeEachCallback, AfterEachCallback {
    private static final ExtensionContext.Namespace STORE =
            ExtensionContext.Namespace.create(SchedulerRunScopeExtension.class);
    private static final ThreadLocal<RunScope> CURRENT = new ThreadLocal<>();

    private static final class RunScope implements ExtensionContext.Store.CloseableResource {
        private boolean infrastructureReady;
        private String infrastructureFailure;
        private final java.util.Map<String, String> blocked = new java.util.TreeMap<>();

        synchronized void requireInfrastructureBaseline() {
            if (infrastructureReady) return;
            if (infrastructureFailure != null)
                throw new TestAbortedException("DEPENDENCY_BLOCKED: " + infrastructureFailure
                        + "; the first infrastructure failure is preserved in the original test result. "
                        + "No fixture preparation or business assertion was executed.");
            try {
                new SchedulerPreflightSteps().infrastructureBaseline();
                infrastructureReady = true;
                System.out.println("[scheduler-suite] Read-only mTLS/Fabric8 regression baseline confirmed once for this JUnit run");
            } catch (RuntimeException | Error failure) {
                infrastructureFailure = infrastructureFailureCode(failure);
                throw failure;
            }
        }

        @Override
        public void close() {
            // Runs after all selected tests, independent of a per-test framework Environment.
            Throwable restorationFailure = null;
            try { SchedulerFixtureStandSteps.restoreRun(this); }
            catch (RuntimeException | Error failure) { restorationFailure = failure; throw failure; }
            finally {
                if (!blocked.isEmpty()) {
                    AssertionError incomplete = new AssertionError("REGRESSION_INCOMPLETE: selected scenarios blocked: "
                            + blocked + ". Restoration was attempted; blocked scenarios are not passed.");
                    if (restorationFailure != null) restorationFailure.addSuppressed(incomplete);
                    else throw incomplete;
                }
            }
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        RunScope run = context.getRoot().getStore(STORE)
                .getOrComputeIfAbsent(RunScope.class, key -> new RunScope(), RunScope.class);
        infrastructure.scheduler.SchedulerAllureContext.bind(context.getRequiredTestClass().getName()
                + "." + context.getRequiredTestMethod().getName());
        CURRENT.set(run);
        SchedulerFixtureStandSteps.bindRun(run);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        try { SchedulerFixtureStandSteps.unbindRun(); }
        finally { CURRENT.remove(); infrastructure.scheduler.SchedulerAllureContext.clear(); }
    }

    public static void requireInfrastructureBaseline() {
        RunScope run = CURRENT.get();
        if (run == null)
            throw new IllegalStateException("Scheduler regression run scope is not bound");
        run.requireInfrastructureBaseline();
    }

    public static void recordBlocked(String scenario, String reason) {
        RunScope run = CURRENT.get();
        if (run == null) throw new IllegalStateException("Scheduler run scope is not bound");
        synchronized (run) { run.blocked.put(scenario, reason); }
        System.out.println("[scheduler-blocked] scenario=" + scenario + "; code=" + reason);
    }

    private static String infrastructureFailureCode(Throwable failure) {
        for (int depth = 0; failure != null && depth < 12; depth++, failure = failure.getCause()) {
            String message = failure.getMessage();
            if (message == null) continue;
            for (String code : java.util.List.of(
                    "INGRESS_UPSTREAM_UNAVAILABLE", "NO_READY_BACKEND", "MTLS_INGRESS_STABILIZATION_INTERRUPTED",
                    "REGRESSION_PREFLIGHT_NO_READY_WORKLOAD", "REGRESSION_PREFLIGHT_CONFIGMAP_KEY_MISSING",
                    "WORKLOAD_HPA_API_UNAVAILABLE", "WORKLOAD_READ_AUTH_REQUIRED", "KUBECONFIG_AUTH",
                    "OC_AUTH_REQUIRED", "AUTH_REQUIRED"))
                if (message.contains(code)) return code;
        }
        return "SCHEDULER_INFRASTRUCTURE_PREFLIGHT_FAILED";
    }
}
