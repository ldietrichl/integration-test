package ru.sber.qa.splitter.EXPLAB_2690;
import steps.flow.splitter.workedgroup.MapperNoMainSteps;

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

import org.junit.jupiter.params.provider.MethodSource;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import util.support.SplitterVersionProvider;

import java.util.List;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("EXPLAB-2690. MAPPER: ALL без выбранного MAIN зависит от профиля")
@AnyConfigLoadMode
@org.junit.jupiter.api.Order(10)
public class SplitterMapperNoMain2690FlowTest extends MapperNoMainSteps {

    @CriticalRegression
    @ParameterizedTest(name = "{0}")
    @MethodSource("support.splitter.cases.MapperNoMainCases#noMainCases")
    @DisplayName("EXPLAB-2690-05. Некорректный/отсутствующий параметр приоритета: нет MAIN, ALL по профилю")
    void objectShouldLookUnlinkedWhenMapperCannotSelectMain(NoMainCase testCase) {
        long version = SplitterVersionProvider.next();
        ExperimentDto experiment = experiment(269005,
                SALT_2690,
                List.of(objectParamEqualsCondition(1, "segment", "2690", "INTEGER")),
                List.of(group("A", shares(0, 10000), List.of(resultFor(testCase)))));
        LoadConfigRequestDto config = singleExperimentConfig(EndpointMode.MAPPER, version, experiment);
        SplitRequestDto request = splitRequest(testCase.id() + "-" + version,
                object(SINGLE_OBJECT_ID, param("segment", "2690", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем MAPPER config: " + testCase.description(),
                        flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем отсутствие MAIN и связанные сработавшие группы по профилю", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertObjectWithoutMain(response, SINGLE_OBJECT_ID, java.util.Map.of(269005L, "A"));
                })
                .run();
    }
}
