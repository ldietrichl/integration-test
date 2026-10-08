package ru.sber.qa.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;
import config.extensions.scheduler.SchedulerRunScopeExtension;
import steps.flow.scheduler.SchedulerRegressionPreconditions;
import steps.flow.scheduler.SchedulerIdentitySteps;
import org.junit.jupiter.api.TestInfo;
import steps.flow.scheduler.SchedulerScenarioSupport;
import steps.flow.scheduler.SchedulerFixtureStandSteps;

/** Framework lifecycle only; fixture and infrastructure behavior stays in reusable flow/DB steps. */
@ExtendWith(SchedulerRunScopeExtension.class)
@ResourceLock("scheduler-service-regression")
public abstract class AbstractSchedulerFlowTest extends SchedulerScenarioSupport {
    @BeforeEach
    protected void start(TestInfo info) {
        Class<?> type = info.getTestClass().orElseThrow();
        String method = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse("");
        // Excluded/blocked capabilities must not trigger the infrastructure gate or stand preparation.
        SchedulerRegressionPreconditions.require(type, method);
        SchedulerRunScopeExtension.requireInfrastructureBaseline();
        if (SchedulerRegressionPreconditions.requiresIdentity(type, method))
            new SchedulerIdentitySteps().prepareFixtureUsers();
        if ((type.getSimpleName().equals("SchedulerApiRegressionFlowTest") && !method.equals("sch135"))
                || type.getSimpleName().equals("SchedulerWorkloadRegressionFlowTest")
                || type.getSimpleName().equals("SchedulerManagedRegressionFlowTest")
                || type.getSimpleName().equals("SchedulerRealStandRegressionFlowTest"))
            new SchedulerFixtureStandSteps().prepare(type);
        startSchedulerScenario(info);
    }
    @AfterEach
    protected void finish() {
        try { cleanupSchedulerFixtures(); }
        finally { SchedulerFixtureStandSteps.finishScenario(); }
        // Only a DATA cleanup failure marks the pause unsafe to restore.
        // Log attachment/tunnel close failures must remain visible but must not strand shared jobs.
    }
}
