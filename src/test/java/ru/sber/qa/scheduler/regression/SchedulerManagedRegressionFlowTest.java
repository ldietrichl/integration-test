package ru.sber.qa.scheduler.regression;

import config.environment.special.EnvironmentConfigWithScheduler;
import config.extensions.scheduler.SchedulerExecutionCondition;
import config.extensions.scheduler.UsesScenarioSteps;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.Regression;
import ru.sber.qa.scheduler.AbstractSchedulerFlowTest;

@ExtendWith({PerfeccionistaExtension.class, SchedulerExecutionCondition.class})
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithScheduler.Managed.class)
@ResourceLock("scheduler-service-regression")
@Epic("Scheduler service")
@Feature("Real-stand autonomous regression")
@Regression
@UsesScenarioSteps(steps.flow.scheduler.SchedulerAutonomousRegressionSteps.class)
public class SchedulerManagedRegressionFlowTest extends AbstractSchedulerFlowTest {
    @Test
    @DisplayName("SCH-014. MY_TASKS without application headers: confirmed deny-or-empty contract")
    // Temporary prerequisite block. Service-defect scenarios remain enabled.
    @org.junit.jupiter.api.Disabled("BLOCKED_TEST_DATA SCH-014: MY_TASKS without application headers has no agreed principal/visibility contract. Re-enable after confirming deny-or-empty or implementing the documented alternative; mTLS remains present.")
    void sch014() {
        getFlowWithRest().step("Check the confirmed header-free MY_TASKS contract; no anonymous author inference",
                flow -> flow.restCustomSteps().schedulerAutonomousRegressionSteps().sch014()).run();
    }

    @Test
    @Story("D10")
    @DisplayName("SCH-LIVE-015. NEAREST_PLANNED: real-clock filtering and order")
    @Description("Partial witness for D10. Does not replace SCH-015 fixed-clock boundary coverage.")
    void live015() {
        getFlowWithRest().step("Check future PLANNED tasks and their order on the real stand",
                flow -> flow.restCustomSteps().schedulerAutonomousRegressionSteps().live015()).run();
    }

    @Test
    @Story("D11")
    @DisplayName("SCH-LIVE-017. LAST_ERRORS: real-clock filtering and order")
    @Description("Partial witness for D11. Does not replace SCH-017 fixed-clock boundary coverage.")
    void live017() {
        getFlowWithRest().step("Check past ERROR tasks and their order on the real stand",
                flow -> flow.restCustomSteps().schedulerAutonomousRegressionSteps().live017()).run();
    }
}
