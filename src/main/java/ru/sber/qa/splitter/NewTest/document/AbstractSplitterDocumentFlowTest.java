package ru.sber.qa.splitter.NewTest.document;

import com.fasterxml.jackson.databind.JsonNode;
import dto.splitter.common.ParamDto;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.GroupDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ObjectSelectConditionDto;
import dto.splitter.config.RuleDto;
import dto.splitter.config.ShareDto;
import dto.splitter.config.SplittingConfigDto;
import dto.splitter.config.SplittingResultDto;
import dto.splitter.split.SplitRequestDto;
import dto.splitter.split.SplittingObjectDto;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.NewTest.AbstractNewSplitterFlowTest;
import util.splittercheck.SplitterResponseReader;
import util.splittercheck.SplitterResponseSnapshot;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static util.TestAssertions.assertEquals;
import static util.TestAssertions.assertFalse;
import static util.TestAssertions.assertNotNull;
import static util.TestAssertions.assertTrue;
import static util.TestAssertions.fail;
import static util.SplitterPrecalcAssertions.shouldBe200;
import static util.SplitterPrecalcAssertions.shouldBeConfigLoaded;

abstract class AbstractSplitterDocumentFlowTest extends AbstractNewSplitterFlowTest {

    static final String SPLITTING_POINT = "MAPPER";
    static final String DEFAULT_SPLITTING_ID = "700";
    static final String MATCHING_OBJECT_ID = "1f53341b-aaa9-4f28-ae70-4bbb91c3c8c2";
    static final String SECOND_OBJECT_ID = "99e2a939-ea9a-404f-9d09-5dca15d14966";
    static final String THIRD_OBJECT_ID = "33333333-3333-3333-3333-333333333333";
    static final String NEGATIVE_OBJECT_ID = "44444444-4444-4444-4444-444444444444";

    ValidatableResponseWrapper load(FlowWithRest flow, LoadConfigRequestDto request) {
        return flow.restCustomSteps().splitterSteps().loadConfig(request);
    }

    ValidatableResponseWrapper split(FlowWithRest flow, SplitRequestDto request) {
        return flow.restCustomSteps().splitterSteps().split(request);
    }

    void loadConfigStep(FlowWithRest flow, LoadConfigRequestDto config) {
        shouldBeConfigLoaded(shouldBe200(load(flow, config)));
    }

    LoadConfigRequestDto config(long version, ExperimentDto... experiments) {
        return LoadConfigRequestDto.builder()
                .messageId(UUID.randomUUID().toString())
                .requestId(UUID.randomUUID().toString())
                .configVersion(version)
                .forceConfigLoad(true)
                .splittingPointCode(SPLITTING_POINT)
                .splittingConfig(SplittingConfigDto.builder()
                        .experiments(Arrays.asList(experiments))
                        .build())
                .build();
    }

    ExperimentDto experiment(int id, String salt, List<ObjectSelectConditionDto> conditions, List<GroupDto> groups) {
        return ExperimentDto.builder()
                .id(id)
                .purpose("DCG")
                .salt(salt)
                .objectSelectConditions(conditions)
                .groups(groups)
                .build();
    }

    ExperimentDto layeredExperiment(int id,
                                    String salt,
                                    int layerId,
                                    int layerPriority,
                                    List<ObjectSelectConditionDto> conditions,
                                    List<GroupDto> groups) {
        return ExperimentDto.builder()
                .id(id)
                .purpose("DCG")
                .salt(salt)
                .layerId(layerId)
                .layerPriority(layerPriority)
                .objectSelectConditions(conditions)
                .groups(groups)
                .build();
    }

    ObjectSelectConditionDto condition(int id, List<List<RuleDto>> rules) {
        return ObjectSelectConditionDto.builder()
                .id(id)
                .rules(rules)
                .build();
    }

    ObjectSelectConditionDto objectParamEqualsCondition(int id, String paramCode, String value, String dataType) {
        return condition(id, List.of(List.of(rule(dataType, paramCode, "SPLITTING_OBJECTS", "equal", value))));
    }

