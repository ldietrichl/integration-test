package ru.sber.qa.splitter.EXPLAB_2892;

import com.fasterxml.jackson.databind.JsonNode;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.GroupDto;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.tests_v9.common.AbstractSplitterV9FlowTest;
import util.support.SplitterVersionProvider;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("EXPLAB-2892. REACTIONS: группа итогового эксперимента")
public class SplitterReactionsFinalGroup2892FlowTest extends AbstractSplitterV9FlowTest {

    private static final int EXP_ID = 2;
    private static final String SALT = "ICP4GROUPD";
    private static final String OBJECT_1_ID = "1";
    private static final String OBJECT_2_ID = "2";
    private static final String OBJECT_3_ID = "3";
    private static final String OBJECT_4_ID = "4";
    private static final String SPEC_B_SPLITTING_ID = "1129464980047006855";

    @CriticalRegression
    @ParameterizedTest(name = "{0}")
    @MethodSource("workedGroupCases")
    @DisplayName("EXPLAB-2892-01. REACTIONS REST выбирает реально сработавшую группу в MAIN")
    void reactionsRestShouldUseWorkedGroupForFinalExperiment(WorkedGroupCase testCase) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = reactionsConfig(version);
        SplitRequestDto request = request(testCase.splittingId());

        getFlowWithRest()
                .step("Загружаем REACTIONS config EXPLAB-2892: один experiment, один conditionId, группы A/B/C",
                        flow -> loadConfig(flow, EndpointMode.REACTIONS, config))
                .step("Выполняем split: objectId=1 связан со всеми группами, сработавшая группа="
                                + testCase.expectedGroup(),
                        flow -> {
                            ValidatableResponseWrapper response = split(flow, EndpointMode.REACTIONS, request);
                            assertBasicResponseContract(response, request, version);
                            assertWorkedGroupInRest(response, testCase);
                            assertUnmatchedObjectsAreEmpty(response);
                        })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2892-02. Kafka-report выбирает B/B в MAIN; ALL проверяется при наличии")
    void reactionsKafkaReportShouldUseWorkedGroupInMainAndCheckAllWhenPresent(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = reactionsConfig(version);
        WorkedGroupCase testCase = new WorkedGroupCase(
                "EXPLAB-2892-02-B", SPEC_B_SPLITTING_ID, "B", 1928, "2");
        SplitRequestDto request = request(testCase.splittingId());
        long since = System.currentTimeMillis();

        getFlowWithRest()
                .step("Загружаем REACTIONS config из спецификации EXPLAB-2892",
                        flow -> loadConfig(flow, EndpointMode.REACTIONS, config))
                .step("Выполняем split из спецификации: spread=1928, сработала группа B",
                        flow -> {
                            ValidatableResponseWrapper response = split(flow, EndpointMode.REACTIONS, request);
                            assertBasicResponseContract(response, request, version);
                            assertWorkedGroupInRest(response, testCase);
                        })
                .step("Читаем Kafka/report и проверяем MAIN по objectId=1, ALL - при наличии",
                        flow -> {
                            String payload = findKafkaPayloadByRequestId(kafkaService, request.getRequestId(), since);
                            assertKafkaReportMeta(payload, request, version);
                            JsonNode reportRoot = reportResultRoot(payload);

                            assertReportRuleResult(reportRoot, OBJECT_1_ID, "MAIN", 1);
                            assertReportExp(reportRoot, OBJECT_1_ID, "MAIN", "B", "B", "2");

                            assertReportAllWhenEnabled(reportRoot);
                            assertUnmatchedObjectsAreEmpty(reportRoot);
                        })
                .run();
    }

    private void assertWorkedGroupInRest(ValidatableResponseWrapper response, WorkedGroupCase testCase) {
        assertRuleResultSize(response, OBJECT_1_ID, "MAIN", 1);
        assertFirstRuleExp(response, OBJECT_1_ID, "MAIN", EXP_ID, testCase.expectedGroup(), testCase.expectedGroup());
        assertFirstRuleExpConditionId(response, OBJECT_1_ID, "MAIN", 1);
        assertFirstRuleExpResultValue(response, OBJECT_1_ID, "MAIN", testCase.expectedResult());
        assertRuleExpsHaveSpreadValue(response, OBJECT_1_ID, "MAIN", testCase.expectedSpread());
        assertNoAlternativeTrueAnywhere(response);

        if (findRule(response, OBJECT_1_ID, "ALL", false) != null) {
            assertRuleResultSize(response, OBJECT_1_ID, "ALL", 1);
            assertFirstRuleExp(response, OBJECT_1_ID, "ALL", EXP_ID, testCase.expectedGroup(), testCase.expectedGroup());
            assertFirstRuleExpConditionId(response, OBJECT_1_ID, "ALL", 1);
            assertFirstRuleExpResultValue(response, OBJECT_1_ID, "ALL", testCase.expectedResult());
            assertRuleExpsHaveSpreadValue(response, OBJECT_1_ID, "ALL", testCase.expectedSpread());
        }
    }

