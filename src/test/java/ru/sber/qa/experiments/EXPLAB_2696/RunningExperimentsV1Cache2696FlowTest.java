package ru.sber.qa.experiments.EXPLAB_2696;

import config.environment.EnvironmentConfigWithRest;
import dto.enums.ExperimentsStatusesV1;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.allure.Regression;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("explab-2696-running-cache")
public class RunningExperimentsV1Cache2696FlowTest extends AbstractRunningV1Cache2696FlowTest {

    @BeforeEach
    void requireV2CjToggleDisabled() {
        assumeV2CjExperimentsToggleDisabledStand();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2696-EXP-01. Running experiments v1 отдает IN_PROGRESS version=4 после ручного evict")
    void runningExperimentsShouldContainInProgressVersion4AfterManualEvict() {
        getFlowWithRest()
                .step("Создаем running-эксперимент version=4", flow -> {
                    Long experimentId = createExperiment(flow, ExperimentsStatusesV1.IN_PROGRESS);
                    flow.restCustomSteps().experimentsV1Steps().evictCashStatusOk();

                    ValidatableResponseWrapper response = flow.restCustomSteps().experimentsV1Steps()
                            .getExperimentsEnhanceRunningStatusOk();

                    assertArrayContainsId(response, experimentId);
                    assertExperimentHasVersionAndStatus(response, experimentId, 4, "IN_PROGRESS");
                    assertEveryItemHasStatus(response, "IN_PROGRESS");
                })
                .run();
    }

    @Regression
    @Test
    @DisplayName("EXPLAB-2696-EXP-02. Running experiments v1 не отдает созданный DRAFT-эксперимент")
    void runningExperimentsShouldNotReturnDraftExperiment() {
        getFlowWithRest()
                .step("Создаем DRAFT и IN_PROGRESS эксперименты через REST API", flow -> {
                    Long draftExperimentId = createExperiment(flow, ExperimentsStatusesV1.DRAFT);
                    Long runningExperimentId = createExperiment(flow, ExperimentsStatusesV1.IN_PROGRESS);

                    ValidatableResponseWrapper response = waitForExperimentInRunning(flow, runningExperimentId);

                    assertArrayContainsId(response, runningExperimentId);
                    assertArrayDoesNotContainId(response, draftExperimentId);
                    assertExperimentHasVersionAndStatus(response, runningExperimentId, 4, "IN_PROGRESS");
                })
                .run();
    }

    @Regression
    @Test
    @DisplayName("EXPLAB-2696-EXP-03. Running experiments v1 удаляет эксперимент из кэша после смены статуса")
    void runningExperimentsShouldRemoveStoppedExperimentFromCache() {
        getFlowWithRest()
                .step("Наполняем кэш running-экспериментом version=4", flow -> {
                    Long experimentId = createExperiment(flow, ExperimentsStatusesV1.IN_PROGRESS);

                    ValidatableResponseWrapper initialResponse = waitForExperimentInRunning(flow, experimentId);
                    assertArrayContainsId(initialResponse, experimentId);

                    changeExperimentStatus(flow, experimentId, ExperimentsStatusesV1.STOPPED);

                    ValidatableResponseWrapper actualResponse = waitForExperimentAbsentFromRunning(flow, experimentId);
                    assertArrayDoesNotContainId(actualResponse, experimentId);
                })
                .run();
    }

    @Regression
    @Test
    @DisplayName("EXPLAB-2696-EXP-04. Ручной evict running-cache идемпотентен для созданного эксперимента")
    void runningExperimentsEvictCashShouldBeIdempotent() {
        getFlowWithRest()
                .step("Создаем running-эксперимент и дважды обновляем кэш", flow -> {
                    Long experimentId = createExperiment(flow, ExperimentsStatusesV1.IN_PROGRESS);
                    flow.restCustomSteps().experimentsV1Steps().evictCashStatusOk();
                    flow.restCustomSteps().experimentsV1Steps().evictCashStatusOk();

                    ValidatableResponseWrapper response = flow.restCustomSteps().experimentsV1Steps()
                            .getExperimentsEnhanceRunningStatusOk();

                    assertArrayContainsId(response, experimentId);
                    assertExperimentHasVersionAndStatus(response, experimentId, 4, "IN_PROGRESS");
                })
                .run();
    }
}
