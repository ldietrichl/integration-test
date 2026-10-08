package steps.flow.scheduler;

import config.environment.special.EnvironmentConfigWithScheduler;
import flow.SchedulerInfrastructureFlow;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.scheduler.AbstractSchedulerFlowTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;


/** Source-backed reusable flow steps; no JUnit scenario discovery here. */
public final class SchedulerPreflightSteps extends SchedulerScenarioSupport {
    public void infrastructureBaseline() {
        Allure.addAttachment("Назначение инфраструктурной проверки",
                "Проверка mTLS, ContainerService/Fabric8, готовности pod, HPA, ConfigMap и ResourceQuota. Стенд не изменяется.");
        new SchedulerInfrastructureDiagnosticSteps().regressionTransportBaseline();
        var infrastructure = new SchedulerInfrastructureFlow().infrastructureSteps();
        try (var control = infrastructure.workloadSteps().session("scheduler")) {
            control.requireReadOnlyRegressionBaseline();
        }
    }

    public void databaseBaseline() {
        Allure.addAttachment("Назначение проверки БД",
                "Вспомогательная проверка окружения, не отдельное требование scheduler. Без вставок, изменений и удаления данных.");

        getFlowWithDbRest()
                .step("Inspect scheduler schema through the framework explab client", flow ->
                        assertDoesNotThrow(
                                () -> flow.dbCustomSteps().schedulerBaselineSteps().requireSchema(settings()),
                                "Scheduler schema must match the configured baseline through the framework explab client"))
                .step("Read scheduler dictionaries, migrations and constraints", flow ->
                        assertDoesNotThrow(
                                () -> flow.dbCustomSteps().schedulerBaselineSteps().attachReferenceInventory(),
                                "Scheduler dictionaries and migration history must be accessible and non-empty"))
                .run();
    }
}
