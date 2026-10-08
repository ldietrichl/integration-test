package ru.sber.qa.splitter.EXPLAB_3056;

import config.environment.EnvironmentConfigurationExample;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import steps.flow.splitter.workedgroup.MapperAlternativeMarkupSteps;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@Execution(ExecutionMode.SAME_THREAD)
@AnyConfigLoadMode
@Epic("Splitter") @Feature("EXPLAB-3056")
@DisplayName("EXPLAB-3056 REACTIONS: отсутствие альтернатив при раздельных и общих связях")
public class SplitterReactionsAlternative3056FlowTest extends MapperAlternativeMarkupSteps {
    @Override protected boolean reactions() { return true; }
    @ParameterizedTest(name="{0}")
    @MethodSource("support.splitter.cases.MapperAlternativeMarkupCases#reactionsControl")
    void reactionsDoesNotAcquireMapperFlags(Case scenario, KafkaService kafka) { verify(scenario,kafka); }
}
