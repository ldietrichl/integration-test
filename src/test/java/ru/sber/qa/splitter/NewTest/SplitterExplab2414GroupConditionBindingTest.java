package ru.sber.qa.splitter.NewTest;

import com.fasterxml.jackson.databind.JsonNode;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.common.ParamDto;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.GroupDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ObjectSelectConditionDto;
import dto.splitter.config.RuleDto;
import dto.splitter.config.ShareDto;
import dto.splitter.config.SplittingConfigDto;
import dto.splitter.config.SplittingResultDto;
import dto.splitter.precalc.SplitterPrecalcObjectDto;
import dto.splitter.precalc.SplitterPrecalcParamDto;
import dto.splitter.precalc.SplitterPrecalcRequestDto;
import dto.splitter.split.SplitRequestDto;
import dto.splitter.split.SplittingObjectDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import util.splittercheck.SplitterResponseReader;
import util.splittercheck.SplitterResponseSnapshot;
import util.support.SplitterVersionProvider;
import ru.sber.qa.allure.CriticalRegression;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static util.SplitterPrecalcAssertions.shouldBe200;
import static util.SplitterPrecalcAssertions.shouldBeConfigLoaded;
import static util.SplitterPrecalcAssertions.shouldHaveJsonBody;
import static util.SplitterPrecalcAssertions.shouldHaveNonEmptySplitEnvelope;
import static util.SplitterPrecalcAssertions.shouldHaveResponseId;
import static util.SplitterPrecalcAssertions.shouldHaveSoConfigVersion;
import static util.SplitterPrecalcAssertions.shouldHaveSplittingResultsSize;

/**
 * EXPLAB-2414.
 *
 * Проверяем корректировку алгоритма привязки объектов к группам:
 * если в одной группе объект подошел под несколько condition одного experiment,
 * должна остаться связь только по condition с минимальным id.
 * При этом тот же object может быть связан с тем же expId по другой группе.
 */
@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
public class SplitterExplab2414GroupConditionBindingTest extends AbstractNewSplitterFlowTest {

    private static final int SO_CONFIG_VERSION = 1;
    private static final int EXP_ID = 1;
    private static final String SPLITTING_ID_GROUP_A = "EXPLAB-2414-0-2";      // spread=1623
    private static final String SPLITTING_ID_GROUP_B = "EXPLAB-2414-5000-2";   // spread=9649
    private static final String SALT = "EXPLAB-2414-SALT";

    private static final String X1_OBJECT_ID = "24140000-0000-0000-0000-000000000001";
    private static final String X2_OBJECT_ID = "24140000-0000-0000-0000-000000000002";
    private static final String X3_OBJECT_ID = "24140000-0000-0000-0000-000000000003";
    private static final String X4_OBJECT_ID = "24140000-0000-0000-0000-000000000004";

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2414-01. REST проверяет минимальный conditionId отдельно для сработавших групп A и B")
    void splitShouldKeepOnlyMinConditionInsideSameGroupAndKeepAnotherGroup() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = explab2414Config(version);
        SplitRequestDto splitForGroupA = runtimeSplitRequestForGroupA();
        SplitRequestDto splitForGroupB = runtimeSplitRequestForGroupB();

