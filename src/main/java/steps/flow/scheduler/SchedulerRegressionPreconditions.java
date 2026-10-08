package steps.flow.scheduler;

import config.services.core.SchedulerApplicationIdentity;
import config.services.core.SchedulerJobSettings;
import config.extensions.scheduler.SchedulerRunScopeExtension;
import infrastructure.scheduler.SchedulerRegressionPolicy;
import org.opentest4j.TestAbortedException;
import java.util.Set;

/** Capability gates run BEFORE infrastructure baseline, identity lookup, fixtures or stand mutations. */
public final class SchedulerRegressionPreconditions {
    private SchedulerRegressionPreconditions() { }
    public static boolean requiresIdentity(Class<?> type, String method) {
        return SchedulerRegressionPolicy.identityRequired(type.getSimpleName(), method);
    }
    private static void blocked(String scenario, String reason) {
        SchedulerRunScopeExtension.recordBlocked(scenario, reason);
        throw new TestAbortedException("DEPENDENCY_BLOCKED: " + reason + "; no business assertion or fixture mutation executed");
    }
    public static void require(Class<?> type, String method) {
        String name = type.getSimpleName();
        String scenario = name + "." + method;
        var phase = SchedulerRegressionPolicy.selected();
        var group = SchedulerRegressionPolicy.group(name, method);
        String refusal = SchedulerRegressionPolicy.refusal(phase, group,
                SchedulerRegressionPolicy.mutationWindowApproved());
        if ("PHASE_NOT_SELECTED".equals(refusal))
            throw new TestAbortedException("PHASE_NOT_SELECTED: selected=" + phase + "; required=" + group
                    + "; scenario remains in the regression plan; coverage is not claimed");
        if (refusal != null) blocked(scenario, refusal);
        if (requiresIdentity(type, method)) {
            if (SchedulerApplicationIdentity.observesDevAuthor())
                blocked(scenario, "MY_TASKS_APPLICATION_PRINCIPAL_UNCONFIRMED");
            try { SchedulerApplicationIdentity.requireToken(); }
            catch (IllegalStateException missing) {
                // No token, raw claim or exception payload is copied into the report.
                blocked(scenario, "MY_TASKS_APPLICATION_TOKEN_REQUIRED_OR_INVALID");
            }
        }
        if (SchedulerRegressionPolicy.anonymousContractRequired(name, method)
                && !"deny-or-empty".equals(SchedulerRegressionPolicy.anonymousContract()))
            blocked(scenario, "MY_TASKS_HEADER_FREE_CONTRACT_UNCONFIRMED");
        if (name.equals("SchedulerApiRegressionFlowTest") && method.equals("sch018")
                && !new config.services.core.StandSettings().flag("workloads.scheduler.regression.search.object-name.confirmed"))
            blocked(scenario, "SEARCH_CONTRACT_UNCONFIRMED");
        if (name.equals("SchedulerRealStandRegressionFlowTest")
                && Set.of("unsupportedCj", "unsupportedSplit", "unsupportedPilot", "unsupportedAction",
                "cleanupHung", "cleanupOutdated").contains(method)) SchedulerJobSettings.requireEnabled();
    }
}
