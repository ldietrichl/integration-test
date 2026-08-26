package ru.sber.qa.splitter.EXPLAB_2690;

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
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import util.support.SplitterVersionProvider;

import java.util.List;
import java.util.stream.Stream;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("EXPLAB-2690. MAPPER: MAIN, finalExpGroup и очистка ALL")
public class SplitterMapperWorkedGroup2690FlowTest extends AbstractExplab2690FlowTest {

    @CriticalRegression
    @ParameterizedTest(name = "{0}")
    @MethodSource("differentConditionCases")
    @DisplayName("EXPLAB-2690-01..02. Разные conditionId: в REST остаётся только реально сработавшая группа")
    void mapperShouldReturnOnlyWorkedGroupAndNoTechnicalMain(WorkedGroupCase testCase) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configWithDifferentConditionPerGroup(version);
        String splittingId = splittingIdForRange(testCase.id(), SALT_2690, testCase.rangeFrom(), testCase.rangeTo());
        SplitRequestDto request = splitRequest(splittingId,
                object(SINGLE_OBJECT_ID,
                        param("groupA", "1", "INTEGER"),
                        param("groupB", "1", "INTEGER"),
                        param("groupC", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем MAPPER config EXPLAB-2690 с группами A/B/C и разными conditionId",
                        flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Выполняем split для диапазона " + testCase.id()
                                + ", spread=" + spread(SALT_2690, splittingId),
                        flow -> verifyDifferentConditionCase(split(flow, EndpointMode.MAPPER, request), request, version, testCase))
                .run();
    }

    @CriticalRegression
    @ParameterizedTest(name = "{0}")
    @MethodSource("sameConditionCases")
    @DisplayName("EXPLAB-2690-03. Один conditionId у A/B/C: выбирается фактически сработавшая группа")
    void mapperShouldDeterministicallyUseWorkedGroupWhenSeveralGroupsShareCondition(WorkedGroupCase testCase) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configWithSameConditionForAllGroups(version);
        String splittingId = splittingIdForRange(testCase.id(), SALT_2690, testCase.rangeFrom(), testCase.rangeTo());
        SplitRequestDto request = splitRequest(splittingId,
                object(SINGLE_OBJECT_ID, param("allGroups", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем MAPPER config: группы A/B/C привязаны к одному conditionId",
                        flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем детерминированный выбор фактически сработавшей группы " + testCase.expectedGroup(),
                        flow -> {
                            ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                            assertBasicResponseContract(response, request, version);
                            assertResultExp(response, SINGLE_OBJECT_ID, "MAIN", 269003L, 1,
                                    testCase.expectedGroup(), testCase.expectedGroup(),
                                    testCase.expectedActionType(), testCase.expectedResult());
                            assertResultExp(response, SINGLE_OBJECT_ID, "ALL", 269003L, 1,
                                    testCase.expectedGroup(), testCase.expectedGroup(),
                                    testCase.expectedActionType(), testCase.expectedResult());
                            assertMainHasNoExpFlags(response, SINGLE_OBJECT_ID);
                            assertAllExpFlagsHaveAlternativeValue(response, SINGLE_OBJECT_ID, "false");
                        })
                .run();
    }

    private void verifyDifferentConditionCase(ValidatableResponseWrapper response,
                                              SplitRequestDto request,
                                              long version,
                                              WorkedGroupCase testCase) {
        assertBasicResponseContract(response, request, version);
        assertSplittingResultsHaveUniqueObjectIds(response);

        if (testCase.expectedGroup() == null) {
            // Изменение EXPLAB-2690: технический MAIN с пустым resultExps больше не является валидным REST-ответом.
            assertObjectHasStrictlyEmptyResult(response, SINGLE_OBJECT_ID);
            assertRuleAbsent(response, SINGLE_OBJECT_ID, "MAIN");
            assertRuleAbsent(response, SINGLE_OBJECT_ID, "ALL");
            return;
        }

        assertResultExp(response, SINGLE_OBJECT_ID, "MAIN", 269001L,
                testCase.expectedConditionId(), testCase.expectedGroup(), testCase.expectedGroup(),
                testCase.expectedActionType(), testCase.expectedResult());
        assertResultExp(response, SINGLE_OBJECT_ID, "ALL", 269001L,
                testCase.expectedConditionId(), testCase.expectedGroup(), testCase.expectedGroup(),
                testCase.expectedActionType(), testCase.expectedResult());
        assertMainHasNoExpFlags(response, SINGLE_OBJECT_ID);
        assertAllExpFlagsHaveAlternativeValue(response, SINGLE_OBJECT_ID, "false");
        assertFilteredFlag(response, SINGLE_OBJECT_ID, false);
    }

    private LoadConfigRequestDto configWithDifferentConditionPerGroup(long version) {
        ExperimentDto experiment = experiment(269001,
                SALT_2690,
                List.of(
                        objectParamEqualsCondition(1, "groupA", "1", "INTEGER"),
                        objectParamEqualsCondition(2, "groupB", "1", "INTEGER"),
                        objectParamEqualsCondition(3, "groupC", "1", "INTEGER")),
                List.of(
                        groupWithDocResult("A", shares(0, 2500), 1, "0", "101"),
                        groupWithDocResult("B", shares(2500, 5000), 2, "1", "202"),
                        groupWithDocResult("C", shares(5000, 7500), 3, "3", "303")));
        return singleExperimentConfig(EndpointMode.MAPPER, version, experiment);
    }

    private LoadConfigRequestDto configWithSameConditionForAllGroups(long version) {
        ExperimentDto experiment = experiment(269003,
                SALT_2690,
                List.of(objectParamEqualsCondition(1, "allGroups", "1", "INTEGER")),
                List.of(
                        groupWithDocResult("A", shares(0, 2500), 1, "0", "101"),
                        groupWithDocResult("B", shares(2500, 5000), 1, "1", "202"),
                        groupWithDocResult("C", shares(5000, 7500), 1, "3", "303")));
        return singleExperimentConfig(EndpointMode.MAPPER, version, experiment);
    }

    private static Stream<Arguments> differentConditionCases() {
        return Stream.of(
                Arguments.of(new WorkedGroupCase("EXPLAB-2690-01-A", 0, 2500, "A", 1, "0", "101")),
                Arguments.of(new WorkedGroupCase("EXPLAB-2690-01-B", 2500, 5000, "B", 2, "1", "202")),
                Arguments.of(new WorkedGroupCase("EXPLAB-2690-01-C", 5000, 7500, "C", 3, "3", "303")),
                Arguments.of(new WorkedGroupCase("EXPLAB-2690-02-NO-MAIN", 7500, 10000, null, 0, null, null))
        );
    }

    private static Stream<Arguments> sameConditionCases() {
        return Stream.of(
                Arguments.of(new WorkedGroupCase("EXPLAB-2690-03-A", 0, 2500, "A", 1, "0", "101")),
                Arguments.of(new WorkedGroupCase("EXPLAB-2690-03-B", 2500, 5000, "B", 1, "1", "202")),
                Arguments.of(new WorkedGroupCase("EXPLAB-2690-03-C", 5000, 7500, "C", 1, "3", "303"))
        );
    }

    private record WorkedGroupCase(String id,
                                   int rangeFrom,
                                   int rangeTo,
                                   String expectedGroup,
                                   int expectedConditionId,
                                   String expectedActionType,
                                   String expectedResult) {
        @Override
        public String toString() {
            return id;
        }
    }
}