        getFlowWithRest()
                .step("Загружаем config EXPLAB-2414: expId=1, conditions 1..4, группы A=[1,3], B=[2,4], shares A=0..5000, B=5000..10000", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(loadConfig(flow, config));
                    //shouldBeConfigLoaded(response);
                })
                .step("Выполняем REST split в диапазоне группы A для X1..X4", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitForGroupA));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSplittingResultsSize(response, 4);
                    assertExplab2414GroupAResponse(response);
                })
                .step("Выполняем REST split в диапазоне группы B только для X2", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitForGroupB));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSplittingResultsSize(response, 1);
                    assertExplab2414GroupBResponse(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2414-02. pre-calculate сохраняет связи, REST возвращает только фактически сработавшую группу")
    void precalcShouldKeepOnlyMinConditionInsideSameGroupAndKeepAnotherGroup() {
        long version = SplitterVersionProvider.next();
        String uniquePrefix = "explab-2414-" + version;
        LoadConfigRequestDto config = explab2414Config(version);
        SplitterPrecalcRequestDto precalcRequest = precalcRequest(uniquePrefix);
        SplitRequestDto splitForGroupA = splitRequestAfterPrecalcForGroupA(uniquePrefix);
        SplitRequestDto splitForGroupB = splitRequestAfterPrecalcForGroupB(uniquePrefix);

        getFlowWithRest()
                .step("Загружаем config EXPLAB-2414: expId=1, conditions 1..4, группы A=[1,3], B=[2,4], shares A=0..5000, B=5000..10000", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(loadConfig(flow, config));
                    shouldBeConfigLoaded(response);
                })
                .step("Выполняем pre-calculate для X1..X4", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(calculatePreliminary(flow, precalcRequest));
                    shouldHaveJsonBody(response);
                    shouldHaveResponseId(response);
                    shouldHaveSoConfigVersion(response, SO_CONFIG_VERSION);
                })
                .step("Выполняем split группы A по тем же uniqueConfigurationId с runtime-параметрами, которые сами условия не матчят", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitForGroupA));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSplittingResultsSize(response, 4);
                    assertExplab2414GroupAResponse(response);
                })
                .step("Выполняем split группы B по предрассчитанной связи X2", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitForGroupB));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSplittingResultsSize(response, 1);
                    assertExplab2414GroupBResponse(response);
                })
                .run();
    }

    private static LoadConfigRequestDto explab2414Config(long version) {
        ExperimentDto experiment = ExperimentDto.builder()
                .id(EXP_ID)
                .purpose("DCG")
                .salt(SALT)
                .objectSelectConditions(List.of(
                        condition(1, "x2414Condition1"),
                        condition(2, "x2414Condition2"),
                        condition(3, "x2414Condition3"),
                        condition(4, "x2414Condition4")
                ))
                .groups(List.of(
                        group("A", 0, 5000,
                                result(1, "A_CONDITION_1"),
                                result(3, "A_CONDITION_3")),
                        group("B", 5000, 10000,
                                result(2, "B_CONDITION_2"),
                                result(4, "B_CONDITION_4"))
                ))
                .build();

        return LoadConfigRequestDto.builder()
                .messageId(UUID.randomUUID().toString())
                .requestId(UUID.randomUUID().toString())
                .configVersion(version)
                .forceConfigLoad(true)
                .splittingPointCode("MAPPER")
                .splittingConfig(SplittingConfigDto.builder()
                        .experiments(List.of(experiment))
                        .build())
                .build();
    }

    private static ObjectSelectConditionDto condition(int id, String paramCode) {
        RuleDto rule = RuleDto.builder()
                .dataType("STRING")
                .paramCode(paramCode)
                .paramSource("SPLITTING_OBJECTS")
                .operatorCode("equal")
                .values(List.of("Y"))
                .build();

        return ObjectSelectConditionDto.builder()
                .id(id)
                .rules(List.of(List.of(rule)))
                .build();
    }

    private static GroupDto group(String code, int shareFrom, int shareTo, SplittingResultDto... results) {
        return GroupDto.builder()
                .code(code)
                .shares(List.of(ShareDto.builder()
                        .shareFrom(shareFrom)
                        .shareTo(shareTo)
                        .build()))
                .splittingResults(List.of(results))
                .build();
    }

    private static SplittingResultDto result(int conditionId, String marker) {
        return SplittingResultDto.builder()
                .conditionId(conditionId)
                .resultParams(List.of(
                        param("actionType", "1", "INTEGER"),
                        param("explab2414Marker", marker, "STRING")
                ))
                .build();
    }

    private static SplitRequestDto runtimeSplitRequestForGroupA() {
        return SplitRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .splittingId(SPLITTING_ID_GROUP_A)
                .requestParams(List.of())
                .splittingObjects(List.of(
                        splitObject(null, X1_OBJECT_ID,
                                splitParam("x2414Condition1", "Y"),
                                splitParam("x2414Condition3", "Y")),
                        splitObject(null, X2_OBJECT_ID,
                                splitParam("x2414Condition3", "Y"),
                                splitParam("x2414Condition4", "Y")),
                        splitObject(null, X3_OBJECT_ID,
                                splitParam("x2414Noise", "X3")),
                        splitObject(null, X4_OBJECT_ID,
                                splitParam("x2414Noise", "X4"))
                ))
                .build();
    }

    private static SplitRequestDto runtimeSplitRequestForGroupB() {
        return SplitRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .splittingId(SPLITTING_ID_GROUP_B)
                .requestParams(List.of())
                .splittingObjects(List.of(
                        splitObject(null, X2_OBJECT_ID,
                                splitParam("x2414Condition3", "Y"),
                                splitParam("x2414Condition4", "Y"))))
                .build();
    }

    private static SplitterPrecalcRequestDto precalcRequest(String uniquePrefix) {
        return SplitterPrecalcRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .soConfigVersion(SO_CONFIG_VERSION)
                .splittingObjects(List.of(
                        precalcObject(uniquePrefix, X1_OBJECT_ID,
                                precalcParam("x2414Condition1", "Y"),
                                precalcParam("x2414Condition3", "Y")),
                        precalcObject(uniquePrefix, X2_OBJECT_ID,
                                precalcParam("x2414Condition3", "Y"),
                                precalcParam("x2414Condition4", "Y")),
                        precalcObject(uniquePrefix, X3_OBJECT_ID,
                                precalcParam("x2414Noise", "X3")),
                        precalcObject(uniquePrefix, X4_OBJECT_ID,
                                precalcParam("x2414Noise", "X4"))
                ))
                .build();
    }

    private static SplitRequestDto splitRequestAfterPrecalcForGroupA(String uniquePrefix) {
        return SplitRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .splittingId(SPLITTING_ID_GROUP_A)
                .requestParams(List.of())
                .splittingObjects(List.of(
                        splitObject(uniqueId(uniquePrefix, X1_OBJECT_ID), X1_OBJECT_ID, splitParam("x2414RuntimeOnly", "X1")),
                        splitObject(uniqueId(uniquePrefix, X2_OBJECT_ID), X2_OBJECT_ID, splitParam("x2414RuntimeOnly", "X2")),
                        splitObject(uniqueId(uniquePrefix, X3_OBJECT_ID), X3_OBJECT_ID, splitParam("x2414RuntimeOnly", "X3")),
                        splitObject(uniqueId(uniquePrefix, X4_OBJECT_ID), X4_OBJECT_ID, splitParam("x2414RuntimeOnly", "X4"))
                ))
                .build();
    }

    private static SplitRequestDto splitRequestAfterPrecalcForGroupB(String uniquePrefix) {
        return SplitRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .splittingId(SPLITTING_ID_GROUP_B)
                .requestParams(List.of())
                .splittingObjects(List.of(
                        splitObject(uniqueId(uniquePrefix, X2_OBJECT_ID), X2_OBJECT_ID, splitParam("x2414RuntimeOnly", "X2"))))
                .build();
    }

    private static SplitterPrecalcObjectDto precalcObject(String uniquePrefix,
                                                          String objectId,
                                                          SplitterPrecalcParamDto... params) {
        return SplitterPrecalcObjectDto.builder()
                .uniqueConfigurationId(uniqueId(uniquePrefix, objectId))
                .objectParams(List.of(params))
                .build();
    }

    private static SplittingObjectDto splitObject(String uniqueConfigurationId, String objectId, ParamDto... params) {
        return SplittingObjectDto.builder()
                .uniqueConfigurationId(uniqueConfigurationId)
                .objectId(objectId)
                .objectParams(List.of(params))
                .build();
    }

    private static SplitterPrecalcParamDto precalcParam(String code, String value) {
        return new SplitterPrecalcParamDto(code, List.of(value), "STRING");
    }

    private static ParamDto splitParam(String code, String value) {
        return param(code, value, "STRING");
    }

    private static ParamDto param(String code, String value, String dataType) {
        return ParamDto.builder()
                .paramCode(code)
                .paramValues(List.of(value))
                .dataType(dataType)
                .build();
    }

    private static String uniqueId(String prefix, String objectId) {
        return prefix + "-" + objectId.substring(objectId.length() - 1);
    }

    private ValidatableResponseWrapper loadConfig(FlowWithRest flow, LoadConfigRequestDto request) {
        return flow.restCustomSteps().splitterSteps().loadConfig(request);
    }

    private ValidatableResponseWrapper split(FlowWithRest flow, SplitRequestDto request) {
        return flow.restCustomSteps().splitterSteps().split(request);
    }

    private ValidatableResponseWrapper calculatePreliminary(FlowWithRest flow, SplitterPrecalcRequestDto request) {
        return flow.restCustomSteps().splitterSteps().calculatePreliminary(request);
    }

    private static void assertExplab2414GroupAResponse(ValidatableResponseWrapper response) {
        // EXPLAB-2690: публичный REST ALL содержит только реально сработавшую группу.
        assertRuleHasExactlyExpectedBindings(response, X1_OBJECT_ID, "ALL", Set.of(binding(1, "A")));
        assertRuleHasExactlyExpectedBindings(response, X2_OBJECT_ID, "ALL", Set.of(binding(3, "A")));
        assertRuleHasExpectedConditionMarkers(response, X1_OBJECT_ID, "ALL", 1, Set.of("A_CONDITION_1"));
        assertRuleHasExpectedConditionMarkers(response, X2_OBJECT_ID, "ALL", 3, Set.of("A_CONDITION_3"));
        assertRuleHasConsistentConditionMarkers(response, X1_OBJECT_ID, "MAIN");
        assertRuleHasConsistentConditionMarkers(response, X2_OBJECT_ID, "MAIN");
        assertEmptyObjectResults(response, X3_OBJECT_ID);
        assertEmptyObjectResults(response, X4_OBJECT_ID);
    }

    private static void assertExplab2414GroupBResponse(ValidatableResponseWrapper response) {
        assertRuleHasExactlyExpectedBindings(response, X2_OBJECT_ID, "ALL", Set.of(binding(4, "B")));
        assertRuleHasExpectedConditionMarkers(response, X2_OBJECT_ID, "ALL", 4, Set.of("B_CONDITION_4"));
        assertRuleHasConsistentConditionMarkers(response, X2_OBJECT_ID, "MAIN");
    }

    private static Binding binding(int conditionId, String group) {
        return new Binding(conditionId, EXP_ID, group);
    }

    private static void assertRuleHasExactlyExpectedBindings(ValidatableResponseWrapper response,
                                                             String objectId,
                                                             String ruleCode,
                                                             Set<Binding> expectedBindings) {
        List<Binding> actualBindingsWithDuplicates = extractBindings(response, objectId, ruleCode);
        Set<Binding> actualBindings = new LinkedHashSet<>(actualBindingsWithDuplicates);

        assertEquals(actualBindingsWithDuplicates.size(), actualBindings.size(),
                "В " + ruleCode + " есть дубли связки expId/conditionId/expGroup для objectId=" + objectId + body(response));
        assertEquals(expectedBindings, actualBindings,
                "В " + ruleCode + " есть лишние или отсутствующие привязки condition/group для objectId=" + objectId
                        + body(response));
    }

    private static void assertRuleHasExpectedConditionMarkers(ValidatableResponseWrapper response,
                                                              String objectId,
                                                              String ruleCode,
                                                              int conditionId,
                                                              Set<String> expectedMarkers) {
        JsonNode resultExp = findResultExpByConditionId(response, objectId, ruleCode, conditionId);
        assertResultExpHasExactlyMarkers(response, objectId, ruleCode, resultExp, expectedMarkers);
    }

    private static void assertRuleHasConsistentConditionMarkers(ValidatableResponseWrapper response,
                                                                String objectId,
                                                                String ruleCode) {
        JsonNode root = json(response);
        JsonNode objectResult = findObject(root, objectId);
        JsonNode ruleResult = findObjectResultByRuleCode(objectResult, ruleCode);
        JsonNode resultExps = ruleResult.path("resultExps");

        assertTrue(resultExps.isArray(),
                "Ожидали массив resultExps в " + ruleCode + " для objectId=" + objectId + body(response));
        assertTrue(!resultExps.isEmpty(),
                "Ожидали непустой " + ruleCode + " для objectId=" + objectId + body(response));

        for (JsonNode resultExp : resultExps) {
            int conditionId = resultExp.path("conditionId").asInt(Integer.MIN_VALUE);
            assertResultExpHasExactlyMarkers(
                    response,
                    objectId,
                    ruleCode,
                    resultExp,
                    expectedMarkersForCondition(conditionId)
            );
        }
    }

    private static JsonNode findResultExpByConditionId(ValidatableResponseWrapper response,
                                                       String objectId,
                                                       String ruleCode,
                                                       int conditionId) {
        JsonNode root = json(response);
        JsonNode objectResult = findObject(root, objectId);
        JsonNode ruleResult = findObjectResultByRuleCode(objectResult, ruleCode);
        JsonNode resultExps = ruleResult.path("resultExps");

        assertTrue(resultExps.isArray(),
                "Ожидали массив resultExps в " + ruleCode + " для objectId=" + objectId + body(response));

        JsonNode found = null;
        int count = 0;

        for (JsonNode resultExp : resultExps) {
            if (conditionId == resultExp.path("conditionId").asInt(Integer.MIN_VALUE)) {
                found = resultExp;
                count++;
            }
        }

        assertEquals(1, count,
                "Ожидали ровно один resultExp с conditionId=" + conditionId
                        + " в " + ruleCode + " для objectId=" + objectId + body(response));

        return found;
    }

    private static void assertResultExpHasExactlyMarkers(ValidatableResponseWrapper response,
                                                         String objectId,
                                                         String ruleCode,
                                                         JsonNode resultExp,
                                                         Set<String> expectedMarkers) {
        List<String> actualMarkersWithDuplicates = extractExplab2414Markers(resultExp);
        Set<String> actualMarkers = new LinkedHashSet<>(actualMarkersWithDuplicates);

        assertEquals(actualMarkersWithDuplicates.size(), actualMarkers.size(),
                "В " + ruleCode + " есть дубли explab2414Marker для objectId=" + objectId
                        + ", conditionId=" + resultExp.path("conditionId").asInt(Integer.MIN_VALUE)
                        + body(response));

        assertEquals(expectedMarkers, actualMarkers,
                "В " + ruleCode + " некорректные explab2414Marker для objectId=" + objectId
                        + ", conditionId=" + resultExp.path("conditionId").asInt(Integer.MIN_VALUE)
                        + ". expected=" + expectedMarkers + ", actual=" + actualMarkers
                        + body(response));
    }

    private static List<String> extractExplab2414Markers(JsonNode resultExp) {
        JsonNode groupResultParams = resultExp.path("groupResultParams");

        assertTrue(groupResultParams.isArray(),
                "Ожидали массив groupResultParams для conditionId="
                        + resultExp.path("conditionId").asInt(Integer.MIN_VALUE));

        List<String> markers = new ArrayList<>();

        for (JsonNode param : groupResultParams) {
            if ("explab2414Marker".equals(param.path("paramCode").asText(null))) {
                JsonNode values = param.path("paramValues");

                assertTrue(values.isArray(),
                        "Ожидали массив paramValues для explab2414Marker, conditionId="
                                + resultExp.path("conditionId").asInt(Integer.MIN_VALUE));

                for (JsonNode value : values) {
                    markers.add(value.asText());
                }
            }
        }

        return markers;
    }

    private static Set<String> expectedMarkersForCondition(int conditionId) {
        return switch (conditionId) {
            case 1 -> Set.of("A_CONDITION_1");
            case 2 -> Set.of("B_CONDITION_2");
            case 3 -> Set.of("A_CONDITION_3");
            case 4 -> Set.of("B_CONDITION_4");
            default -> fail("Неизвестный conditionId для EXPLAB-2414: " + conditionId);
        };
    }

    private static List<Binding> extractBindings(ValidatableResponseWrapper response, String objectId, String ruleCode) {
        JsonNode root = json(response);
        JsonNode objectResult = findObject(root, objectId);
        JsonNode ruleResult = findObjectResultByRuleCode(objectResult, ruleCode);
        JsonNode resultExps = ruleResult.path("resultExps");
        assertTrue(resultExps.isArray(),
                "Ожидали массив resultExps в " + ruleCode + " для objectId=" + objectId + body(response));

        List<Binding> bindings = new ArrayList<>();
        for (JsonNode resultExp : resultExps) {
            bindings.add(new Binding(
                    resultExp.path("conditionId").asInt(Integer.MIN_VALUE),
                    resultExp.path("expId").asInt(Integer.MIN_VALUE),
                    resultExp.path("expGroup").asText(null)
            ));
        }
        return bindings;
    }

    private static void assertEmptyObjectResults(ValidatableResponseWrapper response, String objectId) {
        JsonNode objectResult = findObject(json(response), objectId);
        JsonNode objectResults = objectResult.path("objectResults");
        boolean empty = objectResults.isMissingNode()
                || objectResults.isNull()
                || (objectResults.isArray() && objectResults.isEmpty());
        assertTrue(empty, "Ожидали пустой objectResults для objectId=" + objectId + body(response));
    }

    private static JsonNode findObject(JsonNode root, String objectId) {
        JsonNode splittingResults = root.path("splittingResults");
        assertTrue(splittingResults.isArray(), "Поле splittingResults должно быть массивом");
        for (JsonNode result : splittingResults) {
            if (objectId.equals(result.path("objectId").asText(null))) {
                return result;
            }
        }
        JsonNode missing = null;
        assertNotNull(missing, "Не найден objectId=" + objectId + " в splittingResults");
        return missing;
    }

    private static JsonNode findObjectResultByRuleCode(JsonNode objectResult, String ruleCode) {
        JsonNode objectResults = objectResult.path("objectResults");
        assertTrue(objectResults.isArray(), "Поле objectResults должно быть массивом для objectId="
                + objectResult.path("objectId").asText(null));
        for (JsonNode result : objectResults) {
            if (ruleCode.equals(result.path("ruleCode").asText(null))) {
                return result;
            }
        }
        JsonNode missing = null;
        assertNotNull(missing, "Не найден ruleCode=" + ruleCode
                + " для objectId=" + objectResult.path("objectId").asText(null));
        return missing;
    }

    private static JsonNode json(ValidatableResponseWrapper response) {
        SplitterResponseSnapshot snapshot = SplitterResponseReader.snapshot(response);
        return snapshot.requireJsonBody("Ожидали JSON body для проверки EXPLAB-2414");
    }

    private static String body(ValidatableResponseWrapper response) {
        return "\nResponse body:\n" + SplitterResponseReader.snapshot(response).prettyBody();
    }

    private record Binding(int conditionId, int expId, String expGroup) {
    }
}
