package ru.sber.qa.experiments.v2;

import config.environment.special.EnvironmentConfigWithRestV2;
import dto.experiments.v2.ExperimentV2PostRequestDto;
import dto.experiments.v2.ExperimentV2PostRequestDtoBuilder;
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

import java.util.List;

import static dto.experiments.v2.ExperimentV2PostRequestDtoBuilder.buildDefaultExperimentGroups;
import static dto.experiments.v2.ExperimentV2PostRequestDtoBuilder.buildDefaultShare;

/***
 * Тесты проверяют коды ошибок при создании эксперимента в следующих случаях:
 * некорректный запрос
 * границы интервалов не корректны
 * интервалы между группами групп пересекаются
 * интервалы внутри группы пересекаются
 */
@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithRestV2.class)
public class CreateExperimentV2NegativeTest extends Flows {
    private static ExperimentV2PostRequestDto body = ExperimentV2PostRequestDtoBuilder.buildDefaultExperimentV2PostRequestDto();

    @CriticalRegression
    @Test
    @DisplayName("Создание эксперимента -> Некорректный запрос")
    void createExperimentBadRequestTest() {
        getFlowWithRest()
                .step("Отправка запроса на создание эксперимента, проверка ответа 'Некорректный запрос'", flow ->
                        flow.restCustomSteps().experimentsV2Steps()
                                .createOrChangeExperimentV2(body.toBuilder()
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
    @DisplayName("Создание эксперимента -> Границы интервалов не корректны")
    void createExperimentWrongSharesTest() {
        getFlowWithRest()
                .step("Отправка запроса на создание эксперимента, проверка ответа 'Границы интервалов не корректны'", flow -> {
                    ExperimentV2PostRequestDto.ExperimentGroups.Share share = buildDefaultShare().toBuilder().shareFrom(-100).build();
                    ExperimentV2PostRequestDto.ExperimentGroups groups = buildDefaultExperimentGroups().toBuilder().shares(List.of(share)).build();
                    flow.restCustomSteps().experimentsV2Steps()
                            .createOrChangeExperimentV2(body.toBuilder()
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
    @DisplayName("Создание эксперимента -> Интервалы групп пересекаются")
    void createExpSharesCrossBetweenGroupsTest() {
        getFlowWithRest()
                .step("Отправка запроса на создание эксперимента, проверка ответа 'Интервалы между группами групп пересекаются", flow -> {
                    ExperimentV2PostRequestDto.ExperimentGroups groupA = buildDefaultExperimentGroups();
                    ExperimentV2PostRequestDto.ExperimentGroups groupB = buildDefaultExperimentGroups().toBuilder()
                            .name("Вторая тестовая группа")
                            .symbolName("B")
                            .build();
                    flow.restCustomSteps().experimentsV2Steps()
                            .createOrChangeExperimentV2(body.toBuilder()
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
    @DisplayName("Создание эксперимента -> Интервалы внутри группы пересекаются")
    void createExpSharesCrossInGroupTest() {
        getFlowWithRest()
                .step("Отправка запроса на создание эксперимента, проверка ответа 'Интервалы внутри группы пересекаются", flow -> {
                    ExperimentV2PostRequestDto.ExperimentGroups.Share share = buildDefaultShare();
                    ExperimentV2PostRequestDto.ExperimentGroups groups = buildDefaultExperimentGroups().toBuilder()
                            .shares(List.of(share, share))
                            .build();
                    flow.restCustomSteps().experimentsV2Steps()
                            .createOrChangeExperimentV2(body.toBuilder()
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
}
