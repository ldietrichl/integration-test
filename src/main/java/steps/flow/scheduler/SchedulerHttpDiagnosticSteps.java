package steps.flow.scheduler;

import config.services.core.SchedulerSettings;
import config.services.core.StandSettings;
import config.services.rest.RestMtlsConfiguration;
import flow.SchedulerInfrastructureFlow;
import infrastructure.kubernetes.SchedulerRegressionSession;
import infrastructure.kubernetes.ContainerDiagnosticEvidence;
import infrastructure.kubernetes.KubernetesDiagnosticException;
import infrastructure.kubernetes.TunnelSession;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.function.Executable;
import steps.rest.scheduler.SchedulerSteps;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Independent, read-only ingress/port-forward observations; never treats a 401 as a successful 400. */
public final class SchedulerHttpDiagnosticSteps {
    private final SchedulerInfrastructureFlow flow;

    public SchedulerHttpDiagnosticSteps(SchedulerInfrastructureFlow flow) {
        this.flow = flow;
    }

    public void http001() { runCase("SCH-HTTP-001", validBody(), 200); }
    public void http002() { runCase("SCH-HTTP-002", Map.of("size", 1), 400); }
    public void http003() { runCase("SCH-HTTP-003", Map.of("page", "abc", "size", 1), 400); }

    private static Map<String, Object> validBody() { return Map.of("page", 0, "size", 1); }

    private void runCase(String scenario, Object body, int expected) {
        List<Map<String, Object>> observations = new ArrayList<>();
        List<Executable> checks = new ArrayList<>();
        Map<String, Object> routes = new LinkedHashMap<>();
        List<Throwable> infrastructureFailures = new ArrayList<>();
        // Readiness is a control, not a condition that suppresses the independent ingress attempt.
        try {
            var infrastructure = flow.infrastructureSteps();
            infrastructure.settings().requireContainerServiceManagement();
            var ready = infrastructure.awaitDiagnosticReadyTarget();
            observations.add(Map.of("route", "readiness", "phase", "before-http",
                    "status", "READY", "podUid", ready.podUid(),
                    "backendPodIdentityProvenForIngress", false));
        } catch (RuntimeException | AssertionError failure) {
            recordFailure("readiness", "before-http", failure, observations, checks, infrastructureFailures);
        }
        try {
            SchedulerSettings settings = new SchedulerSettings();
            if (settings.tunnelEnabled() || SchedulerRegressionSession.schedulerEndpoint() != null)
                throw new IllegalStateException("INGRESS_ROUTE_IS_OVERRIDDEN");
            if (!RestMtlsConfiguration.enabled(settings.environment))
                throw new IllegalStateException("STRICT_MTLS_REQUIRED");
            if (!"false".equalsIgnoreCase(new StandSettings().optional("mtls.server-tls.relaxed", "false")))
                throw new IllegalStateException("RELAXED_TLS_NOT_ALLOWED");
            RestMtlsConfiguration.requireApprovedTarget(settings.baseUri());
            routes.put("ingress", Map.of("origin", settings.baseUri(), "strictMtlsRequired", true,
                    "backendPodIdentityProven", false));
            probeRoute(flow.restCustomSteps().schedulerSteps(), "ingress", body, expected, observations, checks, infrastructureFailures);
        } catch (RuntimeException | AssertionError failure) {
            recordFailure("ingress", "setup-or-cleanup", failure, observations, checks, infrastructureFailures);
        }

        // The second route is attempted even if the first one failed. It is not a fallback for the first.
        try {
            var infrastructure = flow.infrastructureSteps();
            var settings = infrastructure.settings();
            settings.requireContainerServiceManagement();
            try (TunnelSession session = infrastructure.openNative()) {
                Map<String, Object> target = new LinkedHashMap<>(session.targetDescription());
                target.put("transport", "FABRIC8_LOOPBACK");
                target.put("logContainer", settings.logsContainer);
                target.put("applicationPrincipalEqualityProven", false);
                target.put("portOwningContainerProven", false);
                routes.put("direct", target);
                if (!session.isAlive() || !target.containsKey("podUid"))
                    throw new IllegalStateException("OWNED_TUNNEL_TARGET_NOT_IDENTIFIED");
                probeRoute(flow.schedulerSteps(session), "direct", body, expected, observations, checks, infrastructureFailures);
            }
        } catch (RuntimeException | AssertionError failure) {
            recordFailure("direct", "setup-or-cleanup", failure, observations, checks, infrastructureFailures);
        }

        Map<String, Object> matrix = new LinkedHashMap<>();
        matrix.put("scenario", scenario);
        matrix.put("routes", routes);
        matrix.put("observations", observations);
        matrix.put("mutationsDispatched", false);
        matrix.put("infrastructureFailureCount", infrastructureFailures.size());
        matrix.put("contractCheckCount", checks.size());
        matrix.put("applicationPrincipalEqualityProven", false);
        matrix.put("classification", "COMPONENT_ATTRIBUTION_REQUIRES_CORRELATED_EVIDENCE");
        matrix.put("limits", List.of(
                "Outgoing JSON metadata is observed at a request filter, not captured from the wire.",
                "A local request contract failure is not an HTTP response and does not suppress the other route.",
                "Header presence does not establish equal application principals.",
                "Ingress mTLS and Kubernetes port-forward authentication are different layers.",
                "Port-forward may still traverse pod-local proxy/security filters.",
                "Server/Via headers and nearby exceptions alone do not identify the producer of a 401.",
                "No automatic login, credential replacement, invalid-request retry or status relaxation."));
        Allure.addAttachment(scenario + " ingress/direct HTTP matrix", "application/json",
                SchedulerSteps.JSON.valueToTree(matrix).toPrettyString(), ".json");
        try {
            assertAll(scenario + ": both routes and all independent controls", checks);
        } catch (AssertionError contractFailure) {
            infrastructureFailures.forEach(contractFailure::addSuppressed);
            throw contractFailure; // Keep unexpected HTTP statuses FAILED alongside transport errors.
        }
        if (!infrastructureFailures.isEmpty()) {
            IllegalStateException failure = new IllegalStateException(
                    scenario + ": HTTP_DIAGNOSTICS_INFRASTRUCTURE_FAILURE; inspect safe readiness/HTTP evidence");
            infrastructureFailures.forEach(failure::addSuppressed);
            throw failure; // Infrastructure-only failure is BROKEN, never SKIPPED or PASSED.
        }
    }

