package ru.sber.qa.configurations.v2;

import config.environment.special.EnvironmentConfigWIthRestDbV2;
import dto.configurations.v2.ConfigurationsV2GenerateSplittingConfigPostRequestDto;
import dto.configurations.v2.ConfigurationsV2GenerateSplittingConfigPostRequestDtoBuilder;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.matchers.DatabaseMatchers;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.allure.CriticalRegression;

/***
 * Данный класс представляет набор негативных тестов для проверки API запроса на формирование новой конфигурации сплиттования
 * + проверка отсутствия записи в БД
 * в следующих случаях:
 * отсутствие requestId,
 * невалидный requestId,
 * пустой splittingPoint;
 */
@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWIthRestDbV2.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PostGenerateSplittingConfigNegativeTest extends Flows {

    @CriticalRegression
    @Test
    @DisplayName("Проверка ошибки валидации на запрос без requestId на формирование новой конфигурации сплиттования")
    void postGenerateSplittingConfigWithoutRequestIdTest() {
        getFlowWithDbRest()
                .step("Отправка и проверка запроса на формирование новой конфигурации сплиттования", flow -> {
                    ConfigurationsV2GenerateSplittingConfigPostRequestDto body = ConfigurationsV2GenerateSplittingConfigPostRequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .requestId(null)
                            .build();
                    flow.restCustomSteps().configurationsSteps().postGenerateSplittingConfig(body)
                            .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("Проверка ошибки валидации на запрос нна формирование новой конфигурации сплиттования с невалидным requestId")
    void postGenerateSplittingConfigWithInvalidRequestIdTest() {
        getFlowWithDbRest()
                .step("Отправка и проверка запроса на формирование новой конфигурации сплиттования", flow -> {
                    String requestId = "123";
                    ConfigurationsV2GenerateSplittingConfigPostRequestDto body = ConfigurationsV2GenerateSplittingConfigPostRequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .requestId(requestId)
                            .build();

                    flow.restCustomSteps().configurationsSteps().postGenerateSplittingConfig(body)
                            .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST));

                    flow.dbCustomSteps().configsDbSteps().getSplittingConfigRequestTableByRequestId(requestId)
                            .should(
                                    DatabaseMatchers.tableHaveSize(0)
                            );
                })
                .run();
    }


    @CriticalRegression
    @Test
    @DisplayName("Проверка ошибки валидации на запрос на формирование новой конфигурации сплиттования с пустым splittingPoint")
    void postGenerateSplittingConfigWithNullSplittingPointTest() {
        getFlowWithDbRest()
                .step("Отправка и проверка запроса на формирование новой конфигурации сплиттования", flow -> {
                    ConfigurationsV2GenerateSplittingConfigPostRequestDto body = ConfigurationsV2GenerateSplittingConfigPostRequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .splittingPoint(null)
                            .build();

                    flow.restCustomSteps().configurationsSteps().postGenerateSplittingConfig(body)
                            .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST));

                    flow.dbCustomSteps().configsDbSteps().getSplittingConfigRequestTableByRequestId(body.getRequestId())
                            .should(
                                    DatabaseMatchers.tableHaveSize(0)
                            );
                })
                .run();
    }
}
