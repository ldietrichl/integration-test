package steps.container;

import config.services.container.KubernetesTunnelSettings;
import infrastructure.kubernetes.KubernetesTunnelService;
import infrastructure.kubernetes.TunnelSession;
import io.qameta.allure.Allure;
import steps.rest.scheduler.SchedulerSteps;

import java.util.Map;

import static io.qameta.allure.Allure.step;

/** Reusable infrastructure steps, owned by the project's Environment rather than by an isolated test. */
public final class KubernetesTunnelSteps {
    private final KubernetesTunnelService service;

    public KubernetesTunnelSteps(KubernetesTunnelService service) { this.service = service; }
    public KubernetesTunnelSettings settings() { return service.settings(); }
    public KubernetesWorkloadSteps workloadSteps() { return new flow.WorkloadFlow() { }.workloadSteps(); }

    public void beginScenario(String scenarioId) { service.beginScenario(scenarioId); }

    /** Diagnostic evidence cannot turn a business assertion into a logging failure. */
    public void attachScenarioLogs() {
        try {
            var capture = service.captureScenarioLogs();
            evidence("Scheduler service log capture status", capture.metadata());
            if (!capture.text().isBlank())
                Allure.addAttachment("Scheduler service log (sanitized, scenario time window)", capture.text());
        } catch (RuntimeException failure) {
            try { evidence("Scheduler service log capture unavailable",
                    Map.of("status", "COLLECTION_FAILED", "failureType", failure.getClass().getSimpleName())); }
            catch (RuntimeException ignored) { }
        }
    }

    public void attachTlsDiagnostics() {
        try {
            if (settings().tlsDiagnosticsEnabled)
                evidence("Java TLS trust-source diagnostics (no credentials)", service.tlsDiagnostics());
        } catch (RuntimeException failure) {
            try { evidence("Java TLS diagnostics unavailable",
                    Map.of("failureType", failure.getClass().getSimpleName())); }
            catch (RuntimeException ignored) { }
        }
    }

    public KubernetesTunnelService.Target target() {
        return step("Read scheduler Service and select a Ready pod via ContainerService", () -> {
            try {
                var target = service.target();
                evidence("Kubernetes target", target.description());
                return target;
            } finally {
                attachSelectionDiagnostics();
                attachTlsDiagnostics();
            }
        });
    }

    public KubernetesTunnelService.Target awaitDiagnosticReadyTarget() {
        return step("Wait for two consecutive Ready observations of the same scheduler pod", () -> {
            try { return service.awaitDiagnosticReadyTarget(); }
            finally {
                attachSelectionDiagnostics();
                try { evidence("Scheduler readiness timeline", service.readinessDiagnostics()); }
                catch (RuntimeException ignored) { /* Keep the original readiness result. */ }
            }
        });
    }

    private void attachSelectionDiagnostics() {
        try { evidence("Scheduler pod selection and rejection reasons", service.selectionDiagnostics()); }
        catch (RuntimeException ignored) { /* Preserve the primary failure. */ }
    }

    public TunnelSession openConfigured() {
        String transport = settings().transport;
        return step("Open owned scheduler API tunnel using configured transport: " + transport, () -> {
            try {
                TunnelSession session = service.openConfigured();
                evidence("Configured API tunnel", session.description());
                return session;
            } finally {
                if ("oc".equals(transport))
                    Allure.addAttachment("oc startup output (sanitized)", service.cliOutput());
            }
        });
    }

    public TunnelSession openNative() {
        return step("Open an owned Fabric8 loopback tunnel", () -> {
            try {
                TunnelSession session = service.openNative();
                evidence("Native tunnel", session.description());
                return session;
            } finally {
                attachSelectionDiagnostics();
                attachTlsDiagnostics();
            }
        });
    }

    public TunnelSession openNative(int port) {
        return step("Open native tunnel on an explicitly requested local port", () -> service.openNative(port));
    }

    public TunnelSession openCli() {
        return step("Open independent oc control tunnel using the same kubeconfig", () -> {
            try {
                TunnelSession session = service.openCli();
                evidence("CLI tunnel", session.description());
                return session;
            } finally {
                Allure.addAttachment("oc startup output (sanitized)", service.cliOutput());
            }
        });
    }

    public TunnelSession openDictionaryCli() {
        return step("Open owned dictionary dependency tunnel through the same DEV/IFT oc context", () -> {
            var target = settings().dictionarySettings();
            evidence("V2 dictionary target without credentials", Map.of(
                    "environment", target.environment, "namespace", target.namespace,
                    "service", target.service, "servicePort", target.servicePort,
                    "formCode", "AUTOSTART_TASK_LIST", "transport", "oc"));
            try {
                TunnelSession session = service.openDictionaryCli();
                evidence("V2 dictionary owned tunnel", session.description());
                return session;
            } finally {
                Allure.addAttachment("oc dependency startup output (sanitized)", service.cliOutput());
            }
        });
    }

    public TunnelSession openUserCli() {
        return step("Open owned user-service dependency tunnel through the same DEV/IFT oc context", () -> {
            var target = settings().userSettings();
            evidence("User-service target without credentials", Map.of(
                    "environment", target.environment, "namespace", target.namespace,
                    "service", target.service, "servicePort", target.servicePort,
                    "transport", "oc"));
            try {
                TunnelSession session = service.openUserCli();
                evidence("User-service owned tunnel", session.description());
                return session;
            } finally {
                Allure.addAttachment("oc dependency startup output (sanitized)", service.cliOutput());
            }
        });
    }

    public boolean reusesFrameworkClients() { return service.reusesFrameworkClients(); }
    public void closeAll() { step("Close only infrastructure resources owned by this test", service::close); }

    public static void evidence(String title, Object value) {
        Allure.addAttachment(title, SchedulerSteps.JSON.valueToTree(value).toPrettyString());
    }
}
