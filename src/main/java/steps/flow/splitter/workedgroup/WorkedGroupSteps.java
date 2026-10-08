package steps.flow.splitter.workedgroup;

import config.services.splitter.WorkedGroupProfile;
import util.splittercheck.WorkedGroupAssertions;

import com.fasterxml.jackson.databind.JsonNode;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ShareDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import config.extensions.WorkloadRunScopeExtension;
import dto.splitter.split.SplitRequestDto;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.tests_v9.common.AbstractSplitterV9FlowTest;

import java.util.List;
import java.util.Objects;

import static util.TestAssertions.assertEquals;
import static util.TestAssertions.assertTrue;

/**
 * Общая DTO/flow-база сценариев EXPLAB-2690.
 *
 * <p>Проверки здесь намеренно строже старого tests_v9-контракта:
 * MAPPER и REACTIONS учитывают allow-result-without-main и ожидаемые
 * связанные сработавшие группы. ALL не зависит от наличия MAIN при allow=true.
 * При allow=true требуется MAIN с {@code resultExps=[]}; при false объект
 * возвращается как несвязанный. Сам несвязанный объект остаётся пустым.</p>
 */
@config.extensions.WorkloadScenario("EXPLAB-2690")
@ExtendWith(WorkloadRunScopeExtension.class)
public abstract class WorkedGroupSteps extends AbstractSplitterV9FlowTest {

    public static final String SALT_2690 = "EXPLAB-2690-SALT";
    public static final String LEFT_OBJECT_ID = "26900000-0000-0000-0000-000000000001";
    public static final String RIGHT_OBJECT_ID = "26900000-0000-0000-0000-000000000002";
    public static final String SINGLE_OBJECT_ID = "26900000-0000-0000-0000-000000000003";
    public static final String REACTIONS_OBJECT_ID = "26900000-0000-0000-0000-000000000004";

    private static final String MAPPER_CONFIGMAP_RESOURCE =
            "splitter/EXPLAB_2690/configmap/mapper-required.yml";
    private static final String REACTIONS_CONFIGMAP_RESOURCE =
            "splitter/EXPLAB_2690/configmap/reactions-required.yml";

    private LoadConfigRequestDto loadedConfig;
    private SplitRequestDto lastRequest;

    protected EndpointMode endpointMode() { return EndpointMode.MAPPER; }
    @Override protected String runtimeSplittingPoint() { return endpointMode().splittingPointCode(); }
    protected boolean allowResultWithoutMain() {
        var policy = getClass().getAnnotation(config.services.splitter.WorkedGroupPolicy.class);
        return policy == null || policy.allowWithoutMain();
    }

    @BeforeEach
    protected void prepareExplab2690StandState() {
        WorkedGroupProfile.prepare(endpointMode() == EndpointMode.REACTIONS, allowResultWithoutMain());
    }

    @org.junit.jupiter.api.AfterEach
    protected void collectWorkloadDiagnostics() {
        infrastructure.kubernetes.WorkloadScenarioEvidence.current().finish();
    }

    @Override
    protected ValidatableResponseWrapper loadConfig(FlowWithRest flow,
                                                    EndpointMode endpointMode,
                                                    LoadConfigRequestDto request) {
        String resource = endpointMode == EndpointMode.MAPPER
                ? MAPPER_CONFIGMAP_RESOURCE
                : REACTIONS_CONFIGMAP_RESOURCE;
        attachResource("Требуемая ConfigMap EXPLAB-2690 / " + endpointMode, resource, "text/yaml", ".yml");
        loadedConfig = request;
        return super.loadConfig(flow, endpointMode, request);
    }

    @Override
    protected ValidatableResponseWrapper split(FlowWithRest flow, EndpointMode mode, SplitRequestDto request) {
        lastRequest = request;
        return super.split(flow, mode, request);
    }

    @Override
    protected void assertBasicResponseContract(ValidatableResponseWrapper response, SplitRequestDto request, long version) {
        super.assertBasicResponseContract(response, request, version);
        WorkedGroupAssertions.objects(jsonBody(response, "split response"),
                request.getSplittingObjects().stream().map(dto.splitter.split.SplittingObjectDto::getObjectId).toList());
    }

    protected void assertRulesExactly(ValidatableResponseWrapper response, String objectId, String... codes) {
        WorkedGroupAssertions.rules(findObjectById(response, objectId), java.util.Set.of(codes));
    }

