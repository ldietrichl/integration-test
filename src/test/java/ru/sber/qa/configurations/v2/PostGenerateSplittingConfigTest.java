package ru.sber.qa.configurations.v2;

import config.environment.special.EnvironmentConfigWIthRestDbV2;
import dto.configurations.v2.ConfigurationsV2GenerateSplittingConfigPostRequestDto;
import dto.configurations.v2.ConfigurationsV2GenerateSplittingConfigPostRequestDtoBuilder;
import dto.enums.RequestSourceSplitConfig;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.matchers.DatabaseMatchers;
import ru.sber.qa.matchers.RestMatchers;
import org.junit.jupiter.api.Disabled;

/***
 * Данный класс представляет набор позитивных тестов для проверки API запроса на формирование новой конфигурации сплиттования,
 * а также проверка наличия записи в таблице splitting_config_request.
 * Проверка запроса от CONFIG_SERVICE и SPLITTER
 */
@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWIthRestDbV2.class)
public class PostGenerateSplittingConfigTest extends Flows {

    @Disabled("TODO: тест напрямую изменяет состояние БД; необходимо пересмотреть тест и перевести подготовку данных на API/фикстуры.")
    @Test
    @DisplayName("Отправка запроса от CONFIG_SERVICE на формирование новой конфигурации сплиттования, проверка статуса 200")
    void postGenerateSplittingConfigFromConfigServiceSuccessTest() {
        getFlowWithDbRest()
                .step("Отправка запроса, проверка ответа", flow -> {
                    ConfigurationsV2GenerateSplittingConfigPostRequestDto body = ConfigurationsV2GenerateSplittingConfigPostRequestDtoBuilder.buildDefaultDto();
                    flow.restCustomSteps().configurationsSteps()
                            .postGenerateSplittingConfig(body)
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                            );

                    flow.dbCustomSteps().configsDbSteps().getSplittingConfigRequestTableByRequestId(body.getRequestId())
                            .should(
                                    DatabaseMatchers.tableHaveSize(1)
                            ).singleRow()
                            .should(
                                    DatabaseMatchers.haveCellValueEqualTo("splitting_point", body.getSplittingPoint()),
                                    DatabaseMatchers.haveCellValueEqualTo("request_source", "1")
                            );

                    flow.dbCustomSteps().configsDbSteps().deleteSplittingConfigRequestByRequestId(body.getRequestId());
                })
                .run();
    }

    @Disabled("TODO: тест напрямую изменяет состояние БД; необходимо пересмотреть тест и перевести подготовку данных на API/фикстуры.")
    @Test
    @DisplayName("Отправка запроса от SPLITTER на формирование новой конфигурации сплиттования, проверка статуса 200")
    void postGenerateSplittingConfigFromSplitterSuccessTest() {
        getFlowWithDbRest()
                .step("Отправка запроса, проверка ответа", flow -> {
                    ConfigurationsV2GenerateSplittingConfigPostRequestDto body = ConfigurationsV2GenerateSplittingConfigPostRequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .requestSource(RequestSourceSplitConfig.SPLITTER)
                            .build();
                    flow.restCustomSteps().configurationsSteps()
                            .postGenerateSplittingConfig(body)
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                            );

                    flow.dbCustomSteps().configsDbSteps().getSplittingConfigRequestTableByRequestId(body.getRequestId())
                            .should(
                                    DatabaseMatchers.tableHaveSize(1)
                            )
                            .singleRow()
                            .should(
                                    DatabaseMatchers.haveCellValueEqualTo("splitting_point", body.getSplittingPoint()),
                                    DatabaseMatchers.haveCellValueEqualTo("request_source", "0")
                            );

                    flow.dbCustomSteps().configsDbSteps().deleteSplittingConfigRequestByRequestId(body.getRequestId());
                })
                .run();
    }
}
