package ru.sber.qa.splitter.tests_v9.document;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.split.SplitRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.tests_v9.common.AbstractSplitterV9FlowTest;
import util.support.SplitterVersionProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("Tests-v9 / EXPLAB-2690. MAPPER: отсутствие технического MAIN")
@AnyConfigLoadMode
public class SplitterV9DocumentMapperMainPresenceAndAllCleanupFlowTest extends AbstractSplitterV9FlowTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CASE_ID = "SPL-V9-MAPPER-NO-TECHNICAL-MAIN-01";
    private static final String CONFIG_RESOURCE = "splitter/tests_v9/document/main_presence_regression/conf-main-missing-source.json";
    private static final String REQUEST_RESOURCE = "splitter/tests_v9/document/main_presence_regression/req-main-missing-source.json";
    private static final String SOURCE_OBJECT_ID = "032e7ed0-340d-41b3-908f-9f8183a96de9";

    @Test
    @DisplayName("SPL-V9-MAPPER-NO-TECHNICAL-MAIN-01. Объект без выбранного MAIN выглядит как несвязанный")
    void mapperResponseShouldNotExposeTechnicalMainWhenFinalExperimentIsNotSelected() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = readResource(CONFIG_RESOURCE, LoadConfigRequestDto.class);
        config.setConfigVersion(version);
        config.setMessageId(UUID.randomUUID().toString());
        config.setRequestId(UUID.randomUUID().toString());

        SplitRequestDto request = readResource(REQUEST_RESOURCE, SplitRequestDto.class);
        request.setRequestId(UUID.randomUUID().toString());

        getFlowWithRest()
                .step("Фиксируем исходные данные регрессии: config + split request", flow -> {
                    attachResource("Исходный config", CONFIG_RESOURCE, "application/json", ".json");
                    attachResource("Исходный split request", REQUEST_RESOURCE, "application/json", ".json");
                })
                .step("Загружаем MAPPER config для " + CASE_ID,
                        flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем новый REST empty-contract EXPLAB-2690", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertSplittingResultsHaveUniqueObjectIds(response);
                    assertObjectEmptyOrAbsent(response, SOURCE_OBJECT_ID);
                    if (hasObject(response, SOURCE_OBJECT_ID)) {
                        assertRuleAbsent(response, SOURCE_OBJECT_ID, "MAIN");
                        assertRuleAbsent(response, SOURCE_OBJECT_ID, "ALL");
                    }
                    assertAllResponseRowsAreWorkedGroupsForEveryObject(response);
                })
                .run();
    }

    private <T> T readResource(String resourcePath, Class<T> type) {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                fail("Не найден test-resource: " + resourcePath);
            }
            return MAPPER.readValue(stream, type);
        } catch (IOException exception) {
            fail("Не удалось прочитать test-resource " + resourcePath + ": " + exception.getMessage());
            return null;
        }
    }
}
