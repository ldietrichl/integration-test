package ru.sber.qa.splitter.EXPLAB_2690;
import steps.flow.splitter.workedgroup.ReactionsWorkedGroupSteps;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import config.environment.EnvironmentConfigurationExample;

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

import org.junit.jupiter.params.provider.MethodSource;
import ru.sber.qa.allure.CriticalRegression;

import util.support.SplitterVersionProvider;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("EXPLAB-2690. REACTIONS: нет альтернативы для чужой сработавшей группы")
@AnyConfigLoadMode
@org.junit.jupiter.api.Order(30)
public class SplitterReactionsNoAlternative2690FlowTest extends ReactionsWorkedGroupSteps {

    @CriticalRegression
    @ParameterizedTest(name = "{0}")
    @MethodSource("support.splitter.cases.ReactionsWorkedGroupCases#reactionsCases")
    @DisplayName("EXPLAB-2690-06. REACTIONS: MAIN только у объекта своей сработавшей группы")
    void reactionsShouldUseWorkedFinalGroupForLinkedObjects(ReactionsCase testCase) {
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
}
