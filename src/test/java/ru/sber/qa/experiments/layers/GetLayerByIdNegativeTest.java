package ru.sber.qa.experiments.layers;

import config.environment.EnvironmentConfigWithRest;
import constants.Endpoints;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.matchers.JsonMatchers;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.matchers.conditions.TextConditions;
import ru.sber.qa.allure.CriticalRegression;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
public class GetLayerByIdNegativeTest extends Flows {

    @CriticalRegression
    @Test
    @DisplayName("Получение слоя по id, ошибка 'Слой не найден'")
    public void getLayerByIdNotFoundTest() {


        getFlowWithRest()
                .step("Отправка запроса на получение слоя -> ошибка 'Слой не найден'", flow ->
                        flow.restCustomSteps().layerSteps().getLayerById(-10500L)
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                                )
                                .toValidatableJson()
                                .should(
                                        JsonMatchers.haveNotBlankJsonValue("id"),
                                        JsonMatchers.haveJsonValue("message",
                                                TextConditions.equalToText("Слой с id=-10500 не найден")),
                                        JsonMatchers.evaluateJsonPathExpression("containsKey('params')")
                                ))
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("Получение слоя по id - ошибка 400")
    public void getLayerByIdBadRequestTest() {


        getFlowWithRest()
                .step("Отправка запроса на получение слоя -> ошибка 'Некорректный запрос'", flow ->
                        flow.restClient()
                                .get(spec -> spec.pathParam("id", "thatsnotid"), Endpoints.LayersV1.V1_LAYERS_ID)
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)
                                )
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
