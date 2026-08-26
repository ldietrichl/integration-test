package ru.sber.qa.experiments.v2;

import config.environment.special.EnvironmentConfigWIthRestDbV2;
import constants.Endpoints;
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
import org.junit.jupiter.api.Disabled;

/***
 * Тесты проверяют коды ошибок при получении эксперимента по id в следующих случаях:
 * эксперимент не найден
 * версия эксперимента не корректна
 * некорректный запрос
 */
@ExtendWith(PerfeccionistaExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SetEnvironmentConfiguration(EnvironmentConfigWIthRestDbV2.class)
public class GetExperimentV2ByIdNegativeTest extends Flows {
    private static Long experimentId;

    @CriticalRegression
    @Test
    @DisplayName("Получение эксперимента по id -> эксперимент не найден")
    @Order(10)
    void getExperimentByIdNotFoundTest() {
        getFlowWithRest()
                .step("Получение эксперимента по id, проверка ответа 'Запись не найдена'", flow ->
                        flow.restCustomSteps().experimentsV2Steps()
                                .getExperimentV2ById(100000000000000000L)
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                                )
                                .toValidatableJson()
                                .should(
                                        JsonMatchers.matchJsonSchemaInClasspath("schemes/experiments/experiments_get_response_400_schema.json"),
                                        JsonMatchers.haveJsonValue("message",
                                                TextConditions.equalToText("Запись не найдена"))
                                )).run();
    }

    @Disabled("TODO: тест напрямую изменяет состояние БД; необходимо пересмотреть тест и перевести подготовку данных на API/фикстуры.")
    @Test
    @Order(20)
    @DisplayName("UTIL: создание эксперимента для теста, изменение версии на 4")
    void createExperimentForTest() {
        ValidatableJson result =
                getFlowWithRest().flow().restCustomSteps().experimentsV2Steps()
                        .createOrChangeExperimentV2(ExperimentV2PostRequestDtoBuilder.buildDefaultExperimentV2PostRequestDto())
                        .should(
                                RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                        ).toValidatableJson();
        experimentId = result.getJsonPath().getLong("id");
        getFlowWithDb().flow().dbExpLabClient()
                .executeUpdate("""
                        UPDATE experiments.experiment
                        SET version = 4
                        WHERE id = %s
                        """.formatted(experimentId));
    }

    @Disabled("TODO: тест напрямую изменяет состояние БД; необходимо пересмотреть тест и перевести подготовку данных на API/фикстуры.")
    @Test
    @DisplayName("Получение эксперимента по id -> версия не корректна")
    @Order(30)
    void getExperimentByIdWrongExpVersionTest() {
        getFlowWithRest()
                .step("Получение эксперимента по id, проверка ответа 'Версия эксперимента не корректна'", flow ->
                        flow.restCustomSteps().experimentsV2Steps()
                                .getExperimentV2ById(experimentId)
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                                )
                                .toValidatableJson()
                                .should(
                                        JsonMatchers.matchJsonSchemaInClasspath("schemes/experiments/experiments_get_response_400_schema.json"),
                                        JsonMatchers.haveJsonValue("message",
                                                TextConditions.equalToText("Версия эксперимента не корректна"))
                                )).run();
    }

    @Disabled("TODO: тест напрямую изменяет состояние БД; необходимо пересмотреть тест и перевести подготовку данных на API/фикстуры.")
    @Test
    @Order(40)
    @DisplayName("UTIL: изменение версии на 5 и удаление эксперимента после теста")
    void deleteExperimentAfterTest() {
        getFlowWithDbRest()
                .step("Отправка запроса на удаление эксперимента V2", flow -> {
                    flow.dbExpLabClient()
                            .executeUpdate("""
                                    UPDATE experiments.experiment
                                    SET version = 5
                                    WHERE id = %s
                                    """.formatted(experimentId));
                    flow.restCustomSteps().experimentsV2Steps()
                            .deleteExperimentV2ById(experimentId)
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_OK));
                }).run();
    }

    @CriticalRegression
    @Test
    @Order(50)
    @DisplayName("Получение эксперимента по id -> некорректный запрос")
    void getExperimentByIdBadRequestTest() {
        getFlowWithRest()
                .step("Получение эксперимента по id, проверка ответа 'Некорректный запрос'", flow ->
                        flow.restClient()
                                .get(spec -> spec.pathParam("id", "that's not id"), Endpoints.ExperimentsV2.V2_EXPERIMENTS_ID)
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)
                                ).toValidatableJson()
                                .should(
                                        JsonMatchers.matchJsonSchemaInClasspath("schemes/experiments/experiments_get_response_400_schema.json"),
                                        JsonMatchers.haveJsonValue("message",
                                                TextConditions.equalToText("Некорректный запрос"))
                                )).run();
    }
}
