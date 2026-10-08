package steps.flow.scheduler;

import flow.SchedulerInfrastructureFlow;
import infrastructure.kubernetes.KubernetesWorkloadControl;
import steps.container.KubernetesTunnelSteps;
import steps.container.KubernetesWorkloadSteps;
import static org.junit.jupiter.api.Assertions.*;
import static steps.rest.scheduler.SchedulerSteps.expect;

/** Diagnostics exercise shared workload tools, not implicit ConfigMap hot reload or business coverage. */
public final class SchedulerWorkloadDiagnosticSteps {
    private KubernetesTunnelSteps infrastructure() { return new SchedulerInfrastructureFlow().infrastructureSteps(); }
    private KubernetesWorkloadSteps workload() { return infrastructure().workloadSteps(); }

    public void ops001() {
        var steps = workload();
        var control = steps.session("scheduler");
        withRestoration(control, "pod-only", () -> {
            assertFalse(steps.replacePod(control).isEmpty(), "Deletion produced a different Ready pod UID");
            assertHealthy("pod-replacement");
        });
    }

    public void ops002() {
        var steps = workload();
        try (var control = steps.session("scheduler")) {
            assertDoesNotThrow(() -> steps.inspectConfigMap(control),
                    "ConfigMap pre-image, approved key and Deployment reference must be readable");
        }
    }

    public void ops003() {
        var steps = workload();
        var control = steps.session("scheduler");
        withRestoration(control, "configmap", () -> {
            steps.changeConfigMap(control);
            assertFalse(steps.replacePod(control).isEmpty(), "New Ready pod created after ConfigMap GET assertion");
            assertHealthy("configmap-probe");
        });
    }

    public void ops004() {
        if (!new config.services.core.StandSettings().flag("workloads.scheduler.scale.enabled"))
            throw new IllegalStateException("DIAGNOSTIC_CONFIGURATION_REQUIRED: "
                    + "stand.<env>.workloads.scheduler.scale.enabled=true");
        var steps = workload();
        var control = steps.session("scheduler");
        withRestoration(control, "replicas", () -> {
            steps.scale(control);
            assertHealthy("replicas-probe");
        });
    }

    /** Even a failed main assertion must not prevent cleanup or the post-cleanup HTTP check. */
    public void withRestoration(KubernetesWorkloadControl control, String phase, Runnable scenario) {
        Throwable primary = null;
        try (control) {
            try { scenario.run(); }
            catch (RuntimeException | Error failure) {
                control.captureEvidence(phase + "-scenario-failed-before-rollback");
                throw failure;
            }
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            if (control.isRestored()) {
                try { assertHealthy(phase + "-restored"); }
                catch (RuntimeException | Error failure) {
                    if (primary != null) primary.addSuppressed(failure);
                    else throw failure;
                }
            }
        }
        assertTrue(control.isRestored(), "Owned rollback/no-change state and current pod readiness confirmed");
    }

    public void assertHealthy(String phase) {
        KubernetesTunnelSteps infrastructure = infrastructure();
        infrastructure.closeAll(); // Only project-owned handles; never adopt an existing listener.
        infrastructure.beginScenario("scheduler-workload-" + phase);
        try (var tunnel = infrastructure.openConfigured()) {
            assertTrue(tunnel.isAlive(), "Fresh tunnel must target a current Ready pod");
            var health = expect(new SchedulerInfrastructureFlow().schedulerSteps(tunnel)
                    .call("GET", "/actuator/health", null), 200);
            assertEquals("UP", health.path("status").asText());
            assertEquals("UP", health.at("/components/db/status").asText());
        } finally {
            try { infrastructure.attachScenarioLogs(); } finally { infrastructure.closeAll(); }
        }
        assertIngressReady(phase);
    }

    private void assertIngressReady(String phase) {
        var settings = new config.services.core.SchedulerSettings();
        if (!config.services.rest.RestMtlsConfiguration.enabled(settings.environment)) return;
        new SchedulerInfrastructureFlow().restCustomSteps().schedulerSteps().awaitIngressReady(phase);
    }

    public void assertHealthyAndPrometheus(String phase) {
        KubernetesTunnelSteps infrastructure = infrastructure();
        infrastructure.closeAll(); // Actuator is internal and is intentionally not exposed by ingress-v2.
        infrastructure.beginScenario("scheduler-actuator-" + phase);
        try (var tunnel = infrastructure.openConfigured()) {
            assertTrue(tunnel.isAlive(), "Fresh tunnel must target a current Ready pod");
            var scheduler = new SchedulerInfrastructureFlow().schedulerSteps(tunnel);
            var health = expect(scheduler.call("GET", "/actuator/health", null), 200);
            assertEquals("UP", health.path("status").asText());
            assertEquals("UP", health.at("/components/db/status").asText());

            var metrics = scheduler.getText("/actuator/prometheus");
            assertEquals(200, metrics.toResponse().statusCode());
            String body = metrics.toResponse().asString();
            assertTrue(body.contains("# HELP"), "Expected Prometheus text exposition");
            assertTrue(body.contains("jvm_") || body.contains("process_"), "Expected process metrics");
        } finally {
            try { infrastructure.attachScenarioLogs(); } finally { infrastructure.closeAll(); }
        }
        assertIngressReady(phase);
    }
}