    private static void probeRoute(SchedulerSteps scheduler, String route, Object body, int expected,
                                   List<Map<String, Object>> observations, List<Executable> checks,
                                      List<Throwable> infrastructureFailures) {
        if (expected != 200) probe(scheduler, route, "valid-before", validBody(), 200, observations, checks, infrastructureFailures);
        probe(scheduler, route, "subject", body, expected, observations, checks, infrastructureFailures);
        if (expected != 200) probe(scheduler, route, "valid-after", validBody(), 200, observations, checks, infrastructureFailures);
    }

    private static void probe(SchedulerSteps scheduler, String route, String phase, Object body, int expected,
                              List<Map<String, Object>> observations, List<Executable> checks,
                                      List<Throwable> infrastructureFailures) {
        String label = route + "/" + phase;
        try {
            Map<String, Object> evidence = new LinkedHashMap<>(scheduler.diagnosticRegistry(body, expected, label));
            evidence.put("route", route);
            evidence.put("phase", phase);
            observations.add(evidence);
            checks.add(() -> assertEquals(expected, evidence.get("actualStatus"), label + ": HTTP contract"));
            if (expected == 200 && Integer.valueOf(200).equals(evidence.get("actualStatus")))
                checks.add(() -> assertEquals(Boolean.TRUE, evidence.get("registryContentArray"),
                        label + ": successful registry control must contain a content array"));
        } catch (RuntimeException | AssertionError failure) {
            recordFailure(route, phase, failure, observations, checks, infrastructureFailures);
        }
    }

    private static void recordFailure(String route, String phase, Throwable failure,
                                      List<Map<String, Object>> observations, List<Executable> checks,
                                      List<Throwable> infrastructureFailures) {
        String type = failure.getClass().getName();
        // Exception messages/causes may contain credentials from transport configuration.
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("route", route);
        observation.put("phase", phase);
        observation.put("failureType", type);
        observation.put("status", "NO_COMPLETED_OBSERVATION");
        observation.put("exceptionMessageSuppressed", true);
        observation.put("classification", ContainerDiagnosticEvidence.classify(failure));
        KubernetesDiagnosticException safe = KubernetesDiagnosticException.find(failure);
        if (safe != null) observation.putAll(safe.safeDetails());
        observations.add(observation);
        if (failure instanceof AssertionError assertion) {
            checks.add(() -> { throw assertion; });
        } else {
            infrastructureFailures.add(safe != null ? safe : new KubernetesDiagnosticException(
                    failure instanceof steps.rest.scheduler.SchedulerRequestDiagnostics.RequestContractException
                            ? "SCHEDULER_REQUEST_CONTRACT" : "HTTP_DIAGNOSTIC_TRANSPORT_FAILURE",
                    route + "_" + phase, failure));
        }
    }
}