    private void assertUnmatchedObjectsAreEmpty(ValidatableResponseWrapper response) {
        assertObjectEmptyOrAbsent(response, OBJECT_2_ID);
        assertObjectEmptyOrAbsent(response, OBJECT_3_ID);
        assertObjectEmptyOrAbsent(response, OBJECT_4_ID);
    }

    private LoadConfigRequestDto reactionsConfig(long version) {
        ExperimentDto experiment = experiment(EXP_ID,
                SALT,
                List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")),
                List.of(
                        reactionGroup("A", 0, 1500, "1"),
                        reactionGroup("B", 1500, 4500, "2"),
                        reactionGroup("C", 4500, 7000, "3")));
        return configFor(EndpointMode.REACTIONS, version, experiment);
    }

    private GroupDto reactionGroup(String code, int shareFrom, int shareTo, String resultValue) {
        return group(code, List.of(share(shareFrom, shareTo)),
                List.of(resultWithParams(1, param("result", resultValue, "INTEGER"))));
    }

    private SplitRequestDto request(String splittingId) {
        return splitRequest(splittingId,
                objectWithUniqueId("1", OBJECT_1_ID, param("id", "1", "INTEGER")),
                objectWithUniqueId("2", OBJECT_2_ID, param("id", "2", "INTEGER")),
                objectWithUniqueId("3", OBJECT_3_ID, param("id", "3", "INTEGER")),
                objectWithUniqueId("4", OBJECT_4_ID, param("id", "4", "INTEGER")));
    }

    private JsonNode reportResultRoot(String payload) {
        JsonNode root = readJson(payload);
        JsonNode message = root.path("message");
        if (message.path("splittingResults").isArray()) {
            return message;
        }
        if (root.path("splittingResults").isArray()) {
            return root;
        }
        fail("Kafka/report payload не содержит splittingResults в корне или message\n" + payload);
        return root;
    }

    private void assertReportRuleResult(JsonNode reportRoot, String objectId, String ruleCode, int expectedSize) {
        JsonNode rule = findReportRule(reportRoot, objectId, ruleCode, true);
        JsonNode resultExps = rule.path("resultExps");
        assertTrue(resultExps.isArray(),
                "В Kafka/report " + ruleCode + ".resultExps должен быть массивом\n" + reportRoot);
        assertEquals(expectedSize, resultExps.size(),
                "Неверный размер " + ruleCode + ".resultExps для objectId=" + objectId + "\n" + reportRoot);
    }

    private void assertReportAllWhenEnabled(JsonNode reportRoot) {
        if (findReportRule(reportRoot, OBJECT_1_ID, "ALL", false) == null) {
            return;
        }

        assertReportRuleResult(reportRoot, OBJECT_1_ID, "ALL", 3);
        assertReportExp(reportRoot, OBJECT_1_ID, "ALL", "A", "B", "1");
        assertReportExp(reportRoot, OBJECT_1_ID, "ALL", "B", "B", "2");
        assertReportExp(reportRoot, OBJECT_1_ID, "ALL", "C", "B", "3");
    }

    private void assertReportExp(JsonNode reportRoot,
                                 String objectId,
                                 String ruleCode,
                                 String expectedExpGroup,
                                 String expectedFinalExpGroup,
                                 String expectedResult) {
        JsonNode exp = findReportExp(reportRoot, objectId, ruleCode, expectedExpGroup, expectedFinalExpGroup);
        assertEquals(EXP_ID, exp.path("expId").asInt(Integer.MIN_VALUE), "Неверный expId\n" + reportRoot);
        assertEquals(1, exp.path("conditionId").asInt(Integer.MIN_VALUE), "Неверный conditionId\n" + reportRoot);
        assertEquals(SALT, exp.path("salt").asText(null), "Неверный salt\n" + reportRoot);
        assertEquals(expectedResult, reportResultParamValue(exp, "result"), "Неверный result\n" + reportRoot);
    }

