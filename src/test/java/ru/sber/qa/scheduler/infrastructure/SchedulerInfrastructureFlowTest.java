package ru.sber.qa.scheduler.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import config.environment.special.EnvironmentConfigWithSchedulerInfrastructure;
import config.services.core.TestEnvironment;
import flow.SchedulerInfrastructureFlow;
import infrastructure.kubernetes.KubernetesTunnelService;
import infrastructure.kubernetes.TunnelSession;
import io.perfeccionista.framework.Environment;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
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
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.db.DatabaseService;
import ru.sber.qa.services.rest.RestService;
import steps.container.KubernetesTunnelSteps;
import steps.rest.scheduler.SchedulerSteps;
import steps.rest.scheduler.SchedulerRegistrySteps;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static steps.rest.scheduler.SchedulerSteps.expect;

@ExtendWith({config.extensions.scheduler.SchedulerInfrastructureCondition.class, PerfeccionistaExtension.class, config.extensions.scheduler.SchedulerInfrastructureExtension.class})
@SetEnvironmentConfiguration(EnvironmentConfigWithSchedulerInfrastructure.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("scheduler-service-regression")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 180, threadMode = Timeout.ThreadMode.SAME_THREAD)
@Epic("Scheduler service")
@Feature("Scheduler mTLS business REST and Fabric8 Kubernetes diagnostics")
@DisplayName("Scheduler infrastructure hypotheses")
@config.extensions.scheduler.UsesScenarioSteps(steps.flow.scheduler.SchedulerInfrastructureDiagnosticSteps.class)
public class SchedulerInfrastructureFlowTest {
    @Test
    @Order(1)
    @DisplayName("SCH-INF-001. Shared environment, explicit context and verified cluster TLS")
    void inf001() {
        ru.sber.qa.flow.FlowRunner.flowRunnerFor(flow.SchedulerInfrastructureFlow.class)
                .step("SCH-INF-001. Shared environment, explicit context and verified cluster TLS",
                        flow -> new steps.flow.scheduler.SchedulerInfrastructureDiagnosticSteps().inf001()).run();
    }

    @Test
    @Order(2)
    @DisplayName("SCH-INF-002. Framework Kubernetes API access, service targetPort and Ready pod")
    void inf002() {
        ru.sber.qa.flow.FlowRunner.flowRunnerFor(flow.SchedulerInfrastructureFlow.class)
                .step("SCH-INF-002. Framework Kubernetes API access, service targetPort and Ready pod",
                        flow -> new steps.flow.scheduler.SchedulerInfrastructureDiagnosticSteps().inf002()).run();
    }

    @Test
    @Order(3)
    @DisplayName("SCH-INF-003. Fabric8 port-forward reaches scheduler health through project REST steps")
    void inf003() {
        ru.sber.qa.flow.FlowRunner.flowRunnerFor(flow.SchedulerInfrastructureFlow.class)
                .step("SCH-INF-003. Native tunnel reaches scheduler health through project REST steps",
                        flow -> new steps.flow.scheduler.SchedulerInfrastructureDiagnosticSteps().inf003()).run();
    }

    @Test
    @Order(4)
    @DisplayName("SCH-INF-004. mTLS V1 history dictionary exposes its filter and operator contract")
    void inf004() {
        ru.sber.qa.flow.FlowRunner.flowRunnerFor(flow.SchedulerInfrastructureFlow.class)
                .step("SCH-INF-004. V1 history dictionary exposes its own filter and operator contract",
                        flow -> new steps.flow.scheduler.SchedulerInfrastructureDiagnosticSteps().inf004()).run();
    }

    @Test
    @Order(5)
    @DisplayName("SCH-INF-005. mTLS V2 registry accepts page/size and returns content/totalPages")
    void inf005() {
        ru.sber.qa.flow.FlowRunner.flowRunnerFor(flow.SchedulerInfrastructureFlow.class)
                .step("SCH-INF-005. Minimal V2 registry query accepts page/size and returns content/totalPages",
                        flow -> new steps.flow.scheduler.SchedulerInfrastructureDiagnosticSteps().inf005()).run();
    }

