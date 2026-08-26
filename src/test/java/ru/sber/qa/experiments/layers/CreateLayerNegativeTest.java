package ru.sber.qa.experiments.layers;

import config.environment.EnvironmentConfigWithRest;
import dto.experiments.layers.LayerGetChangeRequestDto;
import dto.experiments.layers.LayerGetChangeRequestDtoBuilder;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.matchers.JsonMatchers;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.matchers.conditions.TextConditions;
import ru.sber.qa.allure.CriticalRegression;

import java.util.List;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
public class CreateLayerNegativeTest extends Flows {

    @CriticalRegression
    @Test
    @DisplayName("Создание слоя - ответ 400")
    void createLayerBadRequestTest() {

        getFlowWithRest()
                .step("Отправка запроса на создание слоя -> ошибка 'Некорректный запрос'", flow -> {
                    LayerGetChangeRequestDto body = LayerGetChangeRequestDtoBuilder.buildDefaultDto().toBuilder().shares(null).build();
                    flow.restCustomSteps().layerSteps()
                            .createOrChangeLayer(body)
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)
                            ).toValidatableJson()
                            .should(
                                    JsonMatchers.haveNotBlankJsonValue("id"),
                                    JsonMatchers.haveJsonValue("message",
                                            TextConditions.equalToText("Некорректный запрос")),
                                    JsonMatchers.evaluateJsonPathExpression("containsKey('params')")
                            );
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("Создание слоя - ответ 422, некорректные границы диапазона распределения")
    void createLayerWrongSharesTest() {


        getFlowWithRest()
                .step("Отправка запроса на создание слоя -> ошибка 'Некорректные границы диапазона распределения'", flow -> {
                    LayerGetChangeRequestDto body = LayerGetChangeRequestDtoBuilder.buildDefaultDto().toBuilder()
                            .shares(List.of(
                                    LayerGetChangeRequestDto.Share.builder()
                                            .shareFrom(-500L)
                                            .shareTo(10500L)
                                            .build()))
                            .build();
                    flow.restCustomSteps().layerSteps().createOrChangeLayer(body)
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                            )
                            .toValidatableJson()
                            .should(
                                    JsonMatchers.haveNotBlankJsonValue("id"),
                                    JsonMatchers.haveJsonValue("message",
                                            TextConditions.equalToText("Некорректные границы диапазона распределения")),
                                    JsonMatchers.evaluateJsonPathExpression("containsKey('params')")
                            );
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("Создание слоя - ответ 422, пересечение границ интервалов распределения")
    void createLayerSharesCrossTest() {


        getFlowWithRest()
                .step("Отправка запроса на создание слоя -> ошибка 'Границы интервалов распределения пересекаются'", flow -> {
                    LayerGetChangeRequestDto body = LayerGetChangeRequestDtoBuilder.buildDefaultDto().toBuilder()
                            .shares(List.of(
                                    LayerGetChangeRequestDto.Share.builder()
                                            .shareFrom(100L)
                                            .shareTo(500L)
                                            .build(),
                                    LayerGetChangeRequestDto.Share.builder()
                                            .shareFrom(100L)
                                            .shareTo(600L)
                                            .build()
                            ))
                            .build();
                    flow.restCustomSteps().layerSteps().createOrChangeLayer(body)
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                            )
                            .toValidatableJson()
                            .should(
                                    JsonMatchers.haveNotBlankJsonValue("id"),
                                    JsonMatchers.haveJsonValue("message",
                                            TextConditions.equalToText("Границы интервалов распределения пересекаются")),
                                    JsonMatchers.evaluateJsonPathExpression("containsKey('params')")
                            );
                })
                .run();
    }
}
