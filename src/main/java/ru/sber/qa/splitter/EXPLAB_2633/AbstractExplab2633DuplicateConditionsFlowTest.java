package ru.sber.qa.splitter.EXPLAB_2633;

import com.fasterxml.jackson.databind.JsonNode;
import dto.splitter.common.ParamDto;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ObjectSelectConditionDto;
import dto.splitter.config.RuleDto;
import dto.splitter.split.SplitRequestDto;
import dto.splitter.split.SplittingObjectDto;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.analytictests.common.AbstractAnalyticSplitterFlowTest;
import util.splittercheck.SplitterResponseReader;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static util.TestAssertions.assertEquals;
import static util.TestAssertions.assertFalse;
import static util.TestAssertions.assertTrue;
import static util.TestAssertions.fail;

/**
 * Общая база для проверки повторяющихся paramCode в objectSelectConditions.rules.
 * Тесты намеренно остаются REST-only и DTO-only: конфигурация и split-request строятся
 * через DTO проекта, без inline JSON.
 */
public abstract class AbstractExplab2633DuplicateConditionsFlowTest extends AbstractAnalyticSplitterFlowTest {

    protected static final String OBJECT_ID = "26330000-0000-0000-0000-000000000001";
    protected static final String SECOND_OBJECT_ID = "26330000-0000-0000-0000-000000000002";
    protected static final String NEGATIVE_OBJECT_ID = "26330000-0000-0000-0000-000000000099";

    protected LoadConfigRequestDto duplicateRulesConfig(long version,
                                                        int expId,
                                                        String salt,
                                                        List<List<RuleDto>> rules) {
        ObjectSelectConditionDto condition = condition(1, rules);
        ExperimentDto experiment = experiment(expId, salt, List.of(condition), List.of(fullRangeGroup("A", 1, "0")));
        return config(version, experiment);
    }

    protected SplitRequestDto splitRequestWithRequestParams(String splittingId,
                                                            List<ParamDto> requestParams,
                                                            SplittingObjectDto... objects) {
        return SplitRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .splittingId(splittingId)
                .requestParams(requestParams)
                .splittingObjects(Arrays.asList(objects))
                .build();
    }

    protected List<List<RuleDto>> andGroup(RuleDto... rules) {
        return List.of(List.of(rules));
    }

    @SafeVarargs
    protected final List<List<RuleDto>> orGroups(List<RuleDto>... groups) {
        return List.of(groups);
    }

    protected List<RuleDto> groupRules(RuleDto... rules) {
        return List.of(rules);
    }

    protected RuleDto objectIntRule(String paramCode, String operatorCode, String... values) {
        return rule("INTEGER", paramCode, "SPLITTING_OBJECTS", operatorCode, values);
    }

    protected RuleDto objectNumberRule(String paramCode, String operatorCode, String... values) {
        return rule("NUMBER", paramCode, "SPLITTING_OBJECTS", operatorCode, values);
    }

    protected RuleDto objectStringRule(String paramCode, String operatorCode, String... values) {
        return rule("STRING", paramCode, "SPLITTING_OBJECTS", operatorCode, values);
    }

    protected RuleDto objectDateRule(String paramCode, String operatorCode, String... values) {
        return rule("DATE", paramCode, "SPLITTING_OBJECTS", operatorCode, values);
    }

    protected RuleDto objectDateTimeRule(String paramCode, String operatorCode, String... values) {
        return rule("DATETIME", paramCode, "SPLITTING_OBJECTS", operatorCode, values);
    }

    protected RuleDto objectBooleanRule(String paramCode, String operatorCode, String... values) {
        return rule("BOOLEAN", paramCode, "SPLITTING_OBJECTS", operatorCode, values);
    }

    protected ParamDto intParam(String paramCode, String value) {
        return param(paramCode, value, "INTEGER");
    }

    protected ParamDto numberParam(String paramCode, String value) {
        return param(paramCode, value, "NUMBER");
    }

