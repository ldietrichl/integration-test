package ru.sber.qa.splitter.EXPLAB_2690;
import steps.flow.splitter.workedgroup.MapperAlternativeSteps;

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

import ru.sber.qa.splitter.support.SplitterTestProfileOnly;
import util.support.SplitterVersionProvider;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("EXPLAB-2690. MAPPER: MAIN альтернативы использует связанную с объектом группу")
@AnyConfigLoadMode
@org.junit.jupiter.api.Order(10)
public class SplitterMapperAlternative2690FlowTest extends MapperAlternativeSteps {

    @CriticalRegression
    @ParameterizedTest(name = "{0}")
    @MethodSource("support.splitter.cases.MapperAlternativeCases#alternativeCases")
    @SplitterTestProfileOnly("mapper-alternative-contract")
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
}
