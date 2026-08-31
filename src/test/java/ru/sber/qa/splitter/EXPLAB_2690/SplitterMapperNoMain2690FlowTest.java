package ru.sber.qa.splitter.EXPLAB_2690;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.SplittingResultDto;
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
@DisplayName("EXPLAB-2690. MAPPER: объект без выбранного MAIN выглядит несвязанным")
@AnyConfigLoadMode
public class SplitterMapperNoMain2690FlowTest extends AbstractExplab2690FlowTest {

    @CriticalRegression
    @ParameterizedTest(name = "{0}")
    @MethodSource("noMainCases")
    @DisplayName("EXPLAB-2690-05. Некорректный/отсутствующий параметр приоритета не оставляет MAIN/ALL")
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
                .step("Проверяем строгий empty-contract EXPLAB-2690", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertObjectHasStrictlyEmptyResult(response, SINGLE_OBJECT_ID);
                    assertRuleAbsent(response, SINGLE_OBJECT_ID, "MAIN");
                    assertRuleAbsent(response, SINGLE_OBJECT_ID, "ALL");
                })
                .run();
    }

    private SplittingResultDto resultFor(NoMainCase testCase) {
        return switch (testCase.mode()) {
            case MISSING_ACTION_TYPE -> resultWithParams(1, param("result", "500", "INTEGER"));
            case UNKNOWN_ACTION_TYPE -> resultWithParams(1,
                    param("actionType", "99", "INTEGER"),
                    param("result", "500", "INTEGER"));
            case WRONG_ACTION_TYPE_DATA_TYPE -> resultWithParams(1,
                    param("actionType", "1", "STRING"),
                    param("result", "500", "INTEGER"));
            case EMPTY_RESULT_PARAMS -> resultWithParams(1);
        };
    }

    private static Stream<Arguments> noMainCases() {
        return Stream.of(
                Arguments.of(new NoMainCase(
                        "EXPLAB-2690-05-MISSING-ACTION-TYPE",
                        NoMainMode.MISSING_ACTION_TYPE,
                        "сработавшая группа не содержит actionType")),
                Arguments.of(new NoMainCase(
                        "EXPLAB-2690-05-UNKNOWN-ACTION-TYPE",
                        NoMainMode.UNKNOWN_ACTION_TYPE,
                        "actionType отсутствует в values-map mapperFinalExp")),
                Arguments.of(new NoMainCase(
                        "EXPLAB-2690-05-WRONG-ACTION-TYPE-DATA-TYPE",
                        NoMainMode.WRONG_ACTION_TYPE_DATA_TYPE,
                        "actionType имеет тип STRING вместо INTEGER")),
                Arguments.of(new NoMainCase(
                        "EXPLAB-2690-05-EMPTY-RESULT-PARAMS",
                        NoMainMode.EMPTY_RESULT_PARAMS,
                        "resultParams сработавшей группы пуст"))
        );
    }

    private enum NoMainMode {
        MISSING_ACTION_TYPE,
        UNKNOWN_ACTION_TYPE,
        WRONG_ACTION_TYPE_DATA_TYPE,
        EMPTY_RESULT_PARAMS
    }

    private record NoMainCase(String id, NoMainMode mode, String description) {
        @Override
        public String toString() {
            return id;
        }
    }
}
