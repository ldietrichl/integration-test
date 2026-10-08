package ru.sber.qa.splitter.EXPLAB_2690;
import steps.flow.splitter.workedgroup.MapperWorkedGroupSteps;

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
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import util.support.SplitterVersionProvider;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("EXPLAB-2690. MAPPER: MAIN, finalExpGroup и очистка ALL")
@AnyConfigLoadMode
@org.junit.jupiter.api.Order(10)
public class SplitterMapperWorkedGroup2690FlowTest extends MapperWorkedGroupSteps {

    @CriticalRegression
    @ParameterizedTest(name = "{0}")
    @MethodSource("support.splitter.cases.MapperWorkedGroupCases#differentConditionCases")
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
    @MethodSource("support.splitter.cases.MapperWorkedGroupCases#sameConditionCases")
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
}
