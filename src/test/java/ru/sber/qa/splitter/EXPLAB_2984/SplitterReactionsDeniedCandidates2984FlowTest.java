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
@DisplayName("EXPLAB-2984 REACTIONS: allow-result-without-main=false")
public class SplitterReactionsDeniedCandidates2984FlowTest extends CandidateSelectionSteps {
    @Override protected boolean allowWithoutMain() { return false; }

    @CriticalRegression
    @ParameterizedTest(name = "2984-T20 / {0}")
    @MethodSource("support.splitter.cases.CandidateSelectionCases#deniedCases")
    void noInvalidWinnerWhenResultsWithoutMainDisabled(Case scenario, KafkaService kafka) { verify(scenario, kafka); }
}