    private JsonNode findReportExp(JsonNode reportRoot,
                                   String objectId,
                                   String ruleCode,
                                   String expectedExpGroup,
                                   String expectedFinalExpGroup) {
        JsonNode rule = findReportRule(reportRoot, objectId, ruleCode, true);
        JsonNode resultExps = rule.path("resultExps");
        assertTrue(resultExps.isArray(), ruleCode + ".resultExps должен быть массивом\n" + reportRoot);
        for (JsonNode exp : resultExps) {
            if (Objects.equals(expectedExpGroup, exp.path("expGroup").asText(null))
                    && Objects.equals(expectedFinalExpGroup, exp.path("finalExpGroup").asText(null))) {
                return exp;
            }
        }
        fail("Не найдена запись " + ruleCode
                + " с expGroup=" + expectedExpGroup
                + ", finalExpGroup=" + expectedFinalExpGroup
                + " для objectId=" + objectId
                + "\n" + reportRoot);
        return null;
    }

    private void assertUnmatchedObjectsAreEmpty(JsonNode reportRoot) {
        assertReportObjectResultsEmpty(reportRoot, OBJECT_2_ID);
        assertReportObjectResultsEmpty(reportRoot, OBJECT_3_ID);
        assertReportObjectResultsEmpty(reportRoot, OBJECT_4_ID);
    }

    private void assertReportObjectResultsEmpty(JsonNode reportRoot, String objectId) {
        JsonNode object = findReportObject(reportRoot, objectId, true);
        JsonNode objectResults = object.path("objectResults");
        boolean empty = objectResults.isMissingNode()
                || objectResults.isNull()
                || (objectResults.isArray() && objectResults.isEmpty());
        assertTrue(empty, "Ожидали пустой objectResults для objectId=" + objectId + "\n" + reportRoot);
    }

    private JsonNode findReportRule(JsonNode reportRoot, String objectId, String ruleCode, boolean failIfMissing) {
        JsonNode object = findReportObject(reportRoot, objectId, failIfMissing);
        if (object == null) {
            return null;
        }
        JsonNode objectResults = object.path("objectResults");
        if (!objectResults.isArray()) {
            if (failIfMissing) {
                fail("objectResults должен быть массивом для objectId=" + objectId + "\n" + reportRoot);
            }
            return null;
        }
        for (JsonNode rule : objectResults) {
            if (Objects.equals(ruleCode, rule.path("ruleCode").asText(null))) {
                return rule;
            }
        }
        if (failIfMissing) {
            fail("Не найден ruleCode=" + ruleCode + " для objectId=" + objectId + "\n" + reportRoot);
        }
        return null;
    }

    private JsonNode findReportObject(JsonNode reportRoot, String objectId, boolean failIfMissing) {
        JsonNode splittingResults = reportRoot.path("splittingResults");
        assertTrue(splittingResults.isArray(), "splittingResults должен быть массивом\n" + reportRoot);
        for (JsonNode object : splittingResults) {
            if (Objects.equals(objectId, object.path("objectId").asText(null))) {
                return object;
            }
        }
        if (failIfMissing) {
            fail("Не найден objectId=" + objectId + "\n" + reportRoot);
        }
        return null;
    }

    private String reportResultParamValue(JsonNode exp, String paramCode) {
        JsonNode params = exp.path("groupResultParams");
        assertTrue(params.isArray(), "groupResultParams должен быть массивом; exp=" + exp);
        for (JsonNode param : params) {
            if (Objects.equals(paramCode, param.path("paramCode").asText(null))) {
                JsonNode values = param.path("paramValues");
                assertTrue(values.isArray() && !values.isEmpty(), "paramValues должен быть непустым; exp=" + exp);
                return values.get(0).asText(null);
            }
        }
        fail("Не найден groupResultParams.paramCode=" + paramCode + "; exp=" + exp);
        return null;
    }

    private static Stream<Arguments> workedGroupCases() {
        return Stream.of(
                Arguments.of(new WorkedGroupCase("EXPLAB-2892-01-B", SPEC_B_SPLITTING_ID, "B", 1928, "2")),
                Arguments.of(new WorkedGroupCase("EXPLAB-2892-01-C",
                        "EXPLAB-2892-C-7", "C", 6313, "3"))
        );
    }

    private record WorkedGroupCase(String id,
                                   String splittingId,
                                   String expectedGroup,
                                   int expectedSpread,
                                   String expectedResult) {
        @Override
        public String toString() {
            return id;
        }
    }
}
