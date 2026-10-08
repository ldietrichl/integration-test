package ru.sber.qa.configurations.v2;

import config.environment.special.EnvironmentConfigWIthRestDbV2;
import dto.configurations.v2.ConfigurationsV2ActionPostRequestDto;
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
import ru.sber.qa.matchers.DatabaseMatchers;
import ru.sber.qa.matchers.RestMatchers;
import org.junit.jupiter.api.Disabled;

import java.util.List;

import static dto.configurations.v2.ConfigurationsV2ActionPostRequestDtoBuilder.buildDefaultConfigsPostActionRequestDto;
import static dto.enums.Action.STOP;
import static dto.enums.Action.UPDATE;

/***
 * Позитивные тесты на отправку запроса на обработку изменений экспериментов
 * проверяемые action: START, UPDATE, STOP
 * тесты проверяют 200 статус код API запроса и запись в БД
 */
@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWIthRestDbV2.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PostConfigurationsActionRequestTest extends Flows {
    private static Long experimentId;

    @Disabled("TODO: тест напрямую изменяет состояние БД; необходимо пересмотреть тест и перевести подготовку данных на API/фикстуры.")
    @Test
    @DisplayName("UTIL: создание тестового эксперимента")
    @Order(10)
    void createExpV2ForTest() {
        experimentId = getFlowWithRest()
                .flow().restCustomSteps().experimentsV2Steps().createDefaultExperimentV2();
    }

    @Disabled("TODO: тест напрямую изменяет состояние БД; необходимо пересмотреть тест и перевести подготовку данных на API/фикстуры.")
    @Test
    @DisplayName("Проверка успешного ответа на запрос на обработку изменений экспериментов START")
    @Order(20)
    void postActionRequestSuccessTestStart() {
        getFlowWithDbRest()
                .step("Отправка запроса на обработку изменений экспериментов, проверка статуса 200 и записи в БД",
                        flow -> {
                            ConfigurationsV2ActionPostRequestDto body = buildDefaultConfigsPostActionRequestDto(experimentId);
                            flow.restCustomSteps().configurationsSteps().postConfigActionRequest(List.of(body))
                                    .should(
                                            RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                                    );

                            flow.dbCustomSteps().configsDbSteps().getExpActionRequestTableByRequestId(body.getRequestId())
                                    .should(
                                            DatabaseMatchers.tableHaveSize(1))
                                    .singleRow().should(
                                            DatabaseMatchers.haveCellValueEqualTo("action", "START"),
                                            DatabaseMatchers.haveCellValueEqualTo("exp_id", experimentId.toString())
                                    );

                            flow.dbCustomSteps().configsDbSteps().deleteExpActionRequestByRequestId(body.getRequestId());
                        })
                .run();
    }

    @Disabled("TODO: тест напрямую изменяет состояние БД; необходимо пересмотреть тест и перевести подготовку данных на API/фикстуры.")
    @Test
    @DisplayName("Проверка успешного ответа на запрос на обработку изменений экспериментов UPDATE")
    @Order(30)
    void postActionRequestSuccessTestU() {
        getFlowWithDbRest()
                .step("Отправка запроса на обработку изменений экспериментов, проверка статуса 200 и записи в БД",
                        flow -> {
                            ConfigurationsV2ActionPostRequestDto body = buildDefaultConfigsPostActionRequestDto(experimentId)
                                    .toBuilder()
                                    .action(UPDATE)
                                    .build();
                            flow.restCustomSteps().configurationsSteps().postConfigActionRequest(List.of(body))
                                    .should(
                                            RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                                    );

                            flow.dbCustomSteps().configsDbSteps().getExpActionRequestTableByRequestId(body.getRequestId())
                                    .should(
                                            DatabaseMatchers.tableHaveSize(1))
                                    .singleRow().should(
                                            DatabaseMatchers.haveCellValueEqualTo("action", "UPDATE"),
                                            DatabaseMatchers.haveCellValueEqualTo("exp_id", experimentId.toString())
                                    );

                            flow.dbCustomSteps().configsDbSteps().deleteExpActionRequestByRequestId(body.getRequestId());
                        })
                .run();
    }

    @Disabled("TODO: тест напрямую изменяет состояние БД; необходимо пересмотреть тест и перевести подготовку данных на API/фикстуры.")
    @Test
    @DisplayName("Проверка успешного ответа на запрос на обработку изменений экспериментов STOP")
    @Order(30)
    void postActionRequestSuccessTestUpdate() {
        getFlowWithDbRest()
                .step("Отправка запроса на обработку изменений экспериментов, проверка статуса 200 и записи в БД",
                        flow -> {
                            ConfigurationsV2ActionPostRequestDto body = buildDefaultConfigsPostActionRequestDto(experimentId)
                                    .toBuilder()
                                    .action(STOP)
                                    .build();
                            flow.restCustomSteps().configurationsSteps().postConfigActionRequest(List.of(body))
                                    .should(
                                            RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                                    );

                            flow.dbCustomSteps().configsDbSteps().getExpActionRequestTableByRequestId(body.getRequestId())
                                    .should(
                                            DatabaseMatchers.tableHaveSize(1))
                                    .singleRow().should(
                                            DatabaseMatchers.haveCellValueEqualTo("action", "STOP"),
                                            DatabaseMatchers.haveCellValueEqualTo("exp_id", experimentId.toString())
                                    );

                            flow.dbCustomSteps().configsDbSteps().deleteExpActionRequestByRequestId(body.getRequestId());
                        })
                .run();
    }

    @Disabled("TODO: тест напрямую изменяет состояние БД; необходимо пересмотреть тест и перевести подготовку данных на API/фикстуры.")
    @Test
    @DisplayName("UTIL: удаление тестового эксперимента")
    @Order(50)
    void deleteExpV2AfterTest() {
        getFlowWithRest().flow().restCustomSteps().experimentsV2Steps().deleteExperimentV2ById(experimentId)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
    }
}
