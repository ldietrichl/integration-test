package ru.sber.qa.splitter.NewTest.document;

import com.fasterxml.jackson.databind.JsonNode;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ObjectSelectConditionDto;
import dto.splitter.split.SplitRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import util.support.SplitterVersionProvider;
import ru.sber.qa.allure.CriticalRegression;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static util.SplitterPrecalcAssertions.shouldBe200;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
/**
 * LG-2348 проверяет приоритеты слоев. В конфиге всех LG-experiment используется actionType=1.
 * ConfigMap splitter-rules-mapper.yml влияет на filtered flag для actionType=1, но эти сценарии
 * намеренно не assert'ят filtered: основной контракт здесь — layerId/layerPriority и итоговый MAIN.
 * Если в ConfigMap изменится final-exp-rule для actionType=1, LG-ожидания не должны измениться,
 * пока все experiment остаются с одинаковым actionType и различаются только layerPriority.
 */
public class SplitterLg2348LayerPriorityFlowTest extends AbstractSplitterDocumentFlowTest {

    private static final String OBJECT_ID = "23482348-2348-2348-2348-234823482348";
    private static final String SALT_LAYER_1 = "ABC";
    private static final String SALT_LAYER_2 = "QWE456";
    private static final String SALT_LAYER_3 = "RTY";

    private static final long EXP_LAYER_1 = 234801L;
    private static final long EXP_LAYER_2 = 234802L;
    private static final long EXP_LAYER_3_0_2000 = 234803L;
    private static final long EXP_LAYER_3_2000_5000 = 234804L;
    private static final long EXP_LAYER_3_5000_8000 = 234805L;
    private static final Long[] ALL_LG_EXPERIMENTS = ids(
            EXP_LAYER_1,
            EXP_LAYER_2,
            EXP_LAYER_3_0_2000,
            EXP_LAYER_3_2000_5000,
            EXP_LAYER_3_5000_8000);

    @CriticalRegression
    @ParameterizedTest(name = "{index}) {0}")
    @MethodSource("layerPriorityCases")
    @DisplayName("LG-2348. 16 комбинаций попадания клиента в слои 1/2/3")
    void clientShouldBeAssignedByLayerPriorityForEveryLayerCombination(String caseName,
                                                                       String splittingId,
                                                                       Long expectedMainExpId,
                                                                       Integer expectedLayerId,
                                                                       Integer expectedLayerPriority,
                                                                       Long[] expectedGroupAExpIds,
                                                                       String expectedMainSalt) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = lg2348Config(version);
        SplitRequestDto request = lg2348SplitRequest(splittingId);

