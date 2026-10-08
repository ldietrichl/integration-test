package ru.sber.qa.experiments.EXPLAB_2972;

import config.environment.special.EnvironmentConfigWithStatusChange2972;
import config.services.core.StatusChange2972Settings;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ru.sber.qa.allure.Regression;
import java.util.stream.Stream;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithStatusChange2972.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("experiment-status-change")
@io.qameta.allure.TmsLink("EXPLAB-2972")
@Epic("Experiment service") @Feature("EXPLAB-2972: подготовленные сценарии")
public class StatusChange2972ManagedFlowTest extends AbstractStatusChange2972FlowTest {
    static Stream<org.junit.jupiter.params.provider.Arguments> scenarios() {
        StatusChange2972Settings settings = new StatusChange2972Settings();
        var selected = settings.selected("managed");
        if (!StatusChange2972Scenarios.MANAGED.containsAll(selected))
            throw new IllegalArgumentException("Only managed scenario ids are allowed in managed.cases");
        // Validate each scenario inside its first Flow step so Allure retains its identity on failure.
        return selected.stream().map(id -> org.junit.jupiter.params.provider.Arguments.of(id, StatusChange2972Catalog.get(id).title()));
    }

    @Regression
    @ParameterizedTest(name = "EXPLAB-2972 TP-{0}: {1}")
    @MethodSource("scenarios")
    void statusChange(int scenario, String title) { runScenario(scenario, true); }
}
