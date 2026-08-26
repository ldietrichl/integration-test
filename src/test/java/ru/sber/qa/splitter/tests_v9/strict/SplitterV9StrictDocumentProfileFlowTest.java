package ru.sber.qa.splitter.tests_v9.strict;

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

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("Tests-v9. Строгие проверки документного профиля")
public class SplitterV9StrictDocumentProfileFlowTest extends AbstractSplitterV9FlowTest {

    @Test
    @DisplayName("SPL-V9-STRICT-01. MAPPER API ALL содержит только сработавшие группы")
    void mapperApiAllShouldContainOnlyWorkedGroupsForDocumentProfile() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = mapperAlternativeConfig(version);
        String splittingId = splittingIdForRange("SPL-V9-STRICT-01", V9_SALT, 1000, 1500);
        SplitRequestDto request = splitRequest(splittingId,
                object(OBJECT_1, param("id1", "1", "INTEGER")),
                object(OBJECT_2, param("id2", "2", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем MAPPER config для строгой проверки фильтрации ALL", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем, что в API ALL остались только строки expGroup == finalExpGroup", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertAllResponseRowsAreWorkedGroups(response, OBJECT_1);
                    assertAllResponseRowsAreWorkedGroups(response, OBJECT_2);
                })
                .run();
    }

    @Test
    @DisplayName("SPL-V9-STRICT-02. REACTIONS API ALL содержит все сработавшие experiments по документной матрице")
    void reactionsApiAllShouldContainWorkedExperimentsForDocumentProfile() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = reactionsConfig(version);
        String splittingId = splittingIdForRange("SPL-V9-STRICT-02", V9_SALT, 0, 2500);
        SplitRequestDto request = splitRequest(splittingId, object(OBJECT_REACTIONS, param("segment", "v9", "STRING")));

        getFlowWithRest()
                .step("Загружаем REACTIONS config для строгой проверки ALL", flow -> loadConfig(flow, EndpointMode.REACTIONS, config))
                .step("Проверяем document-profile ожидание: ALL=1..6, MAIN=4/5/6", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.REACTIONS, request);
                    assertBasicResponseContract(response, request, version);
                    assertRuleExpIdsExactly(response, OBJECT_REACTIONS, "ALL", 1L, 2L, 3L, 4L, 5L, 6L);
                    assertRuleExpIdsExactly(response, OBJECT_REACTIONS, "MAIN", 4L, 5L, 6L);
                    assertRuleExpsUseWorkedGroups(response, OBJECT_REACTIONS, "MAIN");
                    assertRuleExpsHaveNoExpFlags(response, OBJECT_REACTIONS, "MAIN");
                    assertNoAlternativeTrueAnywhere(response);
                })
                .run();
    }

    @Test
    @DisplayName("SPL-V9-STRICT-03. API response содержит resultDt, если документный контракт включен в реализации")
    void apiResponseShouldContainResultDtForDocumentProfile() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = singleDocumentConfig(version);
        SplitRequestDto request = splitRequest("SPL-V9-STRICT-03",
                object(OBJECT_1,
                        param("id1", "1", "INTEGER"),
                        param("id2", "2", "INTEGER"),
                        param("id3", "3", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем config для проверки resultDt в API", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем наличие resultDt в REST response по документу", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertTrue(hasFieldRecursively(jsonBody(response, "Ожидали JSON body для resultDt"), "resultDt"),
                            "Документный профиль ожидает resultDt в REST response" + body(response));
                })
                .run();
    }

    @Test
    @DisplayName("SPL-V9-STRICT-04. DENY-профиль пустого MAIN не возвращает MAIN с пустым resultExps")
    void noMainDenyProfileShouldNotReturnEmptyMainRule() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = singleDocumentConfig(version);
        String splittingId = splittingIdForRange("SPL-V9-STRICT-04", V9_SALT, 7500, 10000);
        SplitRequestDto request = splitRequest(splittingId,
                object(OBJECT_1,
                        param("id1", "1", "INTEGER"),
                        param("id2", "2", "INTEGER"),
                        param("id3", "3", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем config для DENY-профиля пустого MAIN", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем, что объект возвращается без назначенного MAIN", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertObjectEmptyOrAbsent(response, OBJECT_1);
                    if (hasObject(response, OBJECT_1)) {
                        assertRuleAbsent(response, OBJECT_1, "MAIN");
                        assertRuleAbsent(response, OBJECT_1, "ALL");
                    }
                })
                .run();
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
                        List.of(groupWithDocResult("A", List.of(share(0, 500), share(1000, 1500), share(2500, 3000), share(3500, 4000), share(5000, 5500), share(6000, 6500)), 1, "3", "3"))));
    }

    private LoadConfigRequestDto reactionsConfig(long version) {
        return configFor(EndpointMode.REACTIONS, version,
                reactionExperiment(1, 1, 1, 0, 2500, "1"),
                reactionExperiment(2, 2, 2, 0, 7500, "2"),
                reactionExperiment(3, 2, 2, 0, 2500, "3"),
                reactionExperiment(4, 3, 3, 0, 5000, "4"),
                reactionExperiment(5, 3, 3, 0, 7500, "5"),
                reactionExperiment(6, 3, 3, 0, 5000, "6"));
    }

    private ExperimentDto reactionExperiment(int expId,
                                             int layerId,
                                             int layerPriority,
                                             int shareFrom,
                                             int shareTo,
                                             String resultValue) {
        return layeredExperiment(expId,
                V9_SALT,
                layerId,
                layerPriority,
                List.of(objectParamEqualsCondition(1, "segment", "v9", "STRING")),
                List.of(groupWithDocResult("A", shareFrom, shareTo, 1, "1", resultValue)));
    }
}