        getFlowWithRest()
                .step("Загружаем конфиг LG-2348 с тремя слоями и свободным диапазоном в слое 3", flow -> loadConfigStep(flow, config))
                .step("Проверяем " + caseName + " для splittingId=" + splittingId + " без зависимости от ConfigMap filtered flag", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, request));
                    assertConfigVersion(response, version);
                    assertRestAllContainsOnlyWorkedExperiments(response, expectedGroupAExpIds);
                    if (expectedMainExpId == null) {
                        assertObjectHasAllButNoMainAssignment(response, OBJECT_ID);
                    } else {
                        assertObjectHasMainAndAll(response, OBJECT_ID);
                        assertMainExp(response, OBJECT_ID, expectedMainExpId);
                        assertMainLayer(response, OBJECT_ID, expectedLayerId, expectedLayerPriority);
                        assertMainActionType(response, OBJECT_ID, "1");
                        assertMainSpreadValue(response, OBJECT_ID, spread(expectedMainSalt, splittingId));
                    }
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("LG-2348-17. слой 1 перекрывает слой 2 и слой 3 даже при наличии всех ALL-связей")
    void firstLayerShouldWinWhenClientHitsAllLayers() {
        long version = SplitterVersionProvider.next();
        String splittingId = "LG2348-1705";
        LoadConfigRequestDto config = lg2348Config(version);
        SplitRequestDto request = lg2348SplitRequest(splittingId);

        getFlowWithRest()
                .step("Загружаем конфиг LG-2348", flow -> loadConfigStep(flow, config))
                .step("Проверяем приоритет слоя 1 над слоями 2 и 3", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, request));
                    assertConfigVersion(response, version);
                    assertRestAllContainsOnlyWorkedExperiments(
                            response, ids(EXP_LAYER_1, EXP_LAYER_2, EXP_LAYER_3_0_2000));
                    assertMainExp(response, OBJECT_ID, EXP_LAYER_1);
                    assertMainLayer(response, OBJECT_ID, 1, 10);
                    assertMainSpreadValue(response, OBJECT_ID, spread(SALT_LAYER_1, splittingId));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("LG-2348-18. свободный диапазон слоя 3 не создает MAIN при промахе слоев 1 и 2")
    void freeRangeOnThirdLayerShouldReturnEmptyMainWhenNoLayerMatches() {
        long version = SplitterVersionProvider.next();
        String splittingId = "LG2348-4";
        LoadConfigRequestDto config = lg2348Config(version);
        SplitRequestDto request = lg2348SplitRequest(splittingId);

        getFlowWithRest()
                .step("Загружаем конфиг LG-2348", flow -> loadConfigStep(flow, config))
                .step("Проверяем клиента из свободного диапазона layer3=[8000;10000)", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, request));
                    assertConfigVersion(response, version);
                    assertRestAllContainsOnlyWorkedExperiments(response, ids());
                    assertObjectHasAllButNoMainAssignment(response, OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("LG-2348-19. при равном layerPriority внутри одного слоя выбирается минимальный expId")
    void sameLayerPriorityTieShouldBeResolvedByMinExpId() {
        long version = SplitterVersionProvider.next();
        long lowExpId = 234810L;
        long highExpId = 234811L;
        ObjectSelectConditionDto condition = objectParamEqualsCondition(1, "ikp", "true", "BOOLEAN");
        LoadConfigRequestDto config = config(version,
                layeredExperiment((int) highExpId, SALT_LAYER_3, 3, 5, List.of(condition),
                        List.of(group("A", 0, 10000, 1, "1"))),
                layeredExperiment((int) lowExpId, SALT_LAYER_3, 3, 5, List.of(condition),
                        List.of(group("A", 0, 10000, 1, "1"))));
        SplitRequestDto request = lg2348SplitRequest("LG2348-TIE-SAME-LAYER");

        getFlowWithRest()
                .step("Загружаем два эксперимента одного слоя с одинаковым priority", flow -> loadConfigStep(flow, config))
                .step("Проверяем tie-break по минимальному expId", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, request));
                    assertConfigVersion(response, version);
                    assertAllRuleHasExpIdsExactly(response, OBJECT_ID, highExpId, lowExpId);
                    assertMainExp(response, OBJECT_ID, lowExpId);
                    assertMainLayer(response, OBJECT_ID, 3, 5);
                })
                .run();
    }

    private static Stream<Arguments> layerPriorityCases() {
        return Stream.of(
                Arguments.of("L1 miss, L2 miss, L3 exp31 [0;2000) -> MAIN=L3.1", "LG2348-0", EXP_LAYER_3_0_2000, 3, 5, ids(EXP_LAYER_3_0_2000), SALT_LAYER_3),
                Arguments.of("L1 miss, L2 miss, L3 exp32 [2000;5000) -> MAIN=L3.2", "LG2348-1", EXP_LAYER_3_2000_5000, 3, 5, ids(EXP_LAYER_3_2000_5000), SALT_LAYER_3),
                Arguments.of("L1 miss, L2 miss, L3 exp33 [5000;8000) -> MAIN=L3.3", "LG2348-3", EXP_LAYER_3_5000_8000, 3, 5, ids(EXP_LAYER_3_5000_8000), SALT_LAYER_3),
                Arguments.of("L1 miss, L2 miss, L3 free [8000;10000) -> empty MAIN", "LG2348-4", null, null, null, ids(), null),
                Arguments.of("L1 miss, L2 hit, L3 exp31 -> MAIN=L2", "LG2348-45", EXP_LAYER_2, 2, 7, ids(EXP_LAYER_2, EXP_LAYER_3_0_2000), SALT_LAYER_2),
                Arguments.of("L1 miss, L2 hit, L3 exp32 -> MAIN=L2", "LG2348-129", EXP_LAYER_2, 2, 7, ids(EXP_LAYER_2, EXP_LAYER_3_2000_5000), SALT_LAYER_2),
                Arguments.of("L1 miss, L2 hit, L3 exp33 -> MAIN=L2", "LG2348-2", EXP_LAYER_2, 2, 7, ids(EXP_LAYER_2, EXP_LAYER_3_5000_8000), SALT_LAYER_2),
                Arguments.of("L1 miss, L2 hit, L3 free -> MAIN=L2", "LG2348-349", EXP_LAYER_2, 2, 7, ids(EXP_LAYER_2), SALT_LAYER_2),
                Arguments.of("L1 hit, L2 miss, L3 exp31 -> MAIN=L1", "LG2348-100", EXP_LAYER_1, 1, 10, ids(EXP_LAYER_1, EXP_LAYER_3_0_2000), SALT_LAYER_1),
                Arguments.of("L1 hit, L2 miss, L3 exp32 -> MAIN=L1", "LG2348-11", EXP_LAYER_1, 1, 10, ids(EXP_LAYER_1, EXP_LAYER_3_2000_5000), SALT_LAYER_1),
                Arguments.of("L1 hit, L2 miss, L3 exp33 -> MAIN=L1", "LG2348-27", EXP_LAYER_1, 1, 10, ids(EXP_LAYER_1, EXP_LAYER_3_5000_8000), SALT_LAYER_1),
                Arguments.of("L1 hit, L2 miss, L3 free -> MAIN=L1", "LG2348-25", EXP_LAYER_1, 1, 10, ids(EXP_LAYER_1), SALT_LAYER_1),
                Arguments.of("L1 hit, L2 hit, L3 exp31 -> MAIN=L1", "LG2348-1705", EXP_LAYER_1, 1, 10, ids(EXP_LAYER_1, EXP_LAYER_2, EXP_LAYER_3_0_2000), SALT_LAYER_1),
                Arguments.of("L1 hit, L2 hit, L3 exp32 -> MAIN=L1", "LG2348-335", EXP_LAYER_1, 1, 10, ids(EXP_LAYER_1, EXP_LAYER_2, EXP_LAYER_3_2000_5000), SALT_LAYER_1),
                Arguments.of("L1 hit, L2 hit, L3 exp33 -> MAIN=L1", "LG2348-58", EXP_LAYER_1, 1, 10, ids(EXP_LAYER_1, EXP_LAYER_2, EXP_LAYER_3_5000_8000), SALT_LAYER_1),
                Arguments.of("L1 hit, L2 hit, L3 free -> MAIN=L1", "LG2348-412", EXP_LAYER_1, 1, 10, ids(EXP_LAYER_1, EXP_LAYER_2), SALT_LAYER_1)
        );
    }


    private void assertRestAllContainsOnlyWorkedExperiments(ValidatableResponseWrapper response,
                                                           Long[] workedExpIds) {
        if (workedExpIds.length == 0) {
            JsonNode object = findObject(response, OBJECT_ID);
            assertTrue(findRuleResult(object, "ALL", false) == null,
                    "После EXPLAB-2690 REST ALL не должен содержать эксперименты без сработавшей группы"
                            + body(response));
            return;
        }

        assertAllRuleHasExpIdsExactly(response, OBJECT_ID, workedExpIds);
        for (Long expId : workedExpIds) {
            assertAllExpGroup(response, OBJECT_ID, expId, "A");
            assertAllExpActionType(response, OBJECT_ID, expId, "1");
        }
    }

    private LoadConfigRequestDto lg2348Config(long version) {
        ObjectSelectConditionDto condition = objectParamEqualsCondition(1, "ikp", "true", "BOOLEAN");
        return config(version,
                layeredExperiment((int) EXP_LAYER_1, SALT_LAYER_1, 1, 10, List.of(condition),
                        List.of(group("A", 0, 1000, 1, "1"))),
                layeredExperiment((int) EXP_LAYER_2, SALT_LAYER_2, 2, 7, List.of(condition),
                        List.of(group("A", 0, 1000, 1, "1"))),
                layeredExperiment((int) EXP_LAYER_3_0_2000, SALT_LAYER_3, 3, 5, List.of(condition),
                        List.of(group("A", 0, 2000, 1, "1"))),
                layeredExperiment((int) EXP_LAYER_3_2000_5000, SALT_LAYER_3, 3, 5, List.of(condition),
                        List.of(group("A", 2000, 5000, 1, "1"))),
                layeredExperiment((int) EXP_LAYER_3_5000_8000, SALT_LAYER_3, 3, 5, List.of(condition),
                        List.of(group("A", 5000, 8000, 1, "1"))));
    }

    private SplitRequestDto lg2348SplitRequest(String splittingId) {
        return splitRequest(splittingId,
                object(OBJECT_ID, param("ikp", "true", "BOOLEAN")));
    }

    private static Long[] ids(Long... values) {
        return Arrays.copyOf(values, values.length);
    }
}
