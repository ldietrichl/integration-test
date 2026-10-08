package ru.sber.qa.splitter.EXPLAB_2690;

import com.fasterxml.jackson.databind.JsonNode;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ShareDto;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.tests_v9.common.AbstractSplitterV9FlowTest;

import java.util.List;
import java.util.Objects;

import static util.TestAssertions.assertEquals;
import static util.TestAssertions.assertTrue;
import static util.TestAssertions.fail;

/**
 * Общая DTO/flow-база сценариев EXPLAB-2690.
 *
 * <p>Проверки здесь намеренно строже старого tests_v9-контракта:
 * после изменения v1.3.0 объект без выбранного MAIN должен выглядеть так,
 * как будто к нему ничего не привязалось. Поэтому технический MAIN с
 * {@code resultExps=[]} и диагностический ALL в публичном REST-ответе не принимаются.</p>
 */
abstract class AbstractExplab2690FlowTest extends AbstractSplitterV9FlowTest {

    protected static final String SALT_2690 = "EXPLAB-2690-SALT";
    protected static final String LEFT_OBJECT_ID = "26900000-0000-0000-0000-000000000001";
    protected static final String RIGHT_OBJECT_ID = "26900000-0000-0000-0000-000000000002";
    protected static final String SINGLE_OBJECT_ID = "26900000-0000-0000-0000-000000000003";
    protected static final String REACTIONS_OBJECT_ID = "26900000-0000-0000-0000-000000000004";

    private static final String MAPPER_CONFIGMAP_RESOURCE =
            "splitter/EXPLAB_2690/configmap/mapper-required.yml";
    private static final String REACTIONS_CONFIGMAP_RESOURCE =
            "splitter/EXPLAB_2690/configmap/reactions-required.yml";


    @Override
    protected ValidatableResponseWrapper loadConfig(FlowWithRest flow,
                                                    EndpointMode endpointMode,
                                                    LoadConfigRequestDto request) {
        String resource = endpointMode == EndpointMode.MAPPER
                ? MAPPER_CONFIGMAP_RESOURCE
                : REACTIONS_CONFIGMAP_RESOURCE;
        attachResource("Требуемая ConfigMap EXPLAB-2690 / " + endpointMode, resource, "text/yaml", ".yml");
        return super.loadConfig(flow, endpointMode, request);
    }

    protected LoadConfigRequestDto singleExperimentConfig(EndpointMode endpointMode,
                                                          long version,
                                                          ExperimentDto experiment) {
        return configFor(endpointMode, version, experiment);
    }

    protected List<ShareDto> shares(int from, int to) {
        return List.of(share(from, to));
    }

    protected void assertObjectHasStrictlyEmptyResult(ValidatableResponseWrapper response, String objectId) {
        JsonNode object = findObjectById(response, objectId);
        assertStrictlyEmptyObjectResults(response, objectId, object);
    }

    protected void assertObjectStrictlyEmptyOrAbsent(ValidatableResponseWrapper response, String objectId) {
        if (!hasObject(response, objectId)) {
            return;
        }
        assertStrictlyEmptyObjectResults(response, objectId, findObjectById(response, objectId));
    }

    private void assertStrictlyEmptyObjectResults(ValidatableResponseWrapper response,
                                                  String objectId,
                                                  JsonNode object) {
        JsonNode objectResults = object.path("objectResults");
        boolean empty = objectResults.isMissingNode()
                || objectResults.isNull()
                || (objectResults.isArray() && objectResults.isEmpty());
        assertTrue(empty,
                "После EXPLAB-2690 объект без MAIN должен иметь пустой/отсутствующий objectResults; "
                        + "MAIN.resultExps=[] и ALL в REST-ответе не допускаются для objectId=" + objectId
                        + body(response));
    }

    protected void assertResultExp(ValidatableResponseWrapper response,
                                   String objectId,
                                   String ruleCode,
                                   long expectedExpId,
                                   int expectedConditionId,
                                   String expectedExpGroup,
                                   String expectedFinalExpGroup,
                                   String expectedActionType,
                                   String expectedResult) {
        assertRuleResultSize(response, objectId, ruleCode, 1);
        JsonNode exp = firstRuleExp(response, objectId, ruleCode);
        assertEquals(expectedExpId, exp.path("expId").asLong(Long.MIN_VALUE), body(response));
        assertEquals(expectedConditionId, exp.path("conditionId").asInt(Integer.MIN_VALUE), body(response));
        assertEquals(expectedExpGroup, exp.path("expGroup").asText(null), body(response));
        assertFinalExpGroup(exp, expectedFinalExpGroup, response);
        assertEquals(expectedActionType, resultParamValue(exp, "actionType"), body(response));
        assertEquals(expectedResult, resultParamValue(exp, "result"), body(response));
    }

    protected void assertEveryRuleExpUsesWorkedGroup(ValidatableResponseWrapper response,
                                                      String objectId,
                                                      String ruleCode) {
        JsonNode rule = findRule(response, objectId, ruleCode, true);
        JsonNode resultExps = rule.path("resultExps");
        assertTrue(resultExps.isArray(), ruleCode + ".resultExps должен быть массивом" + body(response));
        for (JsonNode exp : resultExps) {
            String expGroup = exp.path("expGroup").asText(null);
            String finalExpGroup = exp.path("finalExpGroup").asText(null);
            assertEquals(expGroup, finalExpGroup,
                    "В " + ruleCode + " REACTIONS не допускается альтернативная пара expGroup/finalExpGroup"
                            + body(response));
        }
    }

    protected String resultParamValue(JsonNode exp, String paramCode) {
        JsonNode params = exp.path("groupResultParams");
        if (!params.isArray()) {
            fail("groupResultParams должен быть массивом для paramCode=" + paramCode + "; exp=" + exp);
        }
        for (JsonNode param : params) {
            if (Objects.equals(paramCode, param.path("paramCode").asText(null))) {
                JsonNode values = param.path("paramValues");
                if (values.isArray() && !values.isEmpty()) {
                    return values.get(0).asText(null);
                }
            }
        }
        fail("Не найден groupResultParams.paramCode=" + paramCode + "; exp=" + exp);
        return null;
    }
}


