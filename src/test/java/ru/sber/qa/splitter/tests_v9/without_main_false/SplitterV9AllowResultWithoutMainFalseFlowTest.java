package ru.sber.qa.splitter.tests_v9.without_main_false;


import ru.sber.qa.allure.ManualTest;
import com.fasterxml.jackson.databind.JsonNode;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ShareDto;
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

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@ManualTest
@DisplayName("Tests-v9. MAPPER: SPLITTER_ALLOW_RESULT_WITHOUT_MAIN=false")
public class SplitterV9AllowResultWithoutMainFalseFlowTest extends AbstractSplitterV9FlowTest {

    @Test
    @DisplayName("SPL-V9-FALSE-01. При no-main и SPLITTER_ALLOW_RESULT_WITHOUT_MAIN=false объект без MAIN не возвращается")
    void mapperNoMainShouldNotReturnEmptyMainWhenAllowResultWithoutMainIsFalse() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = singleDocumentConfig(version);
        String splittingId = splittingIdForRange("SPL-V9-FALSE-01", V9_SALT, 7500, 10000);
        SplitRequestDto request = splitRequest(splittingId,
                object(OBJECT_1,
                        param("id1", "1", "INTEGER"),
                        param("id2", "2", "INTEGER"),
                        param("id3", "3", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем MAPPER config для проверки no-main при SPLITTER_ALLOW_RESULT_WITHOUT_MAIN=false",
                        flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Выполняем split без сработавшей итоговой группы: splittingId=" + splittingId
                                + ", spread=" + spread(V9_SALT, splittingId),
                        flow -> {
                            ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                            assertBasicResponseContract(response, request, version);
                            assertSplittingResultsHaveUniqueObjectIds(response);
                            assertNoEmptyMainRuleForEveryObject(response);
                            assertObjectAbsentOrObjectResultsEmpty(response, OBJECT_1);
                        })
                .run();
    }

    @Test
    @DisplayName("SPL-V9-FALSE-02. При группе без resultParams и SPLITTER_ALLOW_RESULT_WITHOUT_MAIN=false пустой MAIN не возвращается")
    void mapperEmptyResultParamsShouldNotReturnEmptyMainWhenAllowResultWithoutMainIsFalse() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = singleDocumentConfig(version);
        String splittingId = splittingIdForRange("SPL-V9-FALSE-02", V9_SALT, 5000, 7500);
        SplitRequestDto request = splitRequest(splittingId,
                object(OBJECT_1,
                        param("id1", "1", "INTEGER"),
                        param("id2", "2", "INTEGER"),
                        param("id3", "3", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем MAPPER config с группой C без resultParams для проверки false-профиля",
                        flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Выполняем split в диапазон группы C без actionType/resultParams: splittingId=" + splittingId
                                + ", spread=" + spread(V9_SALT, splittingId),
                        flow -> {
                            ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                            assertBasicResponseContract(response, request, version);
                            assertSplittingResultsHaveUniqueObjectIds(response);
                            assertNoEmptyMainRuleForEveryObject(response);
                            assertObjectAbsentOrObjectResultsEmpty(response, OBJECT_1);
                        })
                .run();
    }

    @Test
    @DisplayName("SPL-V9-FALSE-03. При смешанном запросе false-профиль сохраняет объект с MAIN и исключает объект без MAIN")
    void mapperMixedObjectsShouldKeepSelectedMainAndSuppressObjectWithoutMainWhenAllowResultWithoutMainIsFalse() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = mapperAlternativeConfig(version);
        String splittingId = splittingIdForRange("SPL-V9-FALSE-03", V9_SALT, 1500, 2000);
        SplitRequestDto request = splitRequest(splittingId,
                object(OBJECT_1, param("id1", "1", "INTEGER")),
                object(OBJECT_2, param("id2", "2", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем MAPPER config для смешанного false-профиля",
                        flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Выполняем split: objectId=1 имеет MAIN, objectId=2 не должен получать пустой MAIN; splittingId="
                                + splittingId + ", spread=" + spread(V9_SALT, splittingId),
                        flow -> {
                            ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                            assertBasicResponseContract(response, request, version);
                            assertSplittingResultsHaveUniqueObjectIds(response);
                            assertNoEmptyMainRuleForEveryObject(response);

                            assertRuleResultSize(response, OBJECT_1, "MAIN", 1);
                            assertFirstRuleExp(response, OBJECT_1, "MAIN", 1L, "A", "A");
                            assertObjectAbsentOrObjectResultsEmpty(response, OBJECT_2);
                            assertAllResponseRowsAreWorkedGroupsForEveryObject(response);
                        })
                .run();
    }

    private void assertNoEmptyMainRuleForEveryObject(ValidatableResponseWrapper response) {
        JsonNode root = jsonBody(response, "Ожидали JSON body для проверки отсутствия пустого MAIN");
        JsonNode results = root.path("splittingResults");
        assertTrue(results.isArray(), "splittingResults должен быть массивом" + body(response));

        for (JsonNode object : results) {
            JsonNode objectResults = object.path("objectResults");
            if (!objectResults.isArray()) {
                continue;
            }
            for (JsonNode rule : objectResults) {
                if (!Objects.equals("MAIN", rule.path("ruleCode").asText(null))) {
                    continue;
                }
                JsonNode resultExps = rule.path("resultExps");
                boolean emptyMain = resultExps.isMissingNode()
                        || resultExps.isNull()
                        || (resultExps.isArray() && resultExps.isEmpty());

                assertFalse(emptyMain,
                        "При SPLITTER_ALLOW_RESULT_WITHOUT_MAIN=false в ответе не должно быть MAIN.resultExps=[] "
                                + "или пустого MAIN. objectId=" + object.path("objectId").asText(null) + body(response));
            }
        }
    }

    private void assertObjectAbsentOrObjectResultsEmpty(ValidatableResponseWrapper response, String objectId) {
        if (!hasObject(response, objectId)) {
            return;
        }

        JsonNode object = findObjectById(response, objectId);
        JsonNode objectResults = object.path("objectResults");
        boolean empty = objectResults.isMissingNode()
                || objectResults.isNull()
                || (objectResults.isArray() && objectResults.isEmpty());

        assertTrue(empty,
                "При SPLITTER_ALLOW_RESULT_WITHOUT_MAIN=false объект без итогового MAIN должен отсутствовать "
                        + "или иметь пустой objectResults. Не допускается технический MAIN.resultExps=[]. "
                        + "objectId=" + objectId + body(response));
    }

    private LoadConfigRequestDto singleDocumentConfig(long version) {
        return configFor(EndpointMode.MAPPER, version,
                experiment(1,
                        V9_SALT,
                        List.of(
                                objectParamEqualsCondition(1, "id1", "1", "INTEGER"),
                                objectParamEqualsCondition(2, "id2", "2", "INTEGER"),
                                objectParamEqualsCondition(3, "id3", "3", "INTEGER")),
                        List.of(
                                groupWithDocResult("A", 0, 2500, 1, "1", "1"),
                                groupWithDocResult("B", 2500, 5000, 2, "2", "2"),
                                groupWithEmptyResultParams("C", 5000, 7500, 3))));
    }

    private LoadConfigRequestDto mapperAlternativeConfig(long version) {
        return configFor(EndpointMode.MAPPER, version,
                experiment(1,
                        V9_SALT,
                        List.of(
                                objectParamEqualsCondition(1, "id1", "1", "INTEGER"),
                                objectParamEqualsCondition(2, "id2", "2", "INTEGER")),
                        List.of(
                                groupWithDocResult("A", List.of(share(0, 2500)), 1, "0", "1"),
                                groupWithDocResult("B", List.of(share(2500, 5000)), 2, "1", "2"))),
                experiment(2,
                        V9_SALT,
                        List.of(objectParamEqualsCondition(1, "id1", "1", "INTEGER")),
                        List.of(groupWithDocResult("A", List.of(share(0, 1000), share(2500, 3500), share(5000, 6000)), 1, "1", "2"))),
                experiment(3,
                        V9_SALT,
                        List.of(objectParamEqualsCondition(1, "id2", "2", "INTEGER")),
                        List.of(groupWithDocResult("A", shares(0, 500, 1000, 1500, 2500, 3000, 3500, 4000, 5000, 5500, 6000, 6500), 1, "3", "3"))));
    }

    private List<ShareDto> shares(int... boundaries) {
        if (boundaries.length % 2 != 0) {
            throw new IllegalArgumentException("Share boundaries should be pairs");
        }
        java.util.ArrayList<ShareDto> shares = new java.util.ArrayList<>();
        for (int i = 0; i < boundaries.length; i += 2) {
            shares.add(share(boundaries[i], boundaries[i + 1]));
        }
        return shares;
    }
}
