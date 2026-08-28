package ru.sber.qa.experiments.EXPLAB_2696;

import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.config.ApiEnvironmentConfiguration;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.RestService;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.List;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
@ResourceLock("explab-2696-running-cache")
public class RunningSplitsV1Cache2696FlowTest extends AbstractRunningV1Cache2696FlowTest {

    // Для этих сценариев тоггл EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED должен быть выключен
    // или не задан явно в JVM/env.
    @BeforeEach
    void requireV2CjToggleDisabled() {
        assumeV2CjExperimentsToggleDisabledStand();
    }

    @Tag("CriticalRegression")
    @Test
    @DisplayName("EXPLAB-2696-SPL-01. Running splits v1 отдает только сплиты IN_PROGRESS")
    void runningSplitsShouldReturnOnlyInProgress(RestService restService) {
        Long runningSplitId = createSplit(restService, "IN_PROGRESS");
        Long completedSplitId = createSplit(restService, "COMPLETED");

        ValidatableResponseWrapper response = getRunningSplits(restService);

        assertArrayContainsId(response, runningSplitId);
        assertArrayDoesNotContainId(response, completedSplitId);
        assertEveryItemHasStatus(response, "IN_PROGRESS");
    }

    @Tag("Regression")
    @Test
    @DisplayName("EXPLAB-2696-SPL-02. Running splits v1 фильтрует ответ по query ids")
    void runningSplitsShouldFilterByIds(RestService restService) {
        Long expectedSplitId = createSplit(restService, "IN_PROGRESS");
        Long unexpectedSplitId = createSplit(restService, "IN_PROGRESS");

        ValidatableResponseWrapper response = getRunningSplits(restService, List.of(expectedSplitId));

        assertArrayContainsId(response, expectedSplitId);
        assertArrayDoesNotContainId(response, unexpectedSplitId);
        assertArrayContainsOnlyIds(response, List.of(expectedSplitId));
    }

    @Tag("Regression")
    @Test
    @DisplayName("EXPLAB-2696-SPL-03. Running splits v1 возвращает 400 при нечисловом ids")
    void runningSplitsShouldReturn400ForInvalidIdsType(RestService restService) {
        getRunningSplitsWithRawIds(restService, "abc")
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST));
    }

    @Tag("Regression")
    @Test
    @DisplayName("EXPLAB-2696-SPL-04. Running splits v1 возвращает пустой массив для неизвестного ids")
    void runningSplitsShouldReturnEmptyArrayForUnknownIds(RestService restService) {
        Long unknownSplitId = Long.MAX_VALUE - 2696;

        ValidatableResponseWrapper response = getRunningSplits(restService, List.of(unknownSplitId));

        assertArrayIsEmpty(response);
    }

    @Tag("Regression")
    @Test
    @DisplayName("EXPLAB-2696-SPL-05. Running splits v1 возвращает контракт внешнего DTO")
    void runningSplitsShouldReturnExternalDtoContract(RestService restService) {
        Long runningSplitId = createSplit(restService, "IN_PROGRESS", 4);

        ValidatableResponseWrapper response = getRunningSplits(restService);

        assertArrayContainsId(response, runningSplitId);
        assertSplitHasExternalDtoContract(response, runningSplitId);
    }
}
