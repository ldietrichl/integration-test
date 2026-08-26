package ru.sber.qa.experiments.EXPLAB_2696;

import config.environment.EnvironmentConfigWithRest;
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
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.List;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("explab-2696-running-cache")
public class RunningV1CacheV2CjEnabled2696FlowTest extends AbstractRunningV1Cache2696FlowTest {

  //  @BeforeEach
   // void requireV2CjToggleEnabled() {
    //    assumeV2CjExperimentsToggleEnabledStand();
    //}

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2696-TGL-01. V2 CJ toggle глушит running experiments v1 и ручной evict")
    void v2CjToggleShouldReturnEmptyExperimentsAndSkipV1CacheEvict() {
        getFlowWithRest()
                .step("Проверяем пустой ответ running experiments до и после evict при включенном toggle", flow -> {
                    ValidatableResponseWrapper beforeEvict = flow.restCustomSteps().experimentsV1Steps()
                            .getExperimentsEnhanceRunningStatusOk();
                    assertArrayIsEmpty(beforeEvict);

                    flow.restCustomSteps().experimentsV1Steps().evictCashStatusOk();

                    ValidatableResponseWrapper afterEvict = flow.restCustomSteps().experimentsV1Steps()
                            .getExperimentsEnhanceRunningStatusOk();
                    assertArrayIsEmpty(afterEvict);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2696-TGL-02. V2 CJ toggle глушит running splits v1")
    void v2CjToggleShouldReturnEmptySplits() {
        getFlowWithRest()
                .step("Проверяем пустой ответ running splits при включенном toggle", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().splitSteps()
                            .getSplitsEnhanceRunningStatusOk();

                    assertArrayIsEmpty(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2696-TGL-03. V2 CJ toggle глушит running splits v1 при query ids")
    void v2CjToggleShouldReturnEmptySplitsForIds() {
        getFlowWithRest()
                .step("Проверяем пустой ответ running splits с ids при включенном toggle", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().splitSteps()
                            .getSplitsEnhanceRunningStatusOk(List.of(1L));

                    assertArrayIsEmpty(response);
                })
                .run();
    }
}