    ObjectSelectConditionDto objectParamInCondition(int id, String paramCode, String dataType, String... values) {
        return condition(id, List.of(List.of(rule(dataType, paramCode, "SPLITTING_OBJECTS", "in", values))));
    }

    RuleDto rule(String dataType, String paramCode, String paramSource, String operatorCode, String... values) {
        return RuleDto.builder()
                .dataType(dataType)
                .paramCode(paramCode)
                .paramSource(paramSource)
                .operatorCode(operatorCode)
                .values(values == null ? null : List.of(values))
                .build();
    }

    GroupDto fullRangeGroup(String code, int conditionId, String actionType) {
        return group(code, List.of(share(0, 10000)), conditionId, actionType);
    }

    GroupDto group(String code, int shareFrom, int shareTo, int conditionId, String actionType) {
        return group(code, List.of(share(shareFrom, shareTo)), conditionId, actionType);
    }

    GroupDto group(String code, List<ShareDto> shares, int conditionId, String actionType) {
        return GroupDto.builder()
                .code(code)
                .shares(shares)
                .splittingResults(List.of(result(conditionId, actionType)))
                .build();
    }

    GroupDto groupWithoutResult(String code, int shareFrom, int shareTo) {
        return GroupDto.builder()
                .code(code)
                .shares(List.of(share(shareFrom, shareTo)))
                .splittingResults(List.of())
                .build();
    }

    ShareDto share(int from, int to) {
        return ShareDto.builder()
                .shareFrom(from)
                .shareTo(to)
                .build();
    }

    SplittingResultDto result(int conditionId, String actionType) {
        return SplittingResultDto.builder()
                .conditionId(conditionId)
                .resultParams(List.of(param("actionType", actionType, "INTEGER")))
                .build();
    }

    ParamDto param(String code, String value, String dataType) {
        return ParamDto.builder()
                .paramCode(code)
                .paramValues(List.of(value))
                .dataType(dataType)
                .build();
    }

    SplittingObjectDto object(String objectId, ParamDto... params) {
        return SplittingObjectDto.builder()
                .objectId(objectId)
                .objectParams(Arrays.asList(params))
                .build();
    }

    SplittingObjectDto objectWithUniqueId(String uniqueConfigurationId, String objectId, ParamDto... params) {
        return SplittingObjectDto.builder()
                .uniqueConfigurationId(uniqueConfigurationId)
                .objectId(objectId)
                .objectParams(Arrays.asList(params))
                .build();
    }

    SplitRequestDto splitRequest(String splittingId, SplittingObjectDto... objects) {
        return SplitRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .splittingId(splittingId)
                .requestParams(List.of())
                .splittingObjects(Arrays.asList(objects))
                .build();
    }

    JsonNode json(ValidatableResponseWrapper response, String assertionContext) {
        SplitterResponseSnapshot snapshot = SplitterResponseReader.snapshot(response);
        return snapshot.requireJsonBody(assertionContext);
    }

    String body(ValidatableResponseWrapper response) {
        return "\nResponse body:\n" + SplitterResponseReader.snapshot(response).prettyBody();
    }

    void assertConfigVersion(ValidatableResponseWrapper response, long version) {
        JsonNode root = json(response, "Ожидали JSON body для проверки версии конфига");
        assertEquals(version, root.path("splittingConfigVersion").asLong(Long.MIN_VALUE), body(response));
    }

    void assertSplittingResultsSize(ValidatableResponseWrapper response, int expectedSize) {
        JsonNode results = json(response, "Ожидали JSON body для проверки splittingResults").path("splittingResults");
        assertTrue(results.isArray(), "splittingResults должен быть массивом" + body(response));
        assertEquals(expectedSize, results.size(), body(response));
    }

    void assertObjectHasMainAndAll(ValidatableResponseWrapper response, String objectId) {
        JsonNode object = findObject(response, objectId);
        assertNotNull(findRuleResult(object, "MAIN", false), "Не найден MAIN для objectId=" + objectId + body(response));
        assertNotNull(findRuleResult(object, "ALL", false), "Не найден ALL для objectId=" + objectId + body(response));
    }

