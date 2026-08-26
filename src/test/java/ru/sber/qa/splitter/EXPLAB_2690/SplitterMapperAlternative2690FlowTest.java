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
@DisplayName("EXPLAB-2690. MAPPER: MAIN альтернативы использует связанную с объектом группу")
public class SplitterMapperAlternative2690FlowTest extends AbstractExplab2690FlowTest {

    @CriticalRegression
    @ParameterizedTest(name = "{0}")
    @MethodSource("alternativeCases")
    @DisplayName("EXPLAB-2690-04. expGroup — группа связи объекта, finalExpGroup — реально сработавшая группа")
    void alternativeMainShouldUseObjectLinkedGroupAndExposeActualWorkedGroup(AlternativeCase testCase) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = alternativeConfig(version);
        String splittingId = splittingIdForRange(testCase.id(), SALT_2690, testCase.rangeFrom(), testCase.rangeTo());
        SplitRequestDto request = splitRequest(splittingId,
                object(LEFT_OBJECT_ID, param("left", "1", "INTEGER")),
                object(RIGHT_OBJECT_ID, param("right", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем MAPPER config с A для левого объекта и B для правого",
                        flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Выполняем split: сработавшая группа=" + testCase.workedGroup(),
                        flow -> verifyAlternativeResponse(split(flow, EndpointMode.MAPPER, request), request, version, testCase))
                .run();
    }

    private void verifyAlternativeResponse(ValidatableResponseWrapper response,
                                           SplitRequestDto request,
                                           long version,
                                           AlternativeCase testCase) {
        assertBasicResponseContract(response, request, version);
        assertSplittingResultsHaveUniqueObjectIds(response);

        assertResultExp(response,
                testCase.normalObjectId(),
                "MAIN",
                269004L,
                testCase.normalConditionId(),
                testCase.workedGroup(),
                testCase.workedGroup(),
                testCase.normalActionType(),
                testCase.normalResult());
        assertResultExp(response,
                testCase.normalObjectId(),
                "ALL",
                269004L,
                testCase.normalConditionId(),
                testCase.workedGroup(),
                testCase.workedGroup(),
                testCase.normalActionType(),
                testCase.normalResult());

        // Главное изменение EXPLAB-2690: в альтернативном MAIN expGroup/resultParams берутся
        // из группы, через которую эксперимент связан с текущим объектом, а finalExpGroup
        // отдельно сообщает реально сработавшую группу распределения.
        assertResultExp(response,
                testCase.alternativeObjectId(),
                "MAIN",
                269004L,
                testCase.alternativeConditionId(),
                testCase.linkedGroup(),
                testCase.workedGroup(),
                testCase.alternativeActionType(),
                testCase.alternativeResult());
        assertMainHasNoExpFlags(response, testCase.normalObjectId());
        assertMainHasNoExpFlags(response, testCase.alternativeObjectId());

        // Несработавшая связанная группа expGroup != finalExpGroup остаётся в полном КАП-логе,
        // но должна быть удалена из публичного ALL.
        assertRuleAbsent(response, testCase.alternativeObjectId(), "ALL");
        assertAllExpFlagsHaveAlternativeValue(response, testCase.normalObjectId(), "false");
    }

    private LoadConfigRequestDto alternativeConfig(long version) {
        ExperimentDto experiment = experiment(269004,
                SALT_2690,
                List.of(
                        objectParamEqualsCondition(1, "left", "1", "INTEGER"),
                        objectParamEqualsCondition(2, "right", "1", "INTEGER")),
                List.of(
                        groupWithDocResult("A", shares(0, 5000), 1, "1", "101"),
                        groupWithDocResult("B", shares(5000, 10000), 2, "3", "202")));
        return singleExperimentConfig(EndpointMode.MAPPER, version, experiment);
    }

    private static Stream<Arguments> alternativeCases() {
        return Stream.of(
                Arguments.of(new AlternativeCase(
                        "EXPLAB-2690-04-A-WORKED",
                        0, 5000,
                        "A",
                        LEFT_OBJECT_ID, 1, "1", "101",
                        RIGHT_OBJECT_ID, 2, "B", "3", "202")),
                Arguments.of(new AlternativeCase(
                        "EXPLAB-2690-04-B-WORKED",
                        5000, 10000,
                        "B",
                        RIGHT_OBJECT_ID, 2, "3", "202",
                        LEFT_OBJECT_ID, 1, "A", "1", "101"))
        );
    }

    private record AlternativeCase(String id,
                                   int rangeFrom,
                                   int rangeTo,
                                   String workedGroup,
                                   String normalObjectId,
                                   int normalConditionId,
                                   String normalActionType,
                                   String normalResult,
                                   String alternativeObjectId,
                                   int alternativeConditionId,
                                   String linkedGroup,
                                   String alternativeActionType,
                                   String alternativeResult) {
        @Override
        public String toString() {
            return id;
        }
    }
}
