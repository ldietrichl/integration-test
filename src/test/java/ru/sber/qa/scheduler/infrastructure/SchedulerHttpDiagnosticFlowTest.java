package ru.sber.qa.scheduler.infrastructure;

import config.environment.special.EnvironmentConfigWithSchedulerInfrastructure;
import config.extensions.scheduler.SchedulerInfrastructureExtension;
import config.extensions.scheduler.UsesScenarioSteps;
import flow.SchedulerInfrastructureFlow;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.flow.FlowRunner;
import steps.flow.scheduler.SchedulerHttpDiagnosticSteps;

import java.util.concurrent.TimeUnit;

/** No disabling condition: missing transport prerequisites must fail, not silently skip these diagnostics. */
@ExtendWith({PerfeccionistaExtension.class, SchedulerInfrastructureExtension.class})
@SetEnvironmentConfiguration(EnvironmentConfigWithSchedulerInfrastructure.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("scheduler-service-regression")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 5, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SAME_THREAD)
@Epic("Scheduler service")
@Feature("Read-only HTTP 401 diagnostics")
@UsesScenarioSteps(steps.flow.scheduler.SchedulerHttpDiagnosticSteps.class)
public class SchedulerHttpDiagnosticFlowTest {
    @Test @Order(1)
    @DisplayName("SCH-HTTP-001. Valid registry query through ingress and Fabric8")
    void http001() {
        FlowRunner.flowRunnerFor(SchedulerInfrastructureFlow.class)
                .step("Compare two independent valid registry queries",
                        flow -> new SchedulerHttpDiagnosticSteps(flow).http001()).run();
    }

    @Test @Order(2)
    @DisplayName("SCH-HTTP-002. Missing page: strict 400 with before/after controls on both routes")
    void http002() {
        FlowRunner.flowRunnerFor(SchedulerInfrastructureFlow.class)
                .step("Compare missing-page validation without accepting 401",
                        flow -> new SchedulerHttpDiagnosticSteps(flow).http002()).run();
    }

    @Test @Order(3)
    @DisplayName("SCH-HTTP-003. Non-numeric page: strict 400 with before/after controls on both routes")
    void http003() {
        FlowRunner.flowRunnerFor(SchedulerInfrastructureFlow.class)
                .step("Compare invalid-page validation without accepting 401",
                        flow -> new SchedulerHttpDiagnosticSteps(flow).http003()).run();
    }
}
