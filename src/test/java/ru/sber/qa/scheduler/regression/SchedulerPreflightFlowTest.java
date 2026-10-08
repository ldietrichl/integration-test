package ru.sber.qa.scheduler.regression;

import config.environment.special.EnvironmentConfigWithScheduler;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.Regression;
import ru.sber.qa.scheduler.AbstractSchedulerFlowTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithScheduler.class)
@ResourceLock("scheduler-service-regression")
@Epic("Scheduler service")
@Feature("Scheduler environment readiness")
@config.extensions.scheduler.UsesScenarioSteps(steps.flow.scheduler.SchedulerPreflightSteps.class)
public class SchedulerPreflightFlowTest extends AbstractSchedulerFlowTest {
    @Test
    @DisplayName("Scheduler: read-only mTLS, Fabric8 and database compatibility")
    void databaseBaseline() {
        getFlowWithRest().step("Scheduler: read-only dev/ift database compatibility",
                flow -> flow.restCustomSteps().schedulerPreflightSteps().databaseBaseline()).run();
    }

}