    void assertObjectResultsEmpty(ValidatableResponseWrapper response, String objectId) {
        JsonNode object = findObject(response, objectId);
        JsonNode objectResults = object.path("objectResults");
        boolean empty = objectResults.isMissingNode()
                || objectResults.isNull()
                || (objectResults.isArray() && objectResults.isEmpty());
        assertTrue(empty, "Ожидали пустой objectResults для objectId=" + objectId + body(response));
    }


    /**
     * Legacy-имя сохранено для совместимости существующих сценариев.
     * После EXPLAB-2690 объект без выбранного MAIN должен выглядеть как несвязанный:
     * в публичном REST-ответе не допускаются ни диагностический ALL, ни технический пустой MAIN.
     */
    void assertObjectHasAllButNoMainAssignment(ValidatableResponseWrapper response, String objectId) {
        assertObjectResultsEmpty(response, objectId);
        JsonNode object = findObject(response, objectId);
        assertTrue(findRuleResult(object, "MAIN", false) == null,
                "Не ожидали MAIN для objectId=" + objectId + body(response));
        assertTrue(findRuleResult(object, "ALL", false) == null,
                "Не ожидали ALL для objectId=" + objectId + body(response));
    }

    void assertMainExp(ValidatableResponseWrapper response, String objectId, long expectedExpId) {
        JsonNode main = findRuleResult(findObject(response, objectId), "MAIN", true);
        JsonNode resultExps = main.path("resultExps");
        assertTrue(resultExps.isArray() && !resultExps.isEmpty(), "MAIN.resultExps должен быть непустым" + body(response));
        assertEquals(expectedExpId, resultExps.get(0).path("expId").asLong(Long.MIN_VALUE), body(response));
    }

    void assertMainGroup(ValidatableResponseWrapper response, String objectId, String expectedGroup) {
        JsonNode main = findRuleResult(findObject(response, objectId), "MAIN", true);
        JsonNode resultExps = main.path("resultExps");
        assertTrue(resultExps.isArray() && !resultExps.isEmpty(), "MAIN.resultExps должен быть непустым" + body(response));
        assertEquals(expectedGroup, resultExps.get(0).path("expGroup").asText(null), body(response));
    }

    void assertMainActionType(ValidatableResponseWrapper response, String objectId, String expectedActionType) {
        JsonNode mainExp = firstMainExp(response, objectId);
        assertEquals(expectedActionType, findResultParamValue(mainExp, "actionType"), body(response));
    }

    void assertMainLayer(ValidatableResponseWrapper response, String objectId, int expectedLayerId, int expectedLayerPriority) {
        JsonNode mainExp = firstMainExp(response, objectId);
        assertEquals(expectedLayerId, mainExp.path("layerId").asInt(Integer.MIN_VALUE), body(response));
        assertEquals(expectedLayerPriority, mainExp.path("layerPriority").asInt(Integer.MIN_VALUE), body(response));
    }

    void assertMainSpreadValue(ValidatableResponseWrapper response, String objectId, int expectedSpreadValue) {
        JsonNode mainExp = firstMainExp(response, objectId);
        assertEquals(expectedSpreadValue, mainExp.path("spreadValue").asInt(Integer.MIN_VALUE), body(response));
    }

    void assertFiltered(ValidatableResponseWrapper response, String objectId, String expectedValue) {
        JsonNode object = findObject(response, objectId);
        JsonNode flags = object.path("objectFlags");
        assertTrue(flags.isArray(), "objectFlags должен быть массивом" + body(response));
        for (JsonNode flag : flags) {
            if ("filtered".equals(flag.path("code").asText(null))) {
                assertEquals(expectedValue, flag.path("value").asText(null), body(response));
                return;
            }
        }
        fail("Не найден filtered flag для objectId=" + objectId + body(response));
    }

    void assertAllRuleHasExpIdsExactly(ValidatableResponseWrapper response, String objectId, Long... expectedExpIds) {
        Set<Long> expected = new LinkedHashSet<>(Arrays.asList(expectedExpIds));
        Set<Long> actual = expIds(response, objectId, "ALL");
        assertEquals(expected, actual, "Неверный набор expId в ALL для objectId=" + objectId + body(response));
    }