    @Override
    protected void assertRuleExpIdsExactly(ValidatableResponseWrapper response, String objectId,
                                           String ruleCode, Long... expectedExpIds) {
        org.junit.jupiter.api.Assertions.assertAll("Exact experiment rows " + ruleCode,
                () -> assertRuleResultSize(response, objectId, ruleCode, expectedExpIds.length),
                () -> super.assertRuleExpIdsExactly(response, objectId, ruleCode, expectedExpIds));
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
                "Для этого объекта ожидается пустой/отсутствующий objectResults; "
                        + "MAIN.resultExps=[] и ALL в REST-ответе не допускаются для objectId=" + objectId
                        + body(response));
        WorkedGroupAssertions.noMainFlags(object.path("objectFlags"));
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
        if ("MAIN".equals(ruleCode)) assertRulesExactly(response, objectId,
                Objects.equals(expectedExpGroup, expectedFinalExpGroup) ? new String[]{"MAIN", "ALL"} : new String[]{"MAIN"});
        JsonNode exp = firstRuleExp(response, objectId, ruleCode);
        assertEquals(expectedExpId, exp.path("expId").asLong(Long.MIN_VALUE), body(response));
        assertEquals(expectedConditionId, exp.path("conditionId").asInt(Integer.MIN_VALUE), body(response));
        assertEquals(expectedExpGroup, exp.path("expGroup").asText(null), body(response));
        assertFinalExpGroup(exp, expectedFinalExpGroup, response);
        WorkedGroupAssertions.parameters(exp, List.of(
                param("actionType", expectedActionType, "INTEGER"), param("result", expectedResult, "INTEGER")));
        assertConfiguredExperiment(response, exp);
    }

    protected void assertObjectWithoutMain(ValidatableResponseWrapper response, String objectId) {
        assertObjectWithoutMain(response, objectId, java.util.Map.of());
    }

    protected void assertObjectWithoutMain(ValidatableResponseWrapper response, String objectId,
                                            java.util.Map<Long, String> expectedAllGroups) {
        JsonNode object = findObjectById(response, objectId);
        boolean includeAll = allowResultWithoutMain() && !expectedAllGroups.isEmpty();
        org.junit.jupiter.api.Assertions.assertAll("no-MAIN object " + objectId,
                () -> WorkedGroupAssertions.noMainRestRules(object,
                        endpointMode() == EndpointMode.REACTIONS, allowResultWithoutMain(), !expectedAllGroups.isEmpty()),
                () -> {
                    if (!includeAll) return;
                    assertRuleResultSize(response, objectId, "ALL", expectedAllGroups.size());
                    assertRuleExpIdsExactly(response, objectId, "ALL", expectedAllGroups.keySet().toArray(Long[]::new));
                    org.junit.jupiter.api.Assertions.assertAll("ALL rows",
                            java.util.stream.StreamSupport.stream(findRule(response, objectId, "ALL", true)
                                    .path("resultExps").spliterator(), false)
                                    .map(exp -> (org.junit.jupiter.api.function.Executable) () -> {
                                        assertEquals(expectedAllGroups.get(exp.path("expId").longValue()),
                                                exp.path("expGroup").asText(null), body(response));
                                        assertEquals(exp.path("expGroup"), exp.path("finalExpGroup"), body(response));
                                        assertConfiguredExperiment(response, exp);
                                    }));
                    assertAllExpFlagsHaveAlternativeValue(response, objectId, "false");
                });
    }

    protected void assertConfiguredExperiment(ValidatableResponseWrapper response, JsonNode exp) {
        assertTrue(loadedConfig != null && lastRequest != null, "Configuration/request must be captured before assertions");
        ExperimentDto source = loadedConfig.getSplittingConfig().getExperiments().stream()
                .filter(e -> e.getId().longValue() == exp.path("expId").asLong(Long.MIN_VALUE)).findFirst().orElseThrow();
        WorkedGroupAssertions.experiment(exp, source, spread(source.getSalt(), lastRequest.getSplittingId()));
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

    @Override
    protected void assertMainHasNoExpFlags(ValidatableResponseWrapper response, String objectId) {
        for (JsonNode exp : findRule(response, objectId, "MAIN", true).path("resultExps"))
            WorkedGroupAssertions.mainFlags(exp.path("expFlags"));
    }

    @Override
    protected void assertAllExpFlagsHaveAlternativeValue(ValidatableResponseWrapper response, String objectId, String expected) {
        for (JsonNode exp : findRule(response, objectId, "ALL", true).path("resultExps")) {
            JsonNode flags = exp.path("expFlags");
            if ("false".equals(expected) && WorkedGroupAssertions.empty(flags)) continue;
            assertEquals(java.util.Set.of("isAlternative"), WorkedGroupAssertions.unique(flags, "code"), body(response));
            assertTrue(flags.get(0).path("value").isTextual(), "Flag value must be a string");
            assertEquals(expected, flags.get(0).path("value").textValue(), body(response));
        }
    }
}