    protected ParamDto stringParam(String paramCode, String value) {
        return param(paramCode, value, "STRING");
    }

    protected ParamDto dateParam(String paramCode, String value) {
        return param(paramCode, value, "DATE");
    }

    protected ParamDto dateTimeParam(String paramCode, String value) {
        return param(paramCode, value, "DATETIME");
    }

    protected ParamDto booleanParam(String paramCode, String value) {
        return param(paramCode, value, "BOOLEAN");
    }

    protected List<ParamDto> objectParams(ParamDto... params) {
        return List.of(params);
    }

    protected void assertSuccessfulSplitResponse(ValidatableResponseWrapper response, long version) {
        assertConfigVersion(response, version);
        assertSplittingResultsSize(response, 1);
    }

    protected void assertObjectMatchedExperiment(ValidatableResponseWrapper response, String objectId, long expId) {
        assertObjectHasMainAndAll(response, objectId);
        assertFiltered(response, objectId, "false");
        assertRuleResultSize(response, objectId, "MAIN", 1);
        assertRuleResultSize(response, objectId, "ALL", 1);
        assertMainExp(response, objectId, expId);
        assertMainGroup(response, objectId, "A");
        assertMainConditionId(response, objectId, 1);
        assertMainActionType(response, objectId, "0");
        assertAllRuleHasExpIdsExactly(response, objectId, expId);
        assertAllExpGroup(response, objectId, expId, "A");
        assertAllExpConditionId(response, objectId, expId, 1);
        assertAllExpActionType(response, objectId, expId, "0");
        assertExperimentAppearsOnlyOnceInObjectResults(response, objectId, expId);
    }

    protected void assertObjectDidNotMatchExperiment(ValidatableResponseWrapper response, String objectId, long expId) {
        assertExperimentAbsentInObjectResults(response, objectId, expId);
        assertObjectResultsEmpty(response, objectId);
    }

    private void assertExperimentAppearsOnlyOnceInObjectResults(ValidatableResponseWrapper response,
                                                                String objectId,
                                                                long expId) {
        JsonNode object = findObjectInResponse(response, objectId);
        int occurrences = countExpIdOccurrences(object, expId);
        assertEquals(2, occurrences,
                "Ожидали expId=" + expId + " ровно в MAIN и ALL для objectId=" + objectId + body(response));
    }

    private void assertExperimentAbsentInObjectResults(ValidatableResponseWrapper response,
                                                       String objectId,
                                                       long expId) {
        JsonNode object = findObjectInResponse(response, objectId);
        assertFalse(containsExpId(object, expId),
                "Не ожидали expId=" + expId + " в objectResults для objectId=" + objectId + body(response));
    }

    private boolean containsExpId(JsonNode object, long expId) {
        return countExpIdOccurrences(object, expId) > 0;
    }

    private int countExpIdOccurrences(JsonNode object, long expId) {
        JsonNode objectResults = object.path("objectResults");
        if (!objectResults.isArray()) {
            return 0;
        }
        int count = 0;
        for (JsonNode ruleResult : objectResults) {
            JsonNode resultExps = ruleResult.path("resultExps");
            if (!resultExps.isArray()) {
                continue;
            }
            for (JsonNode resultExp : resultExps) {
                if (resultExp.path("expId").asLong(Long.MIN_VALUE) == expId) {
                    count++;
                }
            }
        }
        return count;
    }

    private JsonNode findObjectInResponse(ValidatableResponseWrapper response, String objectId) {
        JsonNode root = SplitterResponseReader.snapshot(response)
                .requireJsonBody("Ожидали JSON body для поиска objectId=" + objectId);
        JsonNode splittingResults = root.path("splittingResults");
        assertTrue(splittingResults.isArray(), "splittingResults должен быть массивом" + body(response));
        for (JsonNode result : splittingResults) {
            if (Objects.equals(objectId, result.path("objectId").asText(null))) {
                return result;
            }
        }
        fail("Не найден objectId=" + objectId + body(response));
        return null;
    }
}


