package ru.sber.qa.experiments.layers;

import config.environment.EnvironmentConfigWithRest;
import dto.experiments.layers.LayerGetChangeRequestDto;
import dto.experiments.layers.LayerGetChangeRequestDtoBuilder;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.matchers.JsonMatchers;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.matchers.conditions.TextConditions;
import ru.sber.qa.validation.ValidatableJson;
import ru.sber.qa.allure.CriticalRegression;

@ExtendWith(PerfeccionistaExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
public class ChangeLayerNegativeTest extends Flows {
    private static Long id;


    @CriticalRegression
    @Test
    @Order(10)
    @DisplayName("Изменение слоя - ошибка 400")
    void changeLayerBadRequestTest() {

        getFlowWithRest()
                .step("Отправка запроса на изменение слоя -> ошибка 'Некорректный запрос', проверка ответа", flow -> {

                    ValidatableJson result =
                            flow.restCustomSteps().layerSteps()
                                    .createOrChangeLayer(LayerGetChangeRequestDtoBuilder.buildDefaultDto()).toValidatableJson();
                    id = result.getJsonPath().getLong("id");

                    LayerGetChangeRequestDto body = LayerGetChangeRequestDtoBuilder.buildDefaultDto().toBuilder()
                            .id(id)
                            .shares(null)
                            .build();
                    flow.restCustomSteps().layerSteps().createOrChangeLayer(body)
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)
                            ).toValidatableJson()
                            .should(
                                    JsonMatchers.haveNotBlankJsonValue("id"),
                                    JsonMatchers.haveJsonValue("message",
                                            TextConditions.equalToText("Некорректный запрос")),
                                    JsonMatchers.evaluateJsonPathExpression("containsKey('params')")
                            );
                }).run();
    }

    @Test
    @Order(20)
    void deleteAfterTest() {

        getFlowWithRest().flow().restCustomSteps().layerSteps().deleteLayerById(id);
    }

    @CriticalRegression
    @Test
    @Order(30)
    @DisplayName("Изменение слоя - ошибка 422")
    void changeLayerNotFoundTest() {


        getFlowWithRest()
                .step("Отправка запроса, проверка ответа", flow -> {

                    LayerGetChangeRequestDto body = LayerGetChangeRequestDtoBuilder.buildDefaultDto().toBuilder()
                            .id(1000000000000000000L)
                            .build();
                    flow.restCustomSteps().layerSteps().createOrChangeLayer(body)
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                            ).toValidatableJson()
                            .should(
                                    JsonMatchers.haveNotBlankJsonValue("id"),
                                    JsonMatchers.haveJsonValue("message",
                                            TextConditions.equalToText("Слой с id=1000000000000000000 не найден")),
                                    JsonMatchers.evaluateJsonPathExpression("containsKey('params')")
                            );
                }).run();
    }
}
