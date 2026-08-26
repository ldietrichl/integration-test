package ru.sber.qa.experiments.v2;

import config.environment.special.EnvironmentConfigWithRestV2;
import dto.experiments.v2.ExperimentV2PostRequestDto;
import dto.experiments.v2.ExperimentV2PostRequestDtoBuilder;
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

import java.util.List;

import static dto.experiments.v2.ExperimentV2PostRequestDtoBuilder.buildDefaultExperimentGroups;
import static dto.experiments.v2.ExperimentV2PostRequestDtoBuilder.buildDefaultShare;

/***
 * Тесты проверяют коды ошибок при изменении эксперимента в следующих случаях:
 * некорректный запрос
 * границы интервалов не корректны
 * интервалы между группами групп пересекаются
 * интервалы внутри группы пересекаются
 */

@ExtendWith(PerfeccionistaExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithRestV2.class)
public class ChangeExperimentV2NegativeTest extends Flows {
    private static ExperimentV2PostRequestDto body = ExperimentV2PostRequestDtoBuilder.buildDefaultExperimentV2PostRequestDto();
    private static Long experimentId;

    @Test
    @Order(10)
    @DisplayName("UTIL: Создание эксперимента для тестов")
    void createExpForNegativeTest() {
        ValidatableJson result =
                getFlowWithRest().flow().restCustomSteps().experimentsV2Steps()
                        .createOrChangeExperimentV2(ExperimentV2PostRequestDtoBuilder.buildDefaultExperimentV2PostRequestDto())
                        .should(
                                RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                        ).toValidatableJson();
        experimentId = result.getJsonPath().getLong("id");
    }

    @CriticalRegression
    @Test
    @DisplayName("Изменение эксперимента -> Некорректный запрос")
    @Order(20)
    void changeExperimentBadRequestTest() {
        getFlowWithRest()
                .step("Отправка запроса на изменение эксперимента, проверка ответа 'Некорректный запрос'", flow ->
                        flow.restCustomSteps().experimentsV2Steps()
                                .createOrChangeExperimentV2(body.toBuilder()
                                        .id(experimentId)
                                        .experimentGroups(null)
                                        .build())
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)
                                ).toValidatableJson()
                                .should(
                                        JsonMatchers.matchJsonSchemaInClasspath("schemes/experiments/experiments_get_response_400_schema.json"),
                                        JsonMatchers.haveJsonValue("message",
                                                TextConditions.equalToText("Некорректный запрос"))))
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("Изменение эксперимента -> Границы интервалов не корректны")
    @Order(30)
    void changeExperimentWrongSharesTest() {
        getFlowWithRest()
                .step("Отправка запроса на изменение эксперимента, проверка ответа 'Границы интервалов не корректны'", flow -> {
                    ExperimentV2PostRequestDto.ExperimentGroups.Share share = buildDefaultShare().toBuilder().shareFrom(-100).build();
                    ExperimentV2PostRequestDto.ExperimentGroups groups = buildDefaultExperimentGroups().toBuilder().shares(List.of(share)).build();
                    flow.restCustomSteps().experimentsV2Steps()
                            .createOrChangeExperimentV2(body.toBuilder()
                                    .id(experimentId)
                                    .experimentGroups(List.of(groups))
                                    .build())
                            .should(RestMatchers.haveStatusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                            ).toValidatableJson()
                            .should(
                                    JsonMatchers.matchJsonSchemaInClasspath("schemes/experiments/experiments_get_response_400_schema.json"),
                                    JsonMatchers.haveJsonValue("message",
                                            TextConditions.equalToText("Границы интервалов не корректны")));
                }).run();
    }

    @CriticalRegression
    @Test
    @DisplayName("Изменение эксперимента -> Интервалы групп пересекаются")
    @Order(40)
    void changeExpSharesCrossBetweenGroupsTest() {
        getFlowWithRest()
                .step("Отправка запроса на изменение эксперимента, проверка ответа 'Интервалы между группами групп пересекаются", flow -> {
                    ExperimentV2PostRequestDto.ExperimentGroups groupA = buildDefaultExperimentGroups();
                    ExperimentV2PostRequestDto.ExperimentGroups groupB = buildDefaultExperimentGroups().toBuilder()
                            .name("Вторая тестовая группа")
                            .symbolName("B")
                            .build();
                    flow.restCustomSteps().experimentsV2Steps()
                            .createOrChangeExperimentV2(body.toBuilder()
                                    .id(experimentId)
                                    .experimentGroups(List.of(groupA, groupB))
                                    .build())
                            .should(RestMatchers.haveStatusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                            ).toValidatableJson()
                            .should(
                                    JsonMatchers.matchJsonSchemaInClasspath("schemes/experiments/experiments_get_response_400_schema.json"),
                                    JsonMatchers.haveJsonValue("message",
                                            TextConditions.equalToText("Интервалы между группами групп пересекаются")));
                }).run();
    }

    @CriticalRegression
    @Test
    @DisplayName("Изменение эксперимента -> Интервалы внутри группы пересекаются")
    @Order(50)
    void changeExpSharesCrossInGroupTest() {
        getFlowWithRest()
                .step("Отправка запроса на изменение эксперимента, проверка ответа 'Интервалы внутри группы пересекаются", flow -> {
                    ExperimentV2PostRequestDto.ExperimentGroups.Share share = buildDefaultShare();
                    ExperimentV2PostRequestDto.ExperimentGroups groups = buildDefaultExperimentGroups().toBuilder()
                            .shares(List.of(share, share))
                            .build();
                    flow.restCustomSteps().experimentsV2Steps()
                            .createOrChangeExperimentV2(body.toBuilder()
                                    .id(experimentId)
                                    .experimentGroups(List.of(groups))
                                    .build())
                            .should(RestMatchers.haveStatusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                            ).toValidatableJson()
                            .should(
                                    JsonMatchers.matchJsonSchemaInClasspath("schemes/experiments/experiments_get_response_400_schema.json"),
                                    JsonMatchers.haveJsonValue("message",
                                            TextConditions.equalToText("Интервалы внутри группы пересекаются")));
                }).run();
    }

    @Test
    @Order(60)
    @DisplayName("UTIL: Удаление эксперимента после тестов")
    void deleteExpAfterNegativeTest() {
        getFlowWithDbRest().flow().restCustomSteps().experimentsV2Steps().getExperimentV2ById(experimentId);
    }
}
