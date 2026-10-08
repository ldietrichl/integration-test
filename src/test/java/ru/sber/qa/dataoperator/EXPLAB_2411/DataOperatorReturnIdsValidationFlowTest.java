package ru.sber.qa.dataoperator.EXPLAB_2411;

import config.environment.EnvironmentConfigWithRest;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import request.dataoperator.v2.DataOperatorV2TestDataFactory;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("data-operator-splitting-objects")
public class DataOperatorReturnIdsValidationFlowTest extends Flows {

    @Test
    @DisplayName("EXPLAB-2411-FT-10. Массив в returnIds отклоняется с 400")
    void nonBooleanReturnIdsShouldBeRejected() {
        getFlowWithRest()
                .step("Отправляем returnIds массивом", flow -> flow.restCustomSteps().dataOperatorV2Steps()
                        .getSplittingObjectsStatusBadRequest(
                                DataOperatorV2TestDataFactory.invalidReturnIdsTypeRequest()))
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2411-FT-11. Добавление returnIds не отменяет обязательность page")
    void requestWithoutPageShouldBeRejected() {
        getFlowWithRest()
                .step("Отправляем запрос без обязательного page", flow -> flow.restCustomSteps().dataOperatorV2Steps()
                        .getSplittingObjectsStatusBadRequest(
                                DataOperatorV2TestDataFactory.requestWithoutRequiredPage()))
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2411-FT-12. Добавление returnIds не отменяет обязательность size")
    void requestWithoutSizeShouldBeRejected() {
        getFlowWithRest()
                .step("Отправляем запрос без обязательного size", flow -> flow.restCustomSteps().dataOperatorV2Steps()
                        .getSplittingObjectsStatusBadRequest(
                                DataOperatorV2TestDataFactory.requestWithoutRequiredSize()))
                .run();
    }
}
