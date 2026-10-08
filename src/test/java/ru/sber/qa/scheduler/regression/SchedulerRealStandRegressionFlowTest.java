package ru.sber.qa.scheduler.regression;

import config.environment.special.EnvironmentConfigWithScheduler;
import config.extensions.scheduler.SchedulerExecutionCondition;
import config.extensions.scheduler.UsesScenarioSteps;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.Regression;
import ru.sber.qa.scheduler.AbstractSchedulerFlowTest;
import java.util.concurrent.TimeUnit;

@ExtendWith({PerfeccionistaExtension.class, SchedulerExecutionCondition.class})
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithScheduler.Managed.class)
@ResourceLock("scheduler-service-regression")
@Timeout(value = 40, unit = TimeUnit.MINUTES)
@Epic("Scheduler service")
@Feature("Real stand: owned data and scheduler-only job control")
@Regression
@UsesScenarioSteps(steps.flow.scheduler.SchedulerRealStandRegressionSteps.class)
public class SchedulerRealStandRegressionFlowTest extends AbstractSchedulerFlowTest {
    @Test
    @Story("D51")
    @DisplayName("SCH-LIVE-D51. Registry excludes non-null versions other than 1")
    void registryVersionIsolation() {
        getFlowWithRest().step("Registry excludes non-null versions other than 1",
                flow -> flow.restCustomSteps().schedulerRealStandRegressionSteps().registryVersionIsolation()).run();
    }

    @Test
    @Story("D31")
    @DisplayName("SCH-LIVE-D31. Deletion removes task and actions; completed control survives")
    void deleteCascade() {
        getFlowWithRest().step("Deletion removes task and actions; completed control survives",
                flow -> flow.restCustomSteps().schedulerRealStandRegressionSteps().deleteCascade()).run();
    }

    @Test
    @Story("D20")
    @DisplayName("SCH-LIVE-D20-NULL. Explicit nulls fail validation without database changes")
    void explicitNullValidation() {
        getFlowWithRest().step("Explicit nulls fail validation without database changes",
                flow -> flow.restCustomSteps().schedulerRealStandRegressionSteps().explicitNullValidation()).run();
    }

    @Test
    @Story("D20")
    @DisplayName("SCH-LIVE-D20-MULTI. Invalid second action does not persist the first")
    void invalidSecondAction() {
        getFlowWithRest().step("Invalid second action does not persist the first",
                flow -> flow.restCustomSteps().schedulerRealStandRegressionSteps().invalidSecondAction()).run();
    }

    @Test
    @Story("D38")
    @DisplayName("SCH-LIVE-D38-CJ. Real job rejects CJ without enabling legacy or cleanup jobs")
    void unsupportedCj() {
        getFlowWithRest().step("Real job rejects CJ without enabling legacy or cleanup jobs",
                flow -> flow.restCustomSteps().schedulerRealStandRegressionSteps().unsupportedCj()).run();
    }

    @Test
    @Story("D38")
    @DisplayName("SCH-LIVE-D38-SPLIT. Real job rejects SPLIT")
    void unsupportedSplit() {
        getFlowWithRest().step("Real job rejects SPLIT",
                flow -> flow.restCustomSteps().schedulerRealStandRegressionSteps().unsupportedSplit()).run();
    }

    @Test
    @Story("D38")
    @DisplayName("SCH-LIVE-D38-PILOT. Real job rejects PILOT")
    void unsupportedPilot() {
        getFlowWithRest().step("Real job rejects PILOT",
                flow -> flow.restCustomSteps().schedulerRealStandRegressionSteps().unsupportedPilot()).run();
    }

    @Test
    @Story("D38")
    @DisplayName("SCH-LIVE-D38-ACTION. Real V2 job rejects legacy REPEAT_EXP action")
    void unsupportedAction() {
        getFlowWithRest().step("Real V2 job rejects legacy REPEAT_EXP action",
                flow -> flow.restCustomSteps().schedulerRealStandRegressionSteps().unsupportedAction()).run();
    }

    @Test
    @Story("D44")
    @DisplayName("SCH-LIVE-D44. Hung cleanup applies ERROR and preserves non-target rows")
    void cleanupHung() {
        getFlowWithRest().step("Hung cleanup applies ERROR and preserves non-target rows",
                flow -> flow.restCustomSteps().schedulerRealStandRegressionSteps().cleanupHung()).run();
    }

    @Test
    @Story("D45")
    @DisplayName("SCH-LIVE-D45. Overdue cleanup applies NOT_STARTED with null end time")
    void cleanupOutdated() {
        getFlowWithRest().step("Overdue cleanup applies NOT_STARTED with null end time",
                flow -> flow.restCustomSteps().schedulerRealStandRegressionSteps().cleanupOutdated()).run();
    }

}
