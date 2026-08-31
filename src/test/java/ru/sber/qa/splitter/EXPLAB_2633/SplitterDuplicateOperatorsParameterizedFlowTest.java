package ru.sber.qa.splitter.EXPLAB_2633;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
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
import util.support.SplitterVersionProvider;

import java.util.List;
import java.util.stream.Stream;

import static util.SplitterPrecalcAssertions.shouldBe200;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AnyConfigLoadMode
public class SplitterDuplicateOperatorsParameterizedFlowTest extends AbstractExplab2633DuplicateConditionsFlowTest {

    @CriticalRegression
    @ParameterizedTest(name = "{0}")
    @MethodSource("duplicateOperatorCases")
    @DisplayName("SPL-13..SPL-25. Дубли paramCode внутри AND-группы с разными операторами")
    void duplicateParamWithDifferentOperatorsShouldBeEvaluatedAsIndependentExpressions(DuplicateOperatorCase testCase) {
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

    Stream<Arguments> duplicateOperatorCases() {
        return Stream.of(
                Arguments.of(new DuplicateOperatorCase(
                        "SPL-13",
                        "[[param1 >= 2, param1 <= 5]] => true",
                        263313,
                        andGroup(
                                objectIntRule("param1", "more_equal", "2"),
                                objectIntRule("param1", "less_equal", "5")),
                        objectParams(intParam("param1", "2")),
                        true)),
                Arguments.of(new DuplicateOperatorCase(
                        "SPL-14",
                        "[[param1 > 2, param1 < 5]] => false",
                        263314,
                        andGroup(
                                objectIntRule("param1", "more", "2"),
                                objectIntRule("param1", "less", "5")),
                        objectParams(intParam("param1", "2")),
                        false)),
                Arguments.of(new DuplicateOperatorCase(
                        "SPL-15",
                        "[[param1 in [2,3], param1 not_in [4,5]]] => true",
                        263315,
                        andGroup(
                                objectIntRule("param1", "in", "2", "3"),
                                objectIntRule("param1", "not_in", "4", "5")),
                        objectParams(intParam("param1", "2")),
                        true)),
                Arguments.of(new DuplicateOperatorCase(
                        "SPL-16",
                        "[[param1 in [2,3], param1 not_in [2,5]]] => false",
                        263316,
                        andGroup(
                                objectIntRule("param1", "in", "2", "3"),
                                objectIntRule("param1", "not_in", "2", "5")),
                        objectParams(intParam("param1", "2")),
                        false)),
                Arguments.of(new DuplicateOperatorCase(
                        "SPL-17",
                        "[[paramStr like ABC, paramStr like_any [ABC,XYZ]]] => true",
                        263317,
                        andGroup(
                                objectStringRule("paramStr", "like", "ABC"),
                                objectStringRule("paramStr", "like_any", "ABC", "XYZ")),
                        objectParams(stringParam("paramStr", "ABCDEF")),
                        true)),
                Arguments.of(new DuplicateOperatorCase(
                        "SPL-18",
                        "[[paramStr like ABC, paramStr not_like ABC]] => false",
                        263318,
                        andGroup(
                                objectStringRule("paramStr", "like", "ABC"),
                                objectStringRule("paramStr", "not_like", "ABC")),
                        objectParams(stringParam("paramStr", "ABCDEF")),
                        false)),
                Arguments.of(new DuplicateOperatorCase(
                        "SPL-19",
                        "[[param1 is_not_null, param1 = 2]] => true",
                        263319,
                        andGroup(
                                objectIntRule("param1", "is_not_null"),
                                objectIntRule("param1", "equal", "2")),
                        objectParams(intParam("param1", "2")),
                        true)),
                Arguments.of(new DuplicateOperatorCase(
                        "SPL-20",
                        "[[param1 is_null, param1 = 2]] => false",
                        263320,
                        andGroup(
                                objectIntRule("param1", "is_null"),
                                objectIntRule("param1", "equal", "2")),
                        objectParams(intParam("param1", "2")),
                        false)),
                Arguments.of(new DuplicateOperatorCase(
                        "SPL-21",
                        "[[paramDate >= 2026-01-01, paramDate <= 2026-12-31]] => true",
                        263321,
                        andGroup(
                                objectDateRule("paramDate", "more_equal", "2026-01-01"),
                                objectDateRule("paramDate", "less_equal", "2026-12-31")),
                        objectParams(dateParam("paramDate", "2026-06-02")),
                        true)),
                Arguments.of(new DuplicateOperatorCase(
                        "SPL-22",
                        "[[paramNumber > 10.5, paramNumber < 20.5]] => true",
                        263322,
                        andGroup(
                                objectNumberRule("paramNumber", "more", "10.5"),
                                objectNumberRule("paramNumber", "less", "20.5")),
                        objectParams(numberParam("paramNumber", "15.1")),
                        true)),
                Arguments.of(new DuplicateOperatorCase(
                        "SPL-23",
                        "[[param1 not_equal 4, param1 = 2]] => true",
                        263323,
                        andGroup(
                                objectIntRule("param1", "not_equal", "4"),
                                objectIntRule("param1", "equal", "2")),
                        objectParams(intParam("param1", "2")),
                        true)),
                Arguments.of(new DuplicateOperatorCase(
                        "SPL-24",
                        "[[paramStr like ABC, paramStr not_like_any [XYZ,QQQ]]] => true",
                        263324,
                        andGroup(
                                objectStringRule("paramStr", "like", "ABC"),
                                objectStringRule("paramStr", "not_like_any", "XYZ", "QQQ")),
                        objectParams(stringParam("paramStr", "ABCDEF")),
                        true)),
                Arguments.of(new DuplicateOperatorCase(
                        "SPL-25",
                        "[[paramDateTime >= 2026-06-02 00:00:00.000000, paramDateTime <= 2026-06-02 23:59:59.000000]] => true",
                        263325,
                        andGroup(
                                objectDateTimeRule("paramDateTime", "more_equal", "2026-06-02 00:00:00.000000"),
                                objectDateTimeRule("paramDateTime", "less_equal", "2026-06-02 23:59:59.000000")),
                        objectParams(dateTimeParam("paramDateTime", "2026-06-02 12:30:00.000000")),
                        true))
        );
    }

    record DuplicateOperatorCase(String caseId,
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
