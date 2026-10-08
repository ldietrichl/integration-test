package ru.sber.qa.experiments.EXPLAB_2696;

import config.environment.EnvironmentConfigWithRest;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.allure.Regression;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.List;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("explab-2696-running-cache")
public class RunningSplitsV1Cache2696FlowTest extends AbstractRunningV1Cache2696FlowTest {

    @BeforeEach
    void requireV2CjToggleDisabled() {
        assumeV2CjExperimentsToggleDisabledStand();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2696-SPL-01. Running splits v1 отдает только сплиты IN_PROGRESS")
    void runningSplitsShouldReturnOnlyInProgress() {
        getFlowWithRest()
                .step("Создаем running-сплит и completed-сплит", flow -> {
                    Long runningSplitId = createSplit(flow, "IN_PROGRESS");
                    Long completedSplitId = createSplit(flow, "COMPLETED");

                    ValidatableResponseWrapper response = flow.restCustomSteps().splitSteps()
                            .getSplitsEnhanceRunningStatusOk();

                    assertArrayContainsId(response, runningSplitId);
                    assertArrayDoesNotContainId(response, completedSplitId);
                    assertEveryItemHasStatus(response, "IN_PROGRESS");
                })
                .run();
    }

    @Regression
    @Test
    @DisplayName("EXPLAB-2696-SPL-02. Running splits v1 фильтрует ответ по query ids")
    void runningSplitsShouldFilterByIds() {
        getFlowWithRest()
                .step("Создаем running-сплит и проверяем, что ids ограничивает ответ", flow -> {
                    Long expectedSplitId = createSplit(flow, "IN_PROGRESS");

                    ValidatableResponseWrapper response = flow.restCustomSteps().splitSteps()
                            .getSplitsEnhanceRunningStatusOk(List.of(expectedSplitId));

                    assertArrayContainsId(response, expectedSplitId);
                    assertArrayContainsOnlyIds(response, List.of(expectedSplitId));
                })
                .run();
    }

    @Regression
    @Test
    @DisplayName("EXPLAB-2696-SPL-03. Running splits v1 возвращает 400 при нечисловом ids")
    void runningSplitsShouldReturn400ForInvalidIdsType() {
        getFlowWithRest()
                .step("Запрашиваем running-сплиты с ids=abc", flow ->
                        flow.restCustomSteps().splitSteps()
                                .getSplitsEnhanceRunningWithRawIds("abc")
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("id != null"),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("message != null")
                                ))
                .run();
    }
}
