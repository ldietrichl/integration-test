package ru.sber.qa.scheduler.regression;

import config.environment.special.EnvironmentConfigWithSchedulerInfrastructure;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.*;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import config.extensions.scheduler.*;

@ExtendWith({PerfeccionistaExtension.class, SchedulerExecutionCondition.class, SchedulerInfrastructureExtension.class})
@SetEnvironmentConfiguration(EnvironmentConfigWithSchedulerInfrastructure.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("scheduler-service-regression")
@Timeout(value = 40, unit = java.util.concurrent.TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SAME_THREAD)
@Epic("Scheduler service")
@Feature("Workload recovery regression")
@ru.sber.qa.allure.Regression
@UsesScenarioSteps(steps.flow.scheduler.SchedulerWorkloadRegressionSteps.class)
public class SchedulerWorkloadRegressionFlowTest extends ru.sber.qa.scheduler.AbstractSchedulerFlowTest {
    @Test
    @DisplayName("SCH-POD-001. Сохранение данных БД и доступность scheduler после удаления и автоматического пересоздания pod")
    void pod001() {
        getFlowWithRest()
                .step("SCH-POD-001. Сохранение данных БД и доступность scheduler после удаления и автоматического пересоздания pod", flow ->
                        flow.restCustomSteps().schedulerWorkloadRegressionSteps().pod001()).run();
    }
}
