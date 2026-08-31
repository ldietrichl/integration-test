package ru.sber.qa.splitter.tests_v9.document;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.split.SplitRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.tests_v9.common.AbstractSplitterV9FlowTest;
import util.support.SplitterVersionProvider;

import java.util.List;
import java.util.stream.Stream;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("Tests-v9. Тест 1/2: один эксперимент, группы A/B/C")
@AnyConfigLoadMode
public class SplitterV9DocumentSingleExperimentMatrixFlowTest extends AbstractSplitterV9FlowTest {

    private static final long EXP_ID = 1L;

    @ParameterizedTest(name = "{0}")
    @MethodSource("singleExperimentCases")
    void singleExperimentCasesShouldFollowDocumentMatrix(SingleExperimentCase testCase) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER, version, experimentFor(testCase));
        String splittingId = splittingIdForRange(testCase.id(), V9_SALT, testCase.rangeFrom(), testCase.rangeTo());
        SplitRequestDto request = splitRequest(splittingId, objectFor(testCase));

        getFlowWithRest()
                .step("Загружаем MAPPER config для " + testCase.id(), flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Выполняем split для " + testCase.id() + ": splittingId=" + splittingId
                                + ", spread=" + spread(V9_SALT, splittingId),
                        flow -> {
                            ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                            assertBasicResponseContract(response, request, version);
                            assertSplittingResultsHaveUniqueObjectIds(response);
                            verifySingleExperimentCase(response, testCase, spread(V9_SALT, splittingId));
                            assertAllResponseRowsAreWorkedGroupsForEveryObject(response);
                        })
                .run();
    }

    private void verifySingleExperimentCase(ValidatableResponseWrapper response, SingleExperimentCase testCase, long expectedSpread) {
        if (testCase.expectedGroup() == null || testCase.emptyResult()) {
            assertObjectEmptyOrAbsent(response, OBJECT_1);
            return;
        }

        assertRuleResultSize(response, OBJECT_1, "MAIN", 1);
        assertRuleExpsHaveMandatoryFields(response, OBJECT_1, "MAIN");
        assertRuleExpsHaveSpreadValue(response, OBJECT_1, "MAIN", expectedSpread);
        assertMainHasNoExpFlags(response, OBJECT_1);

        // EXPLAB-2690: даже если несколько групп привязаны через один conditionId,
        // MAIN должен использовать фактически сработавшую группу и её resultParams.
        boolean expectedFiltered = "2".equals(testCase.expectedActionType()) || "4".equals(testCase.expectedActionType());
        assertFilteredFlag(response, OBJECT_1, expectedFiltered);
        assertFirstRuleExp(response, OBJECT_1, "MAIN", EXP_ID, testCase.expectedGroup(), testCase.expectedGroup());
        assertFirstRuleExpConditionId(response, OBJECT_1, "MAIN", testCase.expectedConditionId());
        assertFirstRuleExpActionType(response, OBJECT_1, "MAIN", testCase.expectedActionType());
        assertFirstRuleExpResultValue(response, OBJECT_1, "MAIN", testCase.expectedResult());
        if (findRule(response, OBJECT_1, "ALL", false) != null) {
            assertRuleExpsHaveMandatoryFields(response, OBJECT_1, "ALL");
            assertRuleExpsHaveSpreadValue(response, OBJECT_1, "ALL", expectedSpread);
            assertAllExpFlagsHaveAlternativeValue(response, OBJECT_1, "false");
        }

    }


    private ExperimentDto experimentFor(SingleExperimentCase testCase) {
        if (testCase.sameCondition()) {
            return experiment(1,
                    V9_SALT,
                    List.of(objectParamEqualsCondition(1, "id1", "1", "INTEGER")),
                    List.of(
                            groupWithDocResult("A", 0, 2500, 1, "1", "1"),
                            groupWithDocResult("B", 2500, 5000, 1, "2", "2"),
                            groupWithEmptyResultParams("C", 5000, 7500, 1)
                    ));
        }
        return experiment(1,
                V9_SALT,
                List.of(
                        objectParamEqualsCondition(1, "id1", "1", "INTEGER"),
                        objectParamEqualsCondition(2, "id2", "2", "INTEGER"),
                        objectParamEqualsCondition(3, "id3", "3", "INTEGER")
                ),
                List.of(
                        groupWithDocResult("A", 0, 2500, 1, "1", "1"),
                        groupWithDocResult("B", 2500, 5000, 2, "2", "2"),
                        groupWithEmptyResultParams("C", 5000, 7500, 3)
                ));
    }

    private dto.splitter.split.SplittingObjectDto objectFor(SingleExperimentCase testCase) {
        if (testCase.sameCondition()) {
            return object(OBJECT_1, param("id1", "1", "INTEGER"));
        }
        return object(OBJECT_1,
                param("id1", "1", "INTEGER"),
                param("id2", "2", "INTEGER"),
                param("id3", "3", "INTEGER"));
    }

    private static Stream<Arguments> singleExperimentCases() {
        return Stream.of(
                Arguments.of(SingleExperimentCase.differentConditions("SPL-V9-T01-A-ALLOW", 0, 2500, "A", 1, "1", "1", false)),
                Arguments.of(SingleExperimentCase.differentConditions("SPL-V9-T01-B-ALLOW", 2500, 5000, "B", 2, "2", "2", false)),
                Arguments.of(SingleExperimentCase.differentConditions("SPL-V9-T01-C-EMPTY-ALLOW", 5000, 7500, "C", 3, null, null, true)),
                Arguments.of(SingleExperimentCase.differentConditions("SPL-V9-T01-NOMAIN-ALLOW", 7500, 10000, null, 0, null, null, false)),
                Arguments.of(SingleExperimentCase.differentConditions("SPL-V9-T01-NOMAIN-DENY", 7500, 10000, null, 0, null, null, false)),
                Arguments.of(SingleExperimentCase.sameCondition("SPL-V9-T02-A-ALLOW", 0, 2500, "A", "1", "1", false)),
                Arguments.of(SingleExperimentCase.sameCondition("SPL-V9-T02-B-ALLOW", 2500, 5000, "B", "2", "2", false)),
                Arguments.of(SingleExperimentCase.sameCondition("SPL-V9-T02-C-EMPTY-ALLOW", 5000, 7500, "C", null, null, true)),
                Arguments.of(SingleExperimentCase.sameCondition("SPL-V9-T02-NOMAIN-ALLOW", 7500, 10000, null, null, null, false)),
                Arguments.of(SingleExperimentCase.sameCondition("SPL-V9-T02-NOMAIN-DENY", 7500, 10000, null, null, null, false))
        );
    }

    private record SingleExperimentCase(String id,
                                        int rangeFrom,
                                        int rangeTo,
                                        String expectedGroup,
                                        int expectedConditionId,
                                        String expectedActionType,
                                        String expectedResult,
                                        boolean emptyResult,
                                        boolean sameCondition) {

        static SingleExperimentCase differentConditions(String id,
                                                        int rangeFrom,
                                                        int rangeTo,
                                                        String expectedGroup,
                                                        int expectedConditionId,
                                                        String expectedActionType,
                                                        String expectedResult,
                                                        boolean emptyResult) {
            return new SingleExperimentCase(id, rangeFrom, rangeTo, expectedGroup, expectedConditionId,
                    expectedActionType, expectedResult, emptyResult, false);
        }

        static SingleExperimentCase sameCondition(String id,
                                                  int rangeFrom,
                                                  int rangeTo,
                                                  String expectedGroup,
                                                  String expectedActionType,
                                                  String expectedResult,
                                                  boolean emptyResult) {
            return new SingleExperimentCase(id, rangeFrom, rangeTo, expectedGroup, expectedGroup == null ? 0 : 1,
                    expectedActionType, expectedResult, emptyResult, true);
        }

        @Override
        public String toString() {
            return id;
        }
    }
}
