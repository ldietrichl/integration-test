package ru.sber.qa.scheduler.infrastructure;

import config.environment.special.EnvironmentConfigWithSchedulerInfrastructure;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.*;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import config.extensions.scheduler.*;

@ExtendWith({SchedulerInfrastructureCondition.class, PerfeccionistaExtension.class, SchedulerInfrastructureExtension.class})
@SetEnvironmentConfiguration(EnvironmentConfigWithSchedulerInfrastructure.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("scheduler-service-regression")
@Timeout(value = 40, unit = java.util.concurrent.TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SAME_THREAD)
@Epic("Scheduler service")
@Feature("Dedicated stand workload diagnostics")
@UsesScenarioSteps(steps.flow.scheduler.SchedulerWorkloadDiagnosticSteps.class)
public class SchedulerWorkloadDiagnosticFlowTest {
    @Test
    @DisplayName("SCH-OPS-001. Удаление одного pod, новый UID, Ready и свежий HTTP-туннель")
    void ops001() {
        ru.sber.qa.flow.FlowRunner.flowRunnerFor(flow.SchedulerInfrastructureFlow.class)
                .step("SCH-OPS-001. Удаление одного pod, новый UID, Ready и свежий HTTP-туннель", flow ->
                        new steps.flow.scheduler.SchedulerWorkloadDiagnosticSteps().ops001()).run();
    }

    @Test
    @DisplayName("SCH-OPS-002. ConfigMap: исходная версия в артефактах и привязка к Deployment без изменений")
    void ops002() {
        ru.sber.qa.flow.FlowRunner.flowRunnerFor(flow.SchedulerInfrastructureFlow.class)
                .step("SCH-OPS-002. ConfigMap: исходная версия в артефактах и привязка к Deployment без изменений", flow ->
                        new steps.flow.scheduler.SchedulerWorkloadDiagnosticSteps().ops002()).run();
    }

    @Test
    @DisplayName("SCH-OPS-003. ConfigMap: GET-ассерты, пересоздание pod после изменения и после восстановления")
    void ops003() {
        ru.sber.qa.flow.FlowRunner.flowRunnerFor(flow.SchedulerInfrastructureFlow.class)
                .step("SCH-OPS-003. ConfigMap: GET-ассерты, пересоздание pod после изменения и после восстановления", flow ->
                        new steps.flow.scheduler.SchedulerWorkloadDiagnosticSteps().ops003()).run();
    }

    @Test
    @DisplayName("SCH-OPS-004. Число реплик: изменение 1..2, Ready и восстановление без HPA")
    void ops004() {
        ru.sber.qa.flow.FlowRunner.flowRunnerFor(flow.SchedulerInfrastructureFlow.class)
                .step("SCH-OPS-004. Число реплик: изменение 1..2, Ready и восстановление без HPA", flow ->
                        new steps.flow.scheduler.SchedulerWorkloadDiagnosticSteps().ops004()).run();
    }
}
