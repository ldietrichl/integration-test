package ru.sber.qa.splitter.explab2633;

import config.environment.EnvironmentConfigurationExample;
import dto.splitter.common.ParamDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.RuleDto;
import dto.splitter.split.SplitRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.splitter.EXPLAB_2633.AbstractExplab2633DuplicateConditionsFlowTest;
import util.support.SplitterVersionProvider;

import java.util.List;
import java.util.stream.Stream;

import static util.SplitterPrecalcAssertions.shouldBe200;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SplitterDuplicateParamsInsideAndGroupParameterizedFlowTest extends AbstractExplab2633DuplicateConditionsFlowTest {

    @CriticalRegression
    @ParameterizedTest(name = "{0}")
    @MethodSource("duplicateParamsInsideAndGroupCases")
    @DisplayName("SPL-02..SPL-07. Повтор paramCode внутри одной AND-группы")
    void duplicateParamInsideAndGroupShouldBeEvaluatedAsIndependentExpressions(DuplicateConditionCase testCase) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = duplicateRulesConfig(version, testCase.expId(), testCase.caseId(), testCase.rules());
        SplitRequestDto request = splitRequest(testCase.caseId(), object(testCase.objectId(),
                testCase.objectParams().toArray(new ParamDto[0])));

        getFlowWithRest()
                .step("Загружаем config: " + testCase.description(), flow -> loadConfigStep(flow, config))
                .step("Выполняем split: expectedMatch=" + testCase.expectedMatch(), flow -> {
                    var response = shouldBe200(split(flow, request));
                    assertSuccessfulSplitResponse(response, version);
                    if (testCase.expectedMatch()) {
                        assertObjectMatchedExperiment(response, testCase.objectId(), testCase.expId());
                    } else {
                        assertObjectDidNotMatchExperiment(response, testCase.objectId(), testCase.expId());
                    }
                })
                .run();
    }

    Stream<Arguments> duplicateParamsInsideAndGroupCases() {
        return Stream.of(
                Arguments.of(new DuplicateConditionCase(
                        "SPL-02",
                        "[[param1 = 2, param1 = 2]] => true",
                        263302,
                        andGroup(
                                objectIntRule("param1", "equal", "2"),
                                objectIntRule("param1", "equal", "2")),
                        objectParams(intParam("param1", "2")),
                        true)),
                Arguments.of(new DuplicateConditionCase(
                        "SPL-03",
                        "[[param1 = 2, param1 = 4]] => false",
                        263303,
                        andGroup(
                                objectIntRule("param1", "equal", "2"),
                                objectIntRule("param1", "equal", "4")),
                        objectParams(intParam("param1", "2")),
                        false)),
                Arguments.of(new DuplicateConditionCase(
                        "SPL-04",
                        "[[param1 = 2, param1 = 3, param2 = FFF]] => false",
                        263304,
                        andGroup(
                                objectIntRule("param1", "equal", "2"),
                                objectIntRule("param1", "equal", "3"),
                                objectStringRule("param2", "equal", "FFF")),
                        objectParams(intParam("param1", "2"), stringParam("param2", "FFF")),
                        false)),
                Arguments.of(new DuplicateConditionCase(
                        "SPL-05",
                        "[[param1 = 2, param1 in [2,3], param1 >= 1]] => true",
                        263305,
                        andGroup(
                                objectIntRule("param1", "equal", "2"),
                                objectIntRule("param1", "in", "2", "3"),
                                objectIntRule("param1", "more_equal", "1")),
                        objectParams(intParam("param1", "2")),
                        true)),
                Arguments.of(new DuplicateConditionCase(
                        "SPL-06",
                        "[[param1 = 2, param1 in [3,4], param1 >= 1]] => false",
                        263306,
                        andGroup(
                                objectIntRule("param1", "equal", "2"),
                                objectIntRule("param1", "in", "3", "4"),
                                objectIntRule("param1", "more_equal", "1")),
                        objectParams(intParam("param1", "2")),
                        false)),
                Arguments.of(new DuplicateConditionCase(
                        "SPL-07",
                        "[[paramBool = true, paramBool is_not_null]] => true",
                        263307,
                        andGroup(
                                objectBooleanRule("paramBool", "equal", "true"),
                                objectBooleanRule("paramBool", "is_not_null")),
                        objectParams(booleanParam("paramBool", "true")),
                        true))
        );
    }

    record DuplicateConditionCase(String caseId,
                                  String description,
                                  int expId,
                                  List<List<RuleDto>> rules,
                                  List<ParamDto> objectParams,
                                  boolean expectedMatch) {

        String objectId() {
            return expectedMatch ? OBJECT_ID : NEGATIVE_OBJECT_ID;
        }

        @Override
        public String toString() {
            return caseId + ". " + description;
        }
    }
}
