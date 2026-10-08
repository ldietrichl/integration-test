package ru.sber.qa.scheduler.regression;

import com.fasterxml.jackson.databind.JsonNode;
import config.environment.special.EnvironmentConfigWithScheduler;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.Regression;
import ru.sber.qa.scheduler.AbstractSchedulerFlowTest;
import ru.sber.qa.matchers.RestMatchers;
import steps.container.KubernetesTunnelSteps;
import steps.rest.scheduler.SchedulerRegistrySteps;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static steps.rest.scheduler.SchedulerSteps.expect;

/** Observational regression on shared DEV/IFT. No fixture writes or stand control. */
@ExtendWith({PerfeccionistaExtension.class, config.extensions.scheduler.SchedulerExecutionCondition.class})
@SetEnvironmentConfiguration(EnvironmentConfigWithScheduler.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("scheduler-service-regression")
@Epic("Scheduler service")
@Feature("Scheduler read-only regression on observed data")
@Regression
@config.extensions.scheduler.UsesScenarioSteps(steps.flow.scheduler.SchedulerReadOnlyRegressionSteps.class)
public class SchedulerReadOnlyRegressionFlowTest extends AbstractSchedulerFlowTest {
    @Test
    @DisplayName("SCH-RO-001. Health, database health and Prometheus through the project REST client")
    void ro001() {
        getFlowWithRest().step("SCH-RO-001. Health, database health and Prometheus through the project REST client",
                flow -> flow.restCustomSteps().schedulerReadOnlyRegressionSteps().ro001()).run();
    }

    @Test
    @DisplayName("SCH-RO-002. V2 dictionary metadata and bounded registry contract")
    void ro002() {
        getFlowWithRest().step("SCH-RO-002. V2 dictionary metadata and bounded registry contract",
                flow -> flow.restCustomSteps().schedulerReadOnlyRegressionSteps().ro002()).run();
    }

    @Test
    @DisplayName("SCH-RO-003. Advertised V2 equal filter is applied to observed rows")
    void ro003() {
        getFlowWithRest().step("SCH-RO-003. Advertised V2 equal filter is applied to observed rows",
                flow -> flow.restCustomSteps().schedulerReadOnlyRegressionSteps().ro003()).run();
    }

    @Test
    @DisplayName("SCH-RO-004. V2 ASC sorting on a non-constant numeric or date field")
    void ro004() {
        getFlowWithRest().step("SCH-RO-004. V2 ASC sorting on a non-constant numeric or date field",
                flow -> flow.restCustomSteps().schedulerReadOnlyRegressionSteps().ro004()).run();
    }

    @Test
    @DisplayName("SCH-RO-005. V2 DESC sorting on a non-constant numeric or date field")
    void ro005() {
        getFlowWithRest().step("SCH-RO-005. V2 DESC sorting on a non-constant numeric or date field",
                flow -> flow.restCustomSteps().schedulerReadOnlyRegressionSteps().ro005()).run();
    }

    @Test
    @DisplayName("SCH-RO-006. Registry dates follow the documented integer contract")
    void ro006() {
        getFlowWithRest().step("SCH-RO-006. Registry dates follow the documented integer contract",
                flow -> flow.restCustomSteps().schedulerReadOnlyRegressionSteps().ro006()).run();
    }

    @Test
    @DisplayName("SCH-RO-007. Registry taskNumber is zero; task ids correlate with DB")
    void ro007() {
        getFlowWithRest().step("SCH-RO-007. Registry taskNumber is zero; task ids correlate with DB",
                flow -> flow.restCustomSteps().schedulerReadOnlyRegressionSteps().ro007()).run();
    }

}
