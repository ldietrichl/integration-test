package ru.sber.qa.configurations.v2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import config.environment.special.EnvironmentConfigWIthRestDbV2;
import dto.configurations.v2.ConfigurationsV2ActionPostRequestDto;
import dto.enums.Action;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.matchers.DatabaseMatchers;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.allure.CriticalRegression;

import java.util.List;
import java.util.UUID;

import static dto.configurations.v2.ConfigurationsV2ActionPostRequestDtoBuilder.buildDefaultConfigsPostActionRequestDto;

/***
 * Класс представляет собой набор негативных тестов для проверки API запроса на обработку изменений экспериментов
 * А также проверяется, что в БД не создается запись.
 * Проверяются ошибки валидации в следующих случаях:
 * нет requestId,
 * пустое body,
 * невалидный action,
 * невалидный json,
 * невалидный requestId;
 */
@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWIthRestDbV2.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PostConfigurationsActionRequestNegativeTest extends Flows {
    private static Long experimentId;

    @Test
    @DisplayName("UTIL: создание тестового эксперимента")
    @Order(10)
    void createExpV2ForTest() {
        experimentId = getFlowWithRest()
                .flow().restCustomSteps().experimentsV2Steps().createDefaultExperimentV2();
    }

    @CriticalRegression
    @Test
    @DisplayName("Проверка ошибки валидации на обработку изменений экспериментов - нет requestId")
    @Order(20)
    void postActionRequestWithoutRequestIdTest() {
        getFlowWithDbRest()
                .step("Отправка и проверка запроса на обработку изменений экспериментов без requestId", flow -> {
                    ConfigurationsV2ActionPostRequestDto body = buildDefaultConfigsPostActionRequestDto(experimentId)
                            .toBuilder()
                            .requestId(null)
                            .build();
                    flow.restCustomSteps().configurationsSteps().postConfigActionRequest(List.of(body))
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)
                            );
                    flow.dbExpLabClient().executeSelect("""
                                    SELECT * FROM configurations.exp_action_request
                                        WHERE exp_id = '%s'""".formatted(experimentId))
                            .should(
                                    DatabaseMatchers.tableHaveSize(0)
                            );
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("Проверка ошибки валидации на обработку изменений экспериментов - пустое body")
    @Order(30)
    void postActionRequestNullBodyTest() {
        getFlowWithDbRest()
                .step("Отправка и проверка запроса на обработку изменений экспериментов", flow -> {
                    ConfigurationsV2ActionPostRequestDto body = buildDefaultConfigsPostActionRequestDto(experimentId).toBuilder()
                            .requestId(null)
                            .action(null)
                            .splittingPoint(null)
                            .requestSource(null)
                            .expId(null)
                            .build();
                    flow.restCustomSteps().configurationsSteps().postConfigActionRequest(List.of(body))
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)
                            );
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("Проверка ошибки валидации на обработку изменений экспериментов - невалидный action")
    @Order(40)
    void postActionRequestInvalidActionTest() {
        getFlowWithDbRest()
                .step("Отправка и проверка запроса на обработку изменений экспериментов", flow -> {
                    ConfigurationsV2ActionPostRequestDto body = buildDefaultConfigsPostActionRequestDto(experimentId)
                            .toBuilder()
                            .action(Action.INVALID_ACTION)
                            .build();
                    flow.restCustomSteps().configurationsSteps().postConfigActionRequest(List.of(body))
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)
                            );
                    flow.dbCustomSteps().configsDbSteps().getExpActionRequestTableByRequestId(body.getRequestId())
                            .should(
                                    DatabaseMatchers.tableHaveSize(0)
                            );
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("Проверка ошибки валидации на обработку изменений экспериментов - невалидный JSON")
    @Order(50)
    void postActionRequestInvalidJsonTest() {
        getFlowWithDbRest()
                .step("Отправка запроса с невалидным JSON", flow -> {
                    ObjectMapper om = new ObjectMapper();
                    ObjectNode node = om.createObjectNode();
                    node.put("expId", experimentId);
                    node.put("action", "STOP");
                    node.put("requestSource", "UI");
                    node.put("splittingPoint", "MAPPER");
                    node.put("requestId", String.valueOf(UUID.randomUUID()));

                    String validJson;
                    try {
                        validJson = om.writeValueAsString(List.of(node)); // Получаем валидный JSON: [...]
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException("Ошибка сериализации JSON", e);
                    }

                    // Портим JSON: убираем последнюю закрывающую скобку → делаем невалидным
                    String invalidJson = validJson.substring(0, validJson.length() - 1);

                    // Отправляем испорченный JSON как строка
                    flow.restCustomSteps().configurationsSteps().postConfigActionRequest(invalidJson)
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST) // Ожидаем 400 из-за parsing error
                            );
                })
                .run();
    }

    @Test
    @Disabled
    @DisplayName("Проверка ошибки валидации на обработку изменений экспериментов - невалидный requestId")
    @Order(60)
    void postActionRequestInvalidRequestIdTest() {
        getFlowWithDbRest()
                .step("Отправка запроса с невалидным JSON", flow -> {
                    String requestId = "123"; // ⚠️ невалидный
                    ConfigurationsV2ActionPostRequestDto body = buildDefaultConfigsPostActionRequestDto(experimentId).toBuilder()
                            .action(Action.STOP)
                            .requestId(requestId)
                            .build();

                    flow.restCustomSteps().configurationsSteps().postConfigActionRequest(List.of(body))
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST) // Ожидаем 400 из-за parsing error
                            );
                    flow.dbCustomSteps().configsDbSteps().getExpActionRequestTableByRequestId(requestId)
                            .should(
                                    DatabaseMatchers.tableHaveSize(0)
                            );
                })
                .run();
    }

    @Test
    @DisplayName("UTIL: удаление тестового эксперимента")
    @Order(70)
    void deleteExpV2AfterTest() {
        getFlowWithRest().flow().restCustomSteps().experimentsV2Steps().deleteExperimentV2ById(experimentId)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
    }
}