    @Test
    @Order(6)
    @DisplayName("SCH-INF-006. mTLS V2 registry equal filter uses AUTOSTART_TASK_LIST metadata")
    void inf006() {
        ru.sber.qa.flow.FlowRunner.flowRunnerFor(flow.SchedulerInfrastructureFlow.class)
                .step("SCH-INF-006. V2 registry equal filter uses AUTOSTART_TASK_LIST metadata",
                        flow -> new steps.flow.scheduler.SchedulerInfrastructureDiagnosticSteps().inf006()).run();
    }

    @Test
    @Order(7)
    @DisplayName("SCH-INF-007. mTLS registry date representation matches the documented contract")
    void inf007() {
        ru.sber.qa.flow.FlowRunner.flowRunnerFor(flow.SchedulerInfrastructureFlow.class)
                .step("SCH-INF-007. Registry date representation matches exported OpenAPI string/date-time",
                        flow -> new steps.flow.scheduler.SchedulerInfrastructureDiagnosticSteps().inf007()).run();
    }

    @Test
    @Order(8)
    @DisplayName("SCH-INF-008. Prometheus is accessible through Fabric8 port-forward and project REST steps")
    void inf008() {
        ru.sber.qa.flow.FlowRunner.flowRunnerFor(flow.SchedulerInfrastructureFlow.class)
                .step("SCH-INF-008. Prometheus endpoint is accessible through the project text REST step",
                        flow -> new steps.flow.scheduler.SchedulerInfrastructureDiagnosticSteps().inf008()).run();
    }

    @Test
    @Order(9)
    @DisplayName("SCH-INF-009. Framework explab DB client reaches scheduler schema independently of HTTP")
    void inf009() {
        ru.sber.qa.flow.FlowRunner.flowRunnerFor(flow.SchedulerInfrastructureFlow.class)
                .step("SCH-INF-009. Framework explab DB client reaches scheduler schema independently of HTTP",
                        flow -> new steps.flow.scheduler.SchedulerInfrastructureDiagnosticSteps().inf009()).run();
    }

    @Test
    @Order(10)
    @DisplayName("SCH-INF-010. mTLS business REST and Fabric8 actuator transport are separated")
    void inf010() {
        ru.sber.qa.flow.FlowRunner.flowRunnerFor(flow.SchedulerInfrastructureFlow.class)
                .step("SCH-INF-010. Independent oc control tunnel reaches the same scheduler health endpoint",
                        flow -> new steps.flow.scheduler.SchedulerInfrastructureDiagnosticSteps().inf010()).run();
    }

    @Test
    @Order(11)
    @DisplayName("SCH-INF-011. Occupied local port is rejected without stopping its listener")
    void inf011() {
        ru.sber.qa.flow.FlowRunner.flowRunnerFor(flow.SchedulerInfrastructureFlow.class)
                .step("SCH-INF-011. Occupied local port is rejected without stopping its listener",
                        flow -> new steps.flow.scheduler.SchedulerInfrastructureDiagnosticSteps().inf011()).run();
    }

    @Test
    @Order(12)
    @DisplayName("SCH-INF-012. Native listener is reused, closes, and can be reopened explicitly")
    void inf012() {
        ru.sber.qa.flow.FlowRunner.flowRunnerFor(flow.SchedulerInfrastructureFlow.class)
                .step("SCH-INF-012. Native listener is reused, closes, and can be reopened explicitly",
                        flow -> new steps.flow.scheduler.SchedulerInfrastructureDiagnosticSteps().inf012()).run();
    }

    @Test
    @Order(13)
    @DisplayName("SCH-INF-013. Capture mTLS V2 ASC/DESC evidence without masking regression defects")
    void inf013() {
        ru.sber.qa.flow.FlowRunner.flowRunnerFor(flow.SchedulerInfrastructureFlow.class)
                .step("SCH-INF-013. Mapped V2 numeric/date field respects advertised ASC and DESC sorting",
                        flow -> new steps.flow.scheduler.SchedulerInfrastructureDiagnosticSteps().inf013()).run();
    }

    @Test
    @Order(14)
    @DisplayName("SCH-INF-014. Capture mTLS V2 taskNumber DB/API evidence without masking regression defects")
    void inf014() {
        ru.sber.qa.flow.FlowRunner.flowRunnerFor(flow.SchedulerInfrastructureFlow.class)
                .step("SCH-INF-014. V2 taskNumber preserves the persisted value instead of a constant",
                        flow -> new steps.flow.scheduler.SchedulerInfrastructureDiagnosticSteps().inf014()).run();
    }

}
