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
 * Тесты проверяют коды ошибок при удалении эксперимента в следующих случаях:
 * эксперимент не найден
 * версия эксперимента не корректна
 * некорректный запрос
 * после запуска эксперимент не может быть удален
 */
@ExtendWith(PerfeccionistaExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SetEnvironmentConfiguration(EnvironmentConfigWIthRestDbV2.class)
public class DeleteExperimentV2NegativeTest extends Flows {
    private static Long experimentId;

    @CriticalRegression
    @Test
    @DisplayName("Удаление эксперимента -> некорректный запрос")
    @Order(10)
    void deleteExpBadRequestTest() {
        getFlowWithRest()
                .step("Удаление эксперимента, проверка ответа 'Некорректный запрос'", flow ->
                        flow.restClient()
                                .delete(spec -> spec.pathParam("id", "that's not id"), Endpoints.ExperimentsV2.V2_EXPERIMENTS_ID)
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)
                                ).toValidatableJson()
                                .should(
                                        JsonMatchers.matchJsonSchemaInClasspath("schemes/experiments/experiments_get_response_400_schema.json"),
                                        JsonMatchers.haveJsonValue("message",
                                                TextConditions.equalToText("Некорректный запрос"))
                                )).run();
    }

    @CriticalRegression
    @Test
    @DisplayName("Удаление эксперимента -> эксперимент не найден")
    @Order(20)
    void deleteExperimentNotFoundTest() {
        getFlowWithRest()
                .step("Удаление эксперимента, проверка ответа 'Запись не найдена'", flow ->
                        flow.restCustomSteps().experimentsV2Steps().deleteExperimentV2ById(1000000000000L)
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                                ).toValidatableJson()
                                .should(
                                        JsonMatchers.matchJsonSchemaInClasspath("schemes/experiments/experiments_get_response_400_schema.json"),
                                        JsonMatchers.haveJsonValue("message",
                                                TextConditions.equalToText("Запись не найдена"))
                                )).run();
    }

    @Disabled("TODO: тест напрямую изменяет состояние БД; необходимо пересмотреть тест и перевести подготовку данных на API/фикстуры.")
    @Test
    @Order(30)
    @DisplayName("UTIL: создание тестового эксперимента и изменение его версии на 4")
    void createExperimentForTest() {
        ValidatableJson result =
                getFlowWithRest().flow().restCustomSteps().experimentsV2Steps()
                        .createOrChangeExperimentV2(ExperimentV2PostRequestDtoBuilder.buildDefaultExperimentV2PostRequestDto())
                        .should(
                                RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                        ).toValidatableJson();
        experimentId = result.getJsonPath().getLong("id");
        getFlowWithDbRest().flow().dbExpLabClient()
                .executeUpdate("""
                        UPDATE experiments.experiment
                        SET version = 4
                        WHERE id = %s
                        """.formatted(experimentId));

    }

    @Disabled("TODO: тест напрямую изменяет состояние БД; необходимо пересмотреть тест и перевести подготовку данных на API/фикстуры.")
    @Test
    @DisplayName("Удаление эксперимента -> версия не корректна")
    @Order(40)
    void getExperimentByIdWrongExpVersionTest() {
        getFlowWithRest()
                .step("Удаление эксперимента, проверка ответа 'Версия эксперимента не корректна'", flow ->
                        flow.restCustomSteps().experimentsV2Steps()
                                .deleteExperimentV2ById(experimentId)
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
    @Order(50)
    @DisplayName("UTILS: изменение версии эксперимента на корректную")
    void changeExperimentVersionForTest() {
        getFlowWithDbRest()
                .flow().dbExpLabClient()
                .executeUpdate("""
                        UPDATE experiments.experiment
                        SET version = 5
                        WHERE id = %s
                        """.formatted(experimentId));
    }

    @Disabled("TODO: тест напрямую изменяет состояние БД; необходимо пересмотреть тест и перевести подготовку данных на API/фикстуры.")
    @Test
    @Order(60)
    @DisplayName("Удаление эксперимента -> эксперимент не может быть удален после запуска")
    void cannotDeleteExpInProgressTest() {
        getFlowWithDbRest()
                .step("Удаление эксперимента, проверка ответа 'После запуска эксперимент не может быть удален'", flow -> {
                    flow.dbExpLabClient()
                            .executeUpdate("""
                                    UPDATE experiments.experiment
                                    SET status = 'IN_PROGRESS'
                                    WHERE id = %s
                                    """.formatted(experimentId));
                    flow.restCustomSteps().experimentsV2Steps().deleteExperimentV2ById(experimentId)
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                            )
                            .toValidatableJson()
                            .should(
                                    JsonMatchers.matchJsonSchemaInClasspath("schemes/experiments/experiments_get_response_400_schema.json"),
                                    JsonMatchers.haveJsonValue("message",
                                            TextConditions.equalToText("После запуска эксперимент не может быть удален")));
                }).run();
    }

    @Disabled("TODO: тест напрямую изменяет состояние БД; необходимо пересмотреть тест и перевести подготовку данных на API/фикстуры.")
    @Test
    @Order(70)
    @DisplayName("UTIL: удаление эксперимента после теста")
    void deleteExperimentAfterTest() {
        getFlowWithDbRest()
                .flow().dbExpLabClient().executeUpdate("""
                        UPDATE experiments.experiment
                        SET status = 'DRAFT'
                        WHERE id = %s
                        """.formatted(experimentId));
        getFlowWithDbRest().flow().restCustomSteps().experimentsV2Steps().getExperimentV2ById(experimentId);
    }
}