    void assertAllExpGroup(ValidatableResponseWrapper response, String objectId, long expId, String expectedGroup) {
        JsonNode resultExp = findAllExp(response, objectId, expId);
        JsonNode expGroup = resultExp.path("expGroup");
        if (expectedGroup == null) {
            assertTrue(expGroup.isMissingNode() || expGroup.isNull(),
                    "Ожидали expGroup=null для expId=" + expId + body(response));
        } else {
            assertEquals(expectedGroup, expGroup.asText(null),
                    "Неверная expGroup для expId=" + expId + body(response));
        }
    }

    void assertAllExpActionType(ValidatableResponseWrapper response, String objectId, long expId, String expectedActionType) {
        JsonNode resultExp = findAllExp(response, objectId, expId);
        assertEquals(expectedActionType, findResultParamValue(resultExp, "actionType"),
                "Неверный actionType для expId=" + expId + body(response));
    }

    void assertRuleHasExpIdsExactly(ValidatableResponseWrapper response, String objectId, String ruleCode, Long... expectedExpIds) {
        Set<Long> expected = new LinkedHashSet<>(Arrays.asList(expectedExpIds));
        Set<Long> actual = expIds(response, objectId, ruleCode);
        assertEquals(expected, actual, "Неверный набор expId в " + ruleCode + " для objectId=" + objectId + body(response));
    }

    void assertRuleResultSize(ValidatableResponseWrapper response, String objectId, String ruleCode, int expectedSize) {
        JsonNode ruleResult = findRuleResult(findObject(response, objectId), ruleCode, true);
        JsonNode resultExps = ruleResult.path("resultExps");
        assertTrue(resultExps.isArray(), "resultExps должен быть массивом" + body(response));
        assertEquals(expectedSize, resultExps.size(), body(response));
    }

    void assertAnyExpHasAlternativeFlag(ValidatableResponseWrapper response, String objectId, long expId, String expectedValue) {
        for (JsonNode resultExp : allRuleExps(response, objectId)) {
            if (resultExp.path("expId").asLong(Long.MIN_VALUE) == expId) {
                assertAlternativeFlag(resultExp, expectedValue, response);
                return;
            }
        }
        fail("Не найден expId=" + expId + " в ALL для objectId=" + objectId + body(response));
    }

    void assertMainAlternativeFlag(ValidatableResponseWrapper response, String objectId, String expectedValue) {
        assertAlternativeFlag(firstMainExp(response, objectId), expectedValue, response);
    }

    int spread(String salt, String splittingId) {
        int hash = murmur3_32((salt + "|" + splittingId).getBytes(StandardCharsets.UTF_8));
        int value = hash % 10000;
        return value < 0 ? value + 10000 : value;
    }

    JsonNode findObject(ValidatableResponseWrapper response, String objectId) {
        JsonNode root = json(response, "Ожидали JSON body для поиска objectId=" + objectId);
        JsonNode splittingResults = root.path("splittingResults");
        assertTrue(splittingResults.isArray(), "splittingResults должен быть массивом" + body(response));
        JsonNode found = null;
        for (JsonNode result : splittingResults) {
            if (Objects.equals(objectId, result.path("objectId").asText(null))) {
                found = result;
                break;
            }
        }
        assertNotNull(found, "Не найден objectId=" + objectId + body(response));
        return found;
    }

    JsonNode findRuleResult(JsonNode object, String ruleCode, boolean failIfMissing) {
        JsonNode objectResults = object.path("objectResults");
        if (!objectResults.isArray()) {
            if (failIfMissing) {
                assertTrue(false,
                        "objectResults должен быть массивом для objectId=" + object.path("objectId").asText(null));
            }
            return null;
        }
        JsonNode found = null;
        for (JsonNode result : objectResults) {
            if (Objects.equals(ruleCode, result.path("ruleCode").asText(null))) {
                found = result;
                break;
            }
        }
        if (failIfMissing) {
            assertNotNull(found,
                    "Не найден ruleCode=" + ruleCode + " для objectId=" + object.path("objectId").asText(null));
        }
        return found;
    }

