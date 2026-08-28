package ru.sber.qa.experiments.EXPLAB_2696;

import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.config.ApiEnvironmentConfiguration;
import ru.sber.qa.services.rest.RestService;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
@ResourceLock("explab-2696-running-cache")
public class RunningExperimentsV1Cache2696FlowTest extends AbstractRunningV1Cache2696FlowTest {

    // Для этих сценариев тоггл EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED должен быть выключен
    // или не задан явно в JVM/env.
    @BeforeEach
    void requireV2CjToggleDisabled() {
        assumeV2CjExperimentsToggleDisabledStand();
    }

    @Tag("CriticalRegression")
    @Test
    @DisplayName("EXPLAB-2696-EXP-01. Running experiments v1 отдает IN_PROGRESS version=4 после ручного evict")
    void runningExperimentsShouldContainInProgressVersion4AfterManualEvict(RestService restService) {
        Long experimentId = createExperiment(restService, ExperimentStatus.IN_PROGRESS);
        evictCash(restService);

        ValidatableResponseWrapper response = getRunningExperiments(restService);

        assertArrayContainsId(response, experimentId);
        assertExperimentHasVersionAndStatus(response, experimentId, 4, "IN_PROGRESS");
        assertEveryItemHasStatus(response, "IN_PROGRESS");
        assertArrayHasNoDuplicateIds(response);
    }

    @Tag("Regression")
    @Test
    @DisplayName("EXPLAB-2696-EXP-02. Running experiments v1 не отдает созданный DRAFT-эксперимент")
    void runningExperimentsShouldNotReturnDraftExperiment(RestService restService) {
        Long draftExperimentId = createExperiment(restService, ExperimentStatus.DRAFT);
        Long runningExperimentId = createExperiment(restService, ExperimentStatus.IN_PROGRESS);

        ValidatableResponseWrapper response = waitForExperimentInRunning(restService, runningExperimentId);

        assertArrayContainsId(response, runningExperimentId);
        assertArrayDoesNotContainId(response, draftExperimentId);
        assertExperimentHasVersionAndStatus(response, runningExperimentId, 4, "IN_PROGRESS");
    }

    @Tag("Regression")
    @Test
    @DisplayName("EXPLAB-2696-EXP-03. Running experiments v1 удаляет эксперимент из кэша после смены статуса")
    void runningExperimentsShouldRemoveStoppedExperimentFromCache(RestService restService) {
        Long experimentId = createExperiment(restService, ExperimentStatus.IN_PROGRESS);

        ValidatableResponseWrapper initialResponse = waitForExperimentInRunning(restService, experimentId);
        assertArrayContainsId(initialResponse, experimentId);

        changeExperimentStatus(restService, experimentId, ExperimentStatus.STOPPED);

        ValidatableResponseWrapper actualResponse = waitForExperimentAbsentFromRunning(restService, experimentId);
        assertArrayDoesNotContainId(actualResponse, experimentId);
    }

    @Tag("Regression")
    @Test
    @DisplayName("EXPLAB-2696-EXP-04. Ручной evict running-cache идемпотентен для созданного эксперимента")
    void runningExperimentsEvictCashShouldBeIdempotent(RestService restService) {
        Long experimentId = createExperiment(restService, ExperimentStatus.IN_PROGRESS);
        evictCash(restService);
        evictCash(restService);

        ValidatableResponseWrapper response = getRunningExperiments(restService);

        assertArrayContainsId(response, experimentId);
        assertExperimentHasVersionAndStatus(response, experimentId, 4, "IN_PROGRESS");
        assertArrayHasNoDuplicateIds(response);
    }

    @Tag("Regression")
    @Test
    @DisplayName("EXPLAB-2696-EXP-05. GET running experiments актуализирует cache без ручного evict")
    void runningExperimentsGetShouldRefreshCacheWithoutManualEvict(RestService restService) {
        Long experimentId = createExperiment(restService, ExperimentStatus.IN_PROGRESS);

        ValidatableResponseWrapper response = waitForExperimentInRunning(restService, experimentId);

        assertArrayContainsId(response, experimentId);
        assertExperimentHasVersionAndStatus(response, experimentId, 4, "IN_PROGRESS");
        assertArrayHasNoDuplicateIds(response);
    }

    @Tag("Regression")
    @Test
    @DisplayName("EXPLAB-2696-EXP-06. Running experiments v1 исключает DRAFT и AGREED")
    void runningExperimentsShouldExcludeDraftAndAgreedExperiments(RestService restService) {
        Long draftExperimentId = createExperiment(restService, ExperimentStatus.DRAFT);
        Long agreedExperimentId = createExperiment(restService, ExperimentStatus.AGREED);
        Long runningExperimentId = createExperiment(restService, ExperimentStatus.IN_PROGRESS);

        ValidatableResponseWrapper response = waitForExperimentInRunning(restService, runningExperimentId);

        assertArrayContainsId(response, runningExperimentId);
        assertArrayDoesNotContainId(response, draftExperimentId);
        assertArrayDoesNotContainId(response, agreedExperimentId);
        assertEveryItemHasStatus(response, "IN_PROGRESS");
        assertExperimentHasVersionAndStatus(response, runningExperimentId, 4, "IN_PROGRESS");
    }

    @Tag("Regression")
    @Test
    @DisplayName("EXPLAB-2696-EXP-07. Running experiments v1 возвращает контракт расширенного DTO")
    void runningExperimentsShouldReturnExternalDtoContract(RestService restService) {
        Long experimentId = createExperiment(restService, ExperimentStatus.IN_PROGRESS);

        ValidatableResponseWrapper response = waitForExperimentInRunning(restService, experimentId);

        assertArrayContainsId(response, experimentId);
        assertExperimentHasExternalDtoContract(response, experimentId);
    }
}
