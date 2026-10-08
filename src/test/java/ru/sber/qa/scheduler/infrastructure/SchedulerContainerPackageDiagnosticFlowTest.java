package ru.sber.qa.scheduler.infrastructure;

import config.environment.special.EnvironmentConfigWithContainerDiagnostics;
import config.extensions.infrastructure.ContainerPackageDiagnosticExtension;
import flow.ContainerPackageDiagnosticFlow;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.flow.FlowRunner;

@ExtendWith({ContainerPackageDiagnosticExtension.class, PerfeccionistaExtension.class})
@SetEnvironmentConfiguration(EnvironmentConfigWithContainerDiagnostics.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("scheduler-service-regression")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 900, threadMode = Timeout.ThreadMode.SAME_THREAD)
@Epic("Scheduler service")
@Feature("ContainerService package and OpenShift authentication diagnostics")
@DisplayName("ContainerService package: corporate diagnostics")
@config.extensions.scheduler.UsesScenarioSteps(steps.container.ContainerPackageDiagnosticSteps.class)
public class SchedulerContainerPackageDiagnosticFlowTest {
    @Test @Order(1)
    @DisplayName("CSP-001. Local kubeconfig, credential presence and strict TLS")
    void pkg001() {
        FlowRunner.flowRunnerFor(ContainerPackageDiagnosticFlow.class)
                .step("CSP-001. Local kubeconfig, credential presence and strict TLS", flow -> flow.diagnosticSteps().pkg001()).run();
    }

    @Test @Order(2)
    @DisplayName("CSP-002. ContainerService, owned native client and ContainerServiceFlow resolution")
    void pkg002() {
        FlowRunner.flowRunnerFor(ContainerPackageDiagnosticFlow.class)
                .step("CSP-002. ContainerService, owned native client and ContainerServiceFlow resolution", flow -> flow.diagnosticSteps().pkg002()).run();
    }

    @Test @Order(3)
    @DisplayName("CSP-003. Deployment, Service and pod access through framework")
    void pkg003() {
        FlowRunner.flowRunnerFor(ContainerPackageDiagnosticFlow.class)
                .step("CSP-003. Deployment, Service and pod access through framework", flow -> flow.diagnosticSteps().pkg003()).run();
    }

    @Test @Order(4)
    @DisplayName("CSP-004. Independent read-only Fabric8 comparison")
    void pkg004() {
        FlowRunner.flowRunnerFor(ContainerPackageDiagnosticFlow.class)
                .step("CSP-004. Independent read-only Fabric8 comparison", flow -> flow.diagnosticSteps().pkg004()).run();
    }

    @Test @Order(5)
    @DisplayName("CSP-005. Pod status through client and flow")
    void pkg005() {
        FlowRunner.flowRunnerFor(ContainerPackageDiagnosticFlow.class)
                .step("CSP-005. Pod status through client and flow", flow -> flow.diagnosticSteps().pkg005()).run();
    }

    @Test @Order(6)
    @DisplayName("CSP-006. Exact ConfigMap and native API readback")
    void pkg006() {
        FlowRunner.flowRunnerFor(ContainerPackageDiagnosticFlow.class)
                .step("CSP-006. Exact ConfigMap and native API readback", flow -> flow.diagnosticSteps().pkg006()).run();
    }

    @Test @Order(7)
    @DisplayName("CSP-007. ConfigMap data through framework")
    void pkg007() {
        FlowRunner.flowRunnerFor(ContainerPackageDiagnosticFlow.class)
                .step("CSP-007. ConfigMap data through framework", flow -> flow.diagnosticSteps().pkg007()).run();
    }

    @Test @Order(8)
    @DisplayName("CSP-008. Existing application.yml through client and flow")
    void pkg008() {
        FlowRunner.flowRunnerFor(ContainerPackageDiagnosticFlow.class)
                .step("CSP-008. Existing application.yml through client and flow", flow -> flow.diagnosticSteps().pkg008()).run();
    }

    @Test @Order(9)
    @DisplayName("CSP-009. Default-container log overload")
    void pkg009() {
        FlowRunner.flowRunnerFor(ContainerPackageDiagnosticFlow.class)
                .step("CSP-009. Default-container log overload", flow -> flow.diagnosticSteps().pkg009()).run();
    }

    @Test @Order(10)
    @DisplayName("CSP-010. Named-container log overload")
    void pkg010() {
        FlowRunner.flowRunnerFor(ContainerPackageDiagnosticFlow.class)
                .step("CSP-010. Named-container log overload", flow -> flow.diagnosticSteps().pkg010()).run();
    }

    @Test @Order(11)
    @DisplayName("CSP-011. Filtered log overload")
    void pkg011() {
        FlowRunner.flowRunnerFor(ContainerPackageDiagnosticFlow.class)
                .step("CSP-011. Filtered log overload", flow -> flow.diagnosticSteps().pkg011()).run();
    }

    @Test @Order(12)
    @DisplayName("CSP-012. Absent resources and documented failure contracts")
    void pkg012() {
        FlowRunner.flowRunnerFor(ContainerPackageDiagnosticFlow.class)
                .step("CSP-012. Absent resources and documented failure contracts", flow -> flow.diagnosticSteps().pkg012()).run();
    }

    @Test @Order(13)
    @DisplayName("CSP-013. Owned YAML fixture: reads, all update overloads, flow, restore")
    void pkg013() {
        FlowRunner.flowRunnerFor(ContainerPackageDiagnosticFlow.class)
                .step("CSP-013. Owned YAML fixture: reads, all update overloads, flow, restore", flow -> flow.diagnosticSteps().pkg013()).run();
    }

    @Test @Order(14)
    @DisplayName("CSP-014. restartPods: exact pod deletion and new Ready UID")
    void pkg014() {
        FlowRunner.flowRunnerFor(ContainerPackageDiagnosticFlow.class)
                .step("CSP-014. restartPods: exact pod deletion and new Ready UID", flow -> flow.diagnosticSteps().pkg014()).run();
    }

    @Test @Order(15)
    @DisplayName("CSP-015. Flow restartPods: exact pod deletion and new Ready UID")
    void pkg015() {
        FlowRunner.flowRunnerFor(ContainerPackageDiagnosticFlow.class)
                .step("CSP-015. Flow restartPods: exact pod deletion and new Ready UID", flow -> flow.diagnosticSteps().pkg015()).run();
    }

    @Test @Order(16)
    @DisplayName("CSP-016. restartPodsWithOutUnavailability: recreation, not zero-downtime proof")
    void pkg016() {
        FlowRunner.flowRunnerFor(ContainerPackageDiagnosticFlow.class)
                .step("CSP-016. restartPodsWithOutUnavailability: recreation, not zero-downtime proof", flow -> flow.diagnosticSteps().pkg016()).run();
    }

    @Test @Order(17)
    @DisplayName("CSP-017. Live ConfigMap data: backup, update, recreate, restore, recreate")
    void pkg017() {
        FlowRunner.flowRunnerFor(ContainerPackageDiagnosticFlow.class)
                .step("CSP-017. Live ConfigMap data: backup, update, recreate, restore, recreate", flow -> flow.diagnosticSteps().pkg017()).run();
    }
}
