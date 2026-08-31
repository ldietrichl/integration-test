package ru.sber.qa.splitter.tests_v9.document;

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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.tests_v9.common.AbstractSplitterV9FlowTest;
import util.support.SplitterVersionProvider;

import java.util.List;
import java.util.stream.Stream;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("Tests-v9 / EXPLAB-2690. REACTIONS: итоговый эксперимент по layerPriority и expId")
@AnyConfigLoadMode
public class SplitterV9DocumentReactionsLayerMatrixFlowTest extends AbstractSplitterV9FlowTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("reactionCases")
    void reactionsLayerCasesShouldReturnSelectedExperimentWithMaximumLayerPriority(ReactionsCase testCase) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = reactionsConfig(version);
        String splittingId = splittingIdForRange(testCase.id(), V9_SALT, testCase.rangeFrom(), testCase.rangeTo());
        SplitRequestDto request = splitRequest(splittingId,
                object(OBJECT_REACTIONS, param("segment", "v9", "STRING")));

        getFlowWithRest()
                .step("Загружаем REACTIONS config для " + testCase.id(),
                        flow -> loadConfig(flow, EndpointMode.REACTIONS, config))
                .step("Проверяем finalExpByLayerAndId для " + testCase.id()
                                + ": splittingId=" + splittingId + ", spread=" + spread(V9_SALT, splittingId),
                        flow -> {
                            ValidatableResponseWrapper response = split(flow, EndpointMode.REACTIONS, request);
                            assertBasicResponseContract(response, request, version);
                            assertSplittingResultsHaveUniqueObjectIds(response);
                            verifyReactionsCase(response, testCase, spread(V9_SALT, splittingId));
                            assertNoAlternativeTrueAnywhere(response);
                        })
                .run();
    }

    private void verifyReactionsCase(ValidatableResponseWrapper response,
                                     ReactionsCase testCase,
                                     long expectedSpread) {
        if (testCase.expectedMain().length == 0) {
            assertObjectEmptyOrAbsent(response, OBJECT_REACTIONS);
            if (hasObject(response, OBJECT_REACTIONS)) {
                assertRuleAbsent(response, OBJECT_REACTIONS, "MAIN");
                assertRuleAbsent(response, OBJECT_REACTIONS, "ALL");
            }
            return;
        }

        // В текущем SDK finalExpByLayerAndId сначала оставляет max layerPriority,
        // затем выбирает один experiment по min/max expId из proc-params.
        assertRuleExpIdsExactly(response, OBJECT_REACTIONS, "MAIN", testCase.expectedMain());
        assertRuleExpsHaveMandatoryFields(response, OBJECT_REACTIONS, "MAIN");
        assertRuleExpsHaveSpreadValue(response, OBJECT_REACTIONS, "MAIN", expectedSpread);
        assertRuleExpsUseWorkedGroups(response, OBJECT_REACTIONS, "MAIN");
        assertObjectFlagsEmpty(response, OBJECT_REACTIONS);
        if (findRule(response, OBJECT_REACTIONS, "ALL", false) != null) {
            assertRuleExpIdsExactly(response, OBJECT_REACTIONS, "ALL", testCase.expectedAll());
            assertAllResponseRowsAreWorkedGroups(response, OBJECT_REACTIONS);
            assertAllExpFlagsHaveAlternativeValue(response, OBJECT_REACTIONS, "false");
        }
    }

    private LoadConfigRequestDto reactionsConfig(long version) {
        return configFor(EndpointMode.REACTIONS, version,
                reactionExperiment(1, 1, 1, 0, 2500, "1"),
                reactionExperiment(2, 2, 2, 0, 7500, "2"),
                reactionExperiment(3, 2, 2, 0, 2500, "3"),
                reactionExperiment(4, 3, 3, 0, 5000, "4"),
                reactionExperiment(5, 3, 3, 0, 7500, "5"),
                reactionExperiment(6, 3, 3, 0, 5000, "6"));
    }

    private ExperimentDto reactionExperiment(int expId,
                                             int layerId,
                                             int layerPriority,
                                             int shareFrom,
                                             int shareTo,
                                             String resultValue) {
        return layeredExperiment(expId,
                V9_SALT,
                layerId,
                layerPriority,
                List.of(objectParamEqualsCondition(1, "segment", "v9", "STRING")),
                List.of(groupWithDocResult("A", shareFrom, shareTo, 1, "1", resultValue)));
    }

    private static Stream<Arguments> reactionCases() {
        return Stream.of(
                Arguments.of(new ReactionsCase("SPL-V9-T03-REACT-00-25", 0, 2500,
                        new Long[]{4L},
                        new Long[]{1L, 2L, 3L, 4L, 5L, 6L})),
                Arguments.of(new ReactionsCase("SPL-V9-T03-REACT-25-50", 2500, 5000,
                        new Long[]{4L},
                        new Long[]{2L, 4L, 5L, 6L})),
                Arguments.of(new ReactionsCase("SPL-V9-T03-REACT-50-75", 5000, 7500,
                        new Long[]{5L},
                        new Long[]{2L, 5L})),
                Arguments.of(new ReactionsCase("SPL-V9-T03-REACT-75-100", 7500, 10000,
                        new Long[]{},
                        new Long[]{}))
        );
    }

    private record ReactionsCase(String id,
                                 int rangeFrom,
                                 int rangeTo,
                                 Long[] expectedMain,
                                 Long[] expectedAll) {
        @Override
        public String toString() {
            return id;
        }
    }
}
