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
@DisplayName("EXPLAB-2690. REACTIONS: группы другого объекта не образуют альтернативный MAIN")
public class SplitterReactionsNoAlternative2690FlowTest extends AbstractExplab2690FlowTest {

    @CriticalRegression
    @ParameterizedTest(name = "{0}")
    @MethodSource("reactionsCases")
    @DisplayName("EXPLAB-2690-06. REACTIONS выбирает MAIN только при срабатывании группы, связанной с текущим объектом")
    void reactionsShouldNotSelectAlternativeMain(ReactionsCase testCase) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = reactionsAlternativeTopologyConfig(version);
        String splittingId = splittingIdForRange(testCase.id(), SALT_2690, testCase.rangeFrom(), testCase.rangeTo());
        SplitRequestDto request = splitRequest(splittingId,
                object(LEFT_OBJECT_ID, param("left", "1", "INTEGER")),
                object(RIGHT_OBJECT_ID, param("right", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем REACTIONS config с разными группами для двух объектов",
                        flow -> loadConfig(flow, EndpointMode.REACTIONS, config))
                .step("Выполняем reactions split: реально сработала группа " + testCase.workedGroup(),
                        flow -> verifyReactionsResponse(split(flow, EndpointMode.REACTIONS, request), request, version, testCase))
                .run();
    }

    private void verifyReactionsResponse(ValidatableResponseWrapper response,
                                         SplitRequestDto request,
                                         long version,
                                         ReactionsCase testCase) {
        assertBasicResponseContract(response, request, version);
        assertSplittingResultsHaveUniqueObjectIds(response);

        assertResultExp(response,
                testCase.matchedObjectId(),
                "MAIN",
                269006L,
                testCase.matchedConditionId(),
                testCase.workedGroup(),
                testCase.workedGroup(),
                testCase.expectedActionType(),
                testCase.expectedResult());
        assertEveryRuleExpUsesWorkedGroup(response, testCase.matchedObjectId(), "MAIN");
        assertMainHasNoExpFlags(response, testCase.matchedObjectId());

        // Второй объект связан с этим же экспериментом, но не со сработавшей группой.
        // Для REACTIONS это не альтернатива: MAIN не должен формироваться.
        assertObjectStrictlyEmptyOrAbsent(response, testCase.nonMatchedObjectId());
        if (hasObject(response, testCase.nonMatchedObjectId())) {
            assertRuleAbsent(response, testCase.nonMatchedObjectId(), "MAIN");
            assertRuleAbsent(response, testCase.nonMatchedObjectId(), "ALL");
        }
        assertNoAlternativeTrueAnywhere(response);
    }

    private LoadConfigRequestDto reactionsAlternativeTopologyConfig(long version) {
        ExperimentDto experiment = experiment(269006,
                SALT_2690,
                List.of(
                        objectParamEqualsCondition(1, "left", "1", "INTEGER"),
                        objectParamEqualsCondition(2, "right", "1", "INTEGER")),
                List.of(
                        groupWithDocResult("A", shares(0, 5000), 1, "1", "101"),
                        groupWithDocResult("B", shares(5000, 10000), 2, "3", "202")));
        return singleExperimentConfig(EndpointMode.REACTIONS, version, experiment);
    }

    private static Stream<Arguments> reactionsCases() {
        return Stream.of(
                Arguments.of(new ReactionsCase(
                        "EXPLAB-2690-06-A-WORKED", 0, 5000, "A",
                        LEFT_OBJECT_ID, 1, "1", "101", RIGHT_OBJECT_ID)),
                Arguments.of(new ReactionsCase(
                        "EXPLAB-2690-06-B-WORKED", 5000, 10000, "B",
                        RIGHT_OBJECT_ID, 2, "3", "202", LEFT_OBJECT_ID))
        );
    }

    private record ReactionsCase(String id,
                                 int rangeFrom,
                                 int rangeTo,
                                 String workedGroup,
                                 String matchedObjectId,
                                 int matchedConditionId,
                                 String expectedActionType,
                                 String expectedResult,
                                 String nonMatchedObjectId) {
        @Override
        public String toString() {
            return id;
        }
    }
}
