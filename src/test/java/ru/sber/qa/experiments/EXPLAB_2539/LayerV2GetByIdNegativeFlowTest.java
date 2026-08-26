package ru.sber.qa.experiments.EXPLAB_2539;

import config.environment.EnvironmentConfigWithRest;
import constants.Endpoints;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.matchers.JsonMatchers;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.matchers.conditions.TextConditions;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("explab-2539-layer-v2-by-id")
public class LayerV2GetByIdNegativeFlowTest extends Flows {

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2539-LAY-04. GET /api/v2/experiments/layers/{id}: слой не найден")
    void getLayerByIdV2NotFoundShouldReturnErrorContract() {
        long missingLayerId = -253900L;

        getFlowWithRest()
                .step("Отправляем v2 GET layer by id для несуществующего слоя", flow ->
                        flow.restCustomSteps().layerV2Steps().getLayerById(missingLayerId)
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY))
                                .toValidatableJson()
                                .should(
                                        JsonMatchers.haveNotBlankJsonValue("id"),
                                        JsonMatchers.haveJsonValue("message",
                                                TextConditions.equalToText("Слой с id=" + missingLayerId + " не найден")),
                                        JsonMatchers.evaluateJsonPathExpression("containsKey('params')")
                                ))
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2539-LAY-05. GET /api/v2/experiments/layers/{id}: некорректный id возвращает 400")
    void getLayerByIdV2BadRequestShouldReturnErrorContract() {
        getFlowWithRest()
                .step("Отправляем v2 GET layer by id с некорректным path id", flow ->
                        flow.restClient()
                                .get(spec -> spec.pathParam("id", "thatsnotid"), Endpoints.LayersV2.V2_LAYERS_ID)
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST))
                                .toValidatableJson()
                                .should(
                                        JsonMatchers.haveNotBlankJsonValue("id"),
                                        JsonMatchers.haveJsonValue("message",
                                                TextConditions.equalToText("Некорректный запрос")),
                                        JsonMatchers.evaluateJsonPathExpression("containsKey('params')")
                                ))
                .run();
    }
}