    private JsonNode firstMainExp(ValidatableResponseWrapper response, String objectId) {
        JsonNode main = findRuleResult(findObject(response, objectId), "MAIN", true);
        JsonNode resultExps = main.path("resultExps");
        assertTrue(resultExps.isArray() && !resultExps.isEmpty(), "MAIN.resultExps должен быть непустым" + body(response));
        return resultExps.get(0);
    }

    private Set<Long> expIds(ValidatableResponseWrapper response, String objectId, String ruleCode) {
        JsonNode ruleResult = findRuleResult(findObject(response, objectId), ruleCode, true);
        JsonNode resultExps = ruleResult.path("resultExps");
        assertTrue(resultExps.isArray(), "resultExps должен быть массивом" + body(response));
        Set<Long> result = new LinkedHashSet<>();
        for (JsonNode resultExp : resultExps) {
            result.add(resultExp.path("expId").asLong(Long.MIN_VALUE));
        }
        return result;
    }


    private JsonNode findAllExp(ValidatableResponseWrapper response, String objectId, long expId) {
        for (JsonNode resultExp : allRuleExps(response, objectId)) {
            if (resultExp.path("expId").asLong(Long.MIN_VALUE) == expId) {
                return resultExp;
            }
        }
        fail("Не найден expId=" + expId + " в ALL для objectId=" + objectId + body(response));
        return null;
    }

    private Iterable<JsonNode> allRuleExps(ValidatableResponseWrapper response, String objectId) {
        JsonNode all = findRuleResult(findObject(response, objectId), "ALL", true);
        JsonNode resultExps = all.path("resultExps");
        assertTrue(resultExps.isArray(), "ALL.resultExps должен быть массивом" + body(response));
        return resultExps;
    }

    private String findResultParamValue(JsonNode resultExp, String paramCode) {
        JsonNode params = resultExp.path("groupResultParams");
        assertTrue(params.isArray(), "groupResultParams должен быть массивом");
        for (JsonNode param : params) {
            if (paramCode.equals(param.path("paramCode").asText(null))) {
                JsonNode values = param.path("paramValues");
                assertTrue(values.isArray() && !values.isEmpty(), "paramValues должен быть непустым");
                return values.get(0).asText(null);
            }
        }
        fail("Не найден groupResultParams.paramCode=" + paramCode);
        return null;
    }

    private void assertAlternativeFlag(JsonNode resultExp, String expectedValue, ValidatableResponseWrapper response) {
        JsonNode flags = resultExp.path("expFlags");
        assertFalse(flags.isMissingNode(), "expFlags отсутствует" + body(response));
        assertTrue(flags.isArray(), "expFlags должен быть массивом" + body(response));
        for (JsonNode flag : flags) {
            if ("isAlternative".equals(flag.path("code").asText(null))) {
                assertEquals(expectedValue, flag.path("value").asText(null), body(response));
                return;
            }
        }
        fail("Не найден isAlternative flag" + body(response));
    }

    private int murmur3_32(byte[] data) {
        int length = data.length;
        int h1 = 0;
        final int c1 = 0xcc9e2d51;
        final int c2 = 0x1b873593;
        int roundedEnd = length & 0xfffffffc;

        for (int i = 0; i < roundedEnd; i += 4) {
            int k1 = (data[i] & 0xff)
                    | ((data[i + 1] & 0xff) << 8)
                    | ((data[i + 2] & 0xff) << 16)
                    | (data[i + 3] << 24);
            k1 *= c1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= c2;

            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        int k1 = 0;
        switch (length & 0x03) {
            case 3:
                k1 = (data[roundedEnd + 2] & 0xff) << 16;
            case 2:
                k1 |= (data[roundedEnd + 1] & 0xff) << 8;
            case 1:
                k1 |= data[roundedEnd] & 0xff;
                k1 *= c1;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= c2;
                h1 ^= k1;
            default:
                break;
        }

        h1 ^= length;
        h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;
        return h1;
    }
}


