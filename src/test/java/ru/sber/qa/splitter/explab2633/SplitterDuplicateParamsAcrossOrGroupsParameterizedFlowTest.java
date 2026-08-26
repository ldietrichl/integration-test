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
public class SplitterDuplicateParamsAcrossOrGroupsParameterizedFlowTest extends AbstractExplab2633DuplicateConditionsFlowTest {

    @CriticalRegression
    @ParameterizedTest(name = "{0}")
    @MethodSource("duplicateParamsAcrossOrGroupsCases")
    @DisplayName("SPL-08..SPL-12. Повтор paramCode в разных OR-группах")
    void duplicateParamAcrossOrGroupsShouldBeEvaluatedByOrLogic(DuplicateOrCase testCase) {
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

    Stream<Arguments> duplicateParamsAcrossOrGroupsCases() {
        return Stream.of(
                Arguments.of(new DuplicateOrCase(
                        "SPL-08",
                        "[[param1 = 2], [param1 = 2]] => true",
                        263308,
                        orGroups(
                                groupRules(objectIntRule("param1", "equal", "2")),
                                groupRules(objectIntRule("param1", "equal", "2"))),
                        objectParams(intParam("param1", "2")),
                        true)),
                Arguments.of(new DuplicateOrCase(
                        "SPL-09",
                        "[[param1 = 2], [param1 = 4]] => true",
                        263309,
                        orGroups(
                                groupRules(objectIntRule("param1", "equal", "2")),
                                groupRules(objectIntRule("param1", "equal", "4"))),
                        objectParams(intParam("param1", "2")),
                        true)),
                Arguments.of(new DuplicateOrCase(
                        "SPL-10",
                        "[[param1 = 2, param2 = FFF], [param1 = 3, param6 = uuu]] => true",
                        263310,
                        orGroups(
                                groupRules(
                                        objectIntRule("param1", "equal", "2"),
                                        objectStringRule("param2", "equal", "FFF")),
                                groupRules(
                                        objectIntRule("param1", "equal", "3"),
                                        objectStringRule("param6", "equal", "uuu"))),
                        objectParams(intParam("param1", "2"), stringParam("param2", "FFF"), stringParam("param6", "uuu")),
                        true)),
                Arguments.of(new DuplicateOrCase(
                        "SPL-11",
                        "[[param1 = 1], [param1 = 2], [param1 = 3]] => true",
                        263311,
                        orGroups(
                                groupRules(objectIntRule("param1", "equal", "1")),
                                groupRules(objectIntRule("param1", "equal", "2")),
                                groupRules(objectIntRule("param1", "equal", "3"))),
                        objectParams(intParam("param1", "2")),
                        true)),
                Arguments.of(new DuplicateOrCase(
                        "SPL-12",
                        "[[param1 = 1], [param1 = 2], [param1 = 3]] => false",
                        263312,
                        orGroups(
                                groupRules(objectIntRule("param1", "equal", "1")),
                                groupRules(objectIntRule("param1", "equal", "2")),
                                groupRules(objectIntRule("param1", "equal", "3"))),
                        objectParams(intParam("param1", "8")),
                        false))
        );
    }

    record DuplicateOrCase(String caseId,
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
