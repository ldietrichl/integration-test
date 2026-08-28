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

import java.util.List;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
@ResourceLock("explab-2696-running-cache")
public class RunningV1CacheV2CjEnabled2696FlowTest extends AbstractRunningV1Cache2696FlowTest {

    // Для этих сценариев тоггл EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED должен быть включен:
    // yaml сервиса обновлен, pod'ы перезапущены, в запуск тестов передан -DEXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=true.
    @BeforeEach
    void requireV2CjToggleEnabled() {
        assumeV2CjExperimentsToggleEnabledStand();
    }

    @Tag("CriticalRegression")
    @Test
    @DisplayName("EXPLAB-2696-TGL-01. V2 CJ toggle глушит running experiments v1 и ручной evict")
    void v2CjToggleShouldReturnEmptyExperimentsAndSkipV1CacheEvict(RestService restService) {
        ValidatableResponseWrapper beforeEvict = getRunningExperiments(restService);
        assertArrayIsEmpty(beforeEvict);

        evictCash(restService);

        ValidatableResponseWrapper afterEvict = getRunningExperiments(restService);
        assertArrayIsEmpty(afterEvict);
    }

    @Tag("CriticalRegression")
    @Test
    @DisplayName("EXPLAB-2696-TGL-02. V2 CJ toggle глушит running splits v1")
    void v2CjToggleShouldReturnEmptySplits(RestService restService) {
        ValidatableResponseWrapper response = getRunningSplits(restService);

        assertArrayIsEmpty(response);
    }

    @Tag("CriticalRegression")
    @Test
    @DisplayName("EXPLAB-2696-TGL-03. V2 CJ toggle глушит running splits v1 при query ids")
    void v2CjToggleShouldReturnEmptySplitsForIds(RestService restService) {
        ValidatableResponseWrapper response = getRunningSplits(restService, List.of(1L));

        assertArrayIsEmpty(response);
    }
}
