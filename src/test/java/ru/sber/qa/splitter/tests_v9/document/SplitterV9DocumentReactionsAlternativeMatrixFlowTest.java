package ru.sber.qa.splitter.tests_v9.document;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ShareDto;
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
@DisplayName("Tests-v9 / EXPLAB-2690. REACTIONS без альтернативного MAIN")
@AnyConfigLoadMode
public class SplitterV9DocumentReactionsAlternativeMatrixFlowTest extends AbstractSplitterV9FlowTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("reactionsAlternativeCases")
    void reactionsAlternativeCasesShouldSelectOnlyWorkedGroupsLinkedToCurrentObject(ReactionsAlternativeCase testCase) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = reactionsAnalogConfig(version);
        String splittingId = splittingIdForRange(testCase.id(), V9_SALT, testCase.rangeFrom(), testCase.rangeTo());
        SplitRequestDto request = splitRequest(splittingId,
                object(OBJECT_1, param("id1", "1", "INTEGER")),
                object(OBJECT_2, param("id2", "2", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем REACTIONS config для матрицы " + testCase.id(),
                        flow -> loadConfig(flow, EndpointMode.REACTIONS, config))
                .step("Проверяем отсутствие семантической альтернативы: splittingId=" + splittingId
                                + ", spread=" + spread(V9_SALT, splittingId),
                        flow -> {
                            ValidatableResponseWrapper response = split(flow, EndpointMode.REACTIONS, request);
                            assertBasicResponseContract(response, request, version);
                            assertSplittingResultsHaveUniqueObjectIds(response);
                            long expectedSpread = spread(V9_SALT, splittingId);
                            verifyObjectMain(response, OBJECT_1, testCase.object1Worked(), testCase.object1Main(), expectedSpread);
                            verifyObjectMain(response, OBJECT_2, testCase.object2Worked(), testCase.object2Main(), expectedSpread);
                            assertNoAlternativeTrueAnywhere(response);
                        })
                .run();
    }

    private void verifyObjectMain(ValidatableResponseWrapper response,
                                  String objectId,
                                  Long[] workedExpIds,
                                  ExpectedMain expectedMain,
                                  long expectedSpread) {
        if (expectedMain == null) {
            assertObjectEmptyOrAbsent(response, objectId);
            if (hasObject(response, objectId)) {
                assertRuleAbsent(response, objectId, "MAIN");
                assertRuleAbsent(response, objectId, "ALL");
            }
            return;
        }

        assertRuleExpIdsExactly(response, objectId, "MAIN", expectedMain.expId());
        assertRuleExpsHaveMandatoryFields(response, objectId, "MAIN");
        assertRuleExpsHaveSpreadValue(response, objectId, "MAIN", expectedSpread);
        assertFirstRuleExp(response, objectId, "MAIN",
                expectedMain.expId(), expectedMain.expGroup(), expectedMain.finalExpGroup());
        assertFirstRuleExpConditionId(response, objectId, "MAIN", expectedMain.conditionId());
        assertFirstRuleExpActionType(response, objectId, "MAIN", expectedMain.actionType());
        assertFirstRuleExpResultValue(response, objectId, "MAIN", expectedMain.result());
        assertObjectFlagsEmpty(response, objectId);
        if (findRule(response, objectId, "ALL", false) != null) {
            assertRuleExpIdsExactly(response, objectId, "ALL", workedExpIds);
            assertAllResponseRowsAreWorkedGroups(response, objectId);
            assertAllExpFlagsHaveAlternativeValue(response, objectId, "false");
        }
    }

    private LoadConfigRequestDto reactionsAnalogConfig(long version) {
        return configFor(EndpointMode.REACTIONS, version, exp1(), exp2(), exp3());
    }

    private ExperimentDto exp1() {
        return experiment(1,
                V9_SALT,
                List.of(
                        objectParamEqualsCondition(1, "id1", "1", "INTEGER"),
                        objectParamEqualsCondition(2, "id2", "2", "INTEGER")),
                List.of(
                        groupWithDocResult("A", List.of(share(0, 2500)), 1, "0", "1"),
                        groupWithDocResult("B", List.of(share(2500, 5000)), 2, "1", "2")));
    }

    private ExperimentDto exp2() {
        List<ShareDto> shares = List.of(share(0, 1000), share(2500, 3500), share(5000, 6000));
        return experiment(2,
                V9_SALT,
                List.of(objectParamEqualsCondition(1, "id1", "1", "INTEGER")),
                List.of(groupWithDocResult("A", shares, 1, "1", "2")));
    }

    private ExperimentDto exp3() {
        List<ShareDto> shares = List.of(
                share(0, 500),
                share(1000, 1500),
                share(2500, 3000),
                share(3500, 4000),
                share(5000, 5500),
                share(6000, 6500));
        return experiment(3,
                V9_SALT,
                List.of(objectParamEqualsCondition(1, "id2", "2", "INTEGER")),
                List.of(groupWithDocResult("A", shares, 1, "3", "3")));
    }

    private static Stream<Arguments> reactionsAlternativeCases() {
        return Stream.of(
                Arguments.of(new ReactionsAlternativeCase("SPL-V9-T04R-00-05-ALLOW", 0, 500,
                        idsStatic(1, 2), main(1, "A", "A", 1, "0", "1"),
                        idsStatic(3), main(1, "B", "A", 2, "1", "2"))),
                Arguments.of(new ReactionsAlternativeCase("SPL-V9-T04R-05-10-ALLOW", 500, 1000,
                        idsStatic(1, 2), main(1, "A", "A", 1, "0", "1"),
                        idsStatic(), main(1, "B", "A", 2, "1", "2"))),
                Arguments.of(new ReactionsAlternativeCase("SPL-V9-T04R-10-15-ALLOW", 1000, 1500,
                        idsStatic(1), main(1, "A", "A", 1, "0", "1"),
                        idsStatic(3), main(1, "B", "A", 2, "1", "2"))),
                Arguments.of(new ReactionsAlternativeCase("SPL-V9-T04R-15-20-ALLOW", 1500, 2000,
                        idsStatic(1), main(1, "A", "A", 1, "0", "1"),
                        idsStatic(), main(1, "B", "A", 2, "1", "2"))),
                Arguments.of(new ReactionsAlternativeCase("SPL-V9-T04R-25-30-ALLOW", 2500, 3000,
                        idsStatic(2), main(1, "A", "B", 1, "0", "1"),
                        idsStatic(1, 3), main(1, "B", "B", 2, "1", "2"))),
                Arguments.of(new ReactionsAlternativeCase("SPL-V9-T04R-30-35-ALLOW", 3000, 3500,
                        idsStatic(2), main(1, "A", "B", 1, "0", "1"),
                        idsStatic(1), main(1, "B", "B", 2, "1", "2"))),
                Arguments.of(new ReactionsAlternativeCase("SPL-V9-T04R-35-40-ALLOW", 3500, 4000,
                        idsStatic(), main(1, "A", "B", 1, "0", "1"),
                        idsStatic(1, 3), main(1, "B", "B", 2, "1", "2"))),
                Arguments.of(new ReactionsAlternativeCase("SPL-V9-T04R-40-45-ALLOW", 4000, 4500,
                        idsStatic(), main(1, "A", "B", 1, "0", "1"),
                        idsStatic(1), main(1, "B", "B", 2, "1", "2"))),
                Arguments.of(new ReactionsAlternativeCase("SPL-V9-T04R-50-55-ALLOW", 5000, 5500,
                        idsStatic(2), main(2, "A", "A", 1, "1", "2"),
                        idsStatic(3), main(3, "A", "A", 1, "3", "3"))),
                Arguments.of(new ReactionsAlternativeCase("SPL-V9-T04R-55-60-ALLOW", 5500, 6000,
                        idsStatic(2), main(2, "A", "A", 1, "1", "2"),
                        idsStatic(), null)),
                Arguments.of(new ReactionsAlternativeCase("SPL-V9-T04R-60-65-ALLOW", 6000, 6500,
                        idsStatic(), null,
                        idsStatic(3), main(3, "A", "A", 1, "3", "3"))),
                Arguments.of(new ReactionsAlternativeCase("SPL-V9-T04R-65-70-ALLOW", 6500, 7000,
                        idsStatic(), null,
                        idsStatic(), null)),
                Arguments.of(new ReactionsAlternativeCase("SPL-V9-T04R-55-60-DENY", 5500, 6000,
                        idsStatic(2), main(2, "A", "A", 1, "1", "2"),
                        idsStatic(), null)),
                Arguments.of(new ReactionsAlternativeCase("SPL-V9-T04R-60-65-DENY", 6000, 6500,
                        idsStatic(), null,
                        idsStatic(3), main(3, "A", "A", 1, "3", "3"))),
                Arguments.of(new ReactionsAlternativeCase("SPL-V9-T04R-65-70-DENY", 6500, 7000,
                        idsStatic(), null,
                        idsStatic(), null))
        );
    }

    private static ExpectedMain main(long expId,
                                     String expGroup,
                                     String finalExpGroup,
                                     int conditionId,
                                     String actionType,
                                     String result) {
        return new ExpectedMain(expId, expGroup, finalExpGroup, conditionId, actionType, result);
    }

    private static Long[] idsStatic(long... values) {
        Long[] result = new Long[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i];
        }
        return result;
    }

    private record ReactionsAlternativeCase(String id,
                                            int rangeFrom,
                                            int rangeTo,
                                            Long[] object1Worked,
                                            ExpectedMain object1Main,
                                            Long[] object2Worked,
                                            ExpectedMain object2Main) {
        @Override
        public String toString() {
            return id;
        }
    }

    private record ExpectedMain(long expId,
                                String expGroup,
                                String finalExpGroup,
                                int conditionId,
                                String actionType,
                                String result) {
    }
}
