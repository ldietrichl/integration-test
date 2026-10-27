package ru.sber.qa.experiments.EXPLAB_3109;

import config.environment.special.EnvironmentConfigWithExperimentLinks;
import config.extensions.WorkloadScenario;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.*;
import steps.flow.experiments.links.ExperimentLinksScenarioSteps;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithExperimentLinks.class)
@WorkloadScenario("experiment")
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("experiment-service-state")
public abstract class ExperimentLinks3109TestBase {
    protected ExperimentLinksScenarioSteps scenario;
    @BeforeEach void prepare(TestInfo info) throws Exception {
        int id = Integer.parseInt(info.getTestMethod().orElseThrow().getName().substring(4));
        scenario = new ExperimentLinksScenarioSteps(id);
        scenario.prepare();
    }
    @AfterEach void cleanup() throws Exception { if (scenario != null) scenario.close(); }
}
