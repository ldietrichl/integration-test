package ru.sber.qa.splitter.EXPLAB_2984;

import config.environment.EnvironmentConfigurationExample;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import steps.flow.splitter.workedgroup.CandidateSelectionSteps;
import support.splitter.cases.CandidateSelectionCases;
import java.util.List;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@Execution(ExecutionMode.SAME_THREAD)
@AnyConfigLoadMode
@Epic("Splitter")
@Feature("EXPLAB-2984")
@DisplayName("EXPLAB-2984 REACTIONS: кандидаты MAIN и отсутствие альтернативы")
public class SplitterReactionsCandidates2984FlowTest extends CandidateSelectionSteps {
    @CriticalRegression
    @ParameterizedTest(name = "{0}")
    @MethodSource("support.splitter.cases.CandidateSelectionCases#reactions")
    void eligibleCandidatesAndFlags(Case scenario, KafkaService kafka) { verify(scenario, kafka); }

    @ParameterizedTest(name = "{0}")
    @MethodSource("support.splitter.cases.CandidateSelectionCases#boundaries")
    void distributionBoundaries(Case scenario, KafkaService kafka) { verify(scenario, kafka); }

    @CriticalRegression
    @ParameterizedTest(name = "{0}")
    @MethodSource("support.splitter.cases.CandidateSelectionCases#flagMultiplicity")
    void noAlternativeEvenWithManyLinkedGroups(Case scenario, KafkaService kafka) { verify(scenario, kafka); }

    @Test
    @DisplayName("2984-T12: результаты независимы при совместном и отдельном запросе объектов")
    void independentObjects(KafkaService kafka) { verifyIndependence(CandidateSelectionCases.complex(), kafka); }

    @ParameterizedTest(name = "2984-T13 / {0}")
    @MethodSource("support.splitter.cases.CandidateSelectionCases#permutations")
    void permutationAndRepeatedRequests(Case scenario, KafkaService kafka) { verifyPermutation(scenario, kafka); }

    @Test
    @DisplayName("2984-T15: A -> B -> NONE -> A без перезагрузки конфигурации")
    void changingWorkedGroup(KafkaService kafka) {
        verifySequence(CandidateSelectionCases.changingGroups(), kafka, false, false);
    }

    @Test
    @DisplayName("2984-T21: новая версия меняет связь группы с объектом")
    void updatedBinding(KafkaService kafka) {
        verifySequence(List.of(CandidateSelectionCases.single("2984-T21-BEFORE", "B"),
                CandidateSelectionCases.moved()), kafka, false, false);
    }
}
