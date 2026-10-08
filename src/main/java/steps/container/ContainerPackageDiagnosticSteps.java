package steps.container;

import config.services.container.ContainerDiagnosticsSettings;
import config.services.container.ContainerServiceKubernetesAccess;
import config.services.container.KubernetesTunnelSettings;
import flow.ContainerPackageDiagnosticFlow;
import infrastructure.kubernetes.ContainerDiagnosticEvidence;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.perfeccionista.framework.Environment;
import ru.sber.qa.containers.client.ContainerServiceClient;
import ru.sber.qa.containers.services.ContainerService;

import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.function.Supplier;

import static infrastructure.kubernetes.ContainerDiagnosticEvidence.detail;
import static infrastructure.kubernetes.ContainerDiagnosticEvidence.execute;
import static infrastructure.kubernetes.ContainerDiagnosticEvidence.stage;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Reusable steps: real ContainerService/ContainerServiceFlow, no independent HTTP implementation. */
public final class ContainerPackageDiagnosticSteps {
    private static final AtomicReference<String> DESTRUCTIVE_BLOCKER = new AtomicReference<>();
    private final ContainerPackageDiagnosticFlow flow;

    @FunctionalInterface
    private interface MutationAction { boolean run() throws Exception; }

    public ContainerPackageDiagnosticSteps(ContainerPackageDiagnosticFlow flow) { this.flow = flow; }

    ContainerDiagnosticsSettings settings() {
        return ContainerDiagnosticsSettings.from(Environment.getForCurrentThread());
    }

    ContainerServiceClient service() {
        return Environment.getForCurrentThread().getService(ContainerService.class)
                .containerServiceClient(settings().environment);
    }

    KubernetesClient api() {
        List<KubernetesClient> clients = service().getClients();
        assertEquals(1, clients.size(), "Diagnostic target must be exactly one approved cluster");
        KubernetesClient client = clients.get(0);
        assertEquals(settings().connection().namespace, client.getNamespace(), "Namespace mismatch");
        return client;
    }

    static String exact(String name) { return "^" + Pattern.quote(name) + "$"; }

    Pod selectedPod() {
        KubernetesClient api = api();
        var svc = diagnosticRead(api, "SERVICE_GET", "service", settings().resource("service"),
                () -> api.services().withName(settings().resource("service")).get());
        assertNotNull(svc, "Configured Service is missing");
        Map<String, String> selector = svc.getSpec().getSelector();
        assertNotNull(selector, "Service selector missing");
        assertFalse(selector.isEmpty(), "Empty selectors are prohibited");
        List<Pod> pods = diagnosticRead(api, "POD_LIST", "pods", "service-selector",
                () -> api.pods().withLabels(selector).list()).getItems().stream()
                .filter(p -> p.getMetadata().getDeletionTimestamp() == null).toList();
        assertFalse(pods.isEmpty(), "No active pod matched the Service selector");
        Pod selected = pods.stream().filter(ContainerPackageDiagnosticSteps::ready).findFirst().orElse(pods.get(0));
        detail("selectedPod", selected.getMetadata().getName());
        detail("selectedPodUid", selected.getMetadata().getUid());
        return selected;
    }

    static boolean ready(Pod pod) {
        return pod.getStatus() != null && pod.getStatus().getConditions() != null
                && pod.getStatus().getConditions().stream()
                .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
    }

    ConfigMap configMap() {
        KubernetesClient api = api();
        ConfigMap cm = diagnosticRead(api, "CONFIGMAP_GET", "configmap", settings().resource("configmap"),
                () -> api.configMaps().withName(settings().resource("configmap")).get());
        assertNotNull(cm, "Configured ConfigMap is missing");
        assertNotNull(cm.getData(), "ConfigMap data is missing");
        return cm;
    }

    public void pkg001() {
        execute("CSP-001", () -> {
            DESTRUCTIVE_BLOCKER.set(null);
            detail("destructiveCascadeGuard", "RESET_FOR_DIAGNOSTIC_RUN");
            stage("LOCAL_KUBECONFIG_AND_TLS");
            detail("apiAuthenticationChecked", false);
            detail("credentialValidity", "NOT_CHECKED");
            KubernetesTunnelSettings c = settings().connection();
            Config parsed = c.nativeClientConfiguration();
            detail("nativeTls", infrastructure.kubernetes.KubernetesTlsDiagnostics.snapshot(parsed,
                    "selected stand CA overlay and kubeconfig; no network request"));
            config.services.container.KubernetesCaTrust.requireNativeTrust(parsed);
            detail("target", c.description());
            detail("javaVersion", System.getProperty("java.version"));
            detail("javaHome", System.getProperty("java.home"));
            detail("javaTruststoreConfigured", System.getProperty("javax.net.ssl.trustStore") != null);
            detail("httpProxyEnvironmentPresent", System.getenv("HTTP_PROXY") != null);
            detail("httpsProxyEnvironmentPresent", System.getenv("HTTPS_PROXY") != null);
            detail("resolvedDiagnosticSettings", Map.of(
                    "logsWindowSeconds", logWindow().toSeconds(),
                    "dedicatedStand", settings().flag("dedicated-stand", false),
                    "mutationsEnabled", settings().flag("mutations.enabled", false),
                    "configurationSource", "container-diagnostics.properties + test.properties via ConfigurationService",
                    "credentials", "not attached"));
            detail("kubeconfigSize", Files.size(c.kubeconfig()));
            detail("kubeconfigModified", Files.getLastModifiedTime(c.kubeconfig()).toString());
            detail("kubeconfigSha256", HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(c.kubeconfig()))));
            detail("tokenPresent", parsed.getOauthToken() != null && !parsed.getOauthToken().isBlank());
            detail("caDataPresent", parsed.getCaCertData() != null && !parsed.getCaCertData().isBlank());
            detail("caFilePresent", parsed.getCaCertFile() != null && !parsed.getCaCertFile().isBlank());
            detail("credentialNote", "Presence is not validity; no browser login or oc login was performed");
            assertFalse(parsed.isTrustCerts(), "Cluster certificate verification must be enabled");
            assertFalse(parsed.isDisableHostnameVerification(), "Hostname verification must be enabled");
            assertEquals(c.namespace, parsed.getNamespace(), "Explicit namespace must be retained");
        });
    }

    public void pkg002() {
        execute("CSP-002", () -> {
            stage("CONTAINER_SERVICE_AND_FLOW_RESOLUTION");
            detail("apiAuthenticationChecked", false);
            detail("credentialValidity", "NOT_CHECKED");
            ContainerServiceClient client = service();
            assertNotNull(client, "ContainerService client missing");
            assertNotNull(flow.applicationPlatform(settings().environment), "ContainerServiceFlow steps missing");
            assertSame(client, service(), "Diagnostic configuration must retain one owned client per test");
            assertEquals(1, client.getClients().size(), "One-cluster profile required");
            assertSame(client.getClients().get(0), api(), "Native client must come from ContainerService");
            detail("frameworkClientClass", client.getClass().getName());
            detail("nativeClientClass", api().getClass().getName());
        });
    }

    public void pkg003() {
        execute("CSP-003", () -> {
            stage("FRAMEWORK_API_AUTHORIZATION");
            KubernetesClient api = api();
            var deployment = diagnosticRead(api, "DEPLOYMENT_GET", "deployment",
                    settings().resource("deployment"),
                    () -> api.apps().deployments().withName(settings().resource("deployment")).get());
            assertNotNull(deployment, "Deployment missing");
            assertEquals(settings().resource("deployment"), deployment.getMetadata().getName());
            detail("apiAuthenticationAccepted", true);
            detail("authenticatedUserIdentityProven", false);
            detail("deploymentUid", deployment.getMetadata().getUid());
            detail("desiredReplicas", deployment.getSpec().getReplicas());
            Pod pod = selectedPod();
            detail("podReady", ready(pod));
            assertNotNull(pod.getMetadata().getUid(), "Pod identity is required");
        });
    }

    public void pkg004() {
        execute("CSP-004", () -> {
            if (!settings().flag("fabric8-control.enabled", true))
                throw new IllegalStateException("FABRIC8_CONTROL_DISABLED: every diagnostic must execute");
            stage("INDEPENDENT_FABRIC8_READ_ONLY_CONTROL_SAME_KUBECONFIG");
            KubernetesTunnelSettings connection = settings().connection();
            String deploymentName = settings().resource("deployment");
            String[] uids = new String[2];
            // assertAll executes the independent client even when the framework client rejects authentication.
            assertAll("Both Kubernetes clients must complete their independent read",
                    () -> uids[0] = observeDeploymentUid(connection, "FRAMEWORK_DEPLOYMENT_GET", () -> {
                        var deployment = api().apps().deployments().withName(deploymentName).get();
                        assertNotNull(deployment, "Framework deployment missing");
                        return deployment.getMetadata().getUid();
                    }),
                    () -> uids[1] = observeDeploymentUid(connection, "INDEPENDENT_DEPLOYMENT_GET", () -> {
                        try (KubernetesClient independent = new KubernetesClientBuilder()
                                .withConfig(connection.nativeClientConfiguration()).build()) {
                            detail("independentClientClass", independent.getClass().getName());
                            var deployment = independent.apps().deployments()
                                    .inNamespace(connection.namespace).withName(deploymentName).get();
                            assertNotNull(deployment, "Independent Fabric8 deployment missing");
                            return deployment.getMetadata().getUid();
                        }
                    }));
            assertEquals(uids[0], uids[1], "Framework and independent Fabric8 clients selected different resources");
            detail("resourceUid", uids[1]);
            detail("transport", "fabric8");
        });
    }

    private String observeDeploymentUid(KubernetesTunnelSettings connection, String operation,
                                        Supplier<String> request) {
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("operation", operation);
        observation.put("clientAttempted", true);
        observation.put("apiAuthenticationAccepted", false);
        observation.put("authenticatedUserIdentityProven", false);
        try {
            String uid = request.get();
            assertNotNull(uid, "Deployment UID is required");
            observation.put("status", "SUCCESS");
            observation.put("apiAuthenticationAccepted", true);
            observation.put("resourceUid", uid);
            return uid;
        } catch (RuntimeException failure) {
            var safe = ContainerServiceKubernetesAccess.safeFailure(connection, operation, failure);
            observation.put("status", "INFRASTRUCTURE_FAILURE");
            observation.putAll(safe.safeDetails());
            throw safe;
        } catch (AssertionError failure) {
            observation.put("status", "CONTRACT_ASSERTION");
            throw failure;
        } finally {
            detail(operation, observation);
        }
    }

    public void pkg005() {
        execute("CSP-005", () -> {
            stage("POD_STATUS_SERVICE_AND_FLOW");
            Pod pod = selectedPod();
            String name = pod.getMetadata().getName();
            Map<String, String> direct = service().checkPodsStatus(exact(name));
            Map<String, String> viaFlow = flow.applicationPlatform(settings().environment).checkPodsStatus(exact(name));
            assertEquals(Set.of(name), direct.keySet(), "Status must select only the intended pod");
            assertEquals(Set.of(name), viaFlow.keySet(), "Flow must select only the intended pod");
            assertEquals(Boolean.toString(ready(pod)), direct.get(name), "Status disagrees with Ready condition");
            assertEquals(direct, viaFlow, "Service/flow status changed or disagrees");
            detail("podStatuses", direct);
        });
    }

    public void pkg006() {
        execute("CSP-006", () -> {
            stage("EXACT_CONFIGMAP_READ");
            ConfigMap expected = configMap();
            List<ConfigMap> actual = service().getConfigMap(expected.getMetadata().getName());
            assertEquals(1, actual.size(), "Exactly one ConfigMap expected for one cluster");
            assertEquals(expected.getMetadata().getUid(), actual.get(0).getMetadata().getUid());
            assertTrue(expected.getData().equals(actual.get(0).getData()), "ConfigMap data differs from direct API read");
            detail("configMapUid", expected.getMetadata().getUid());
            detail("configMapDataKeyCount", expected.getData().size());
        });
    }

    public void pkg007() {
        execute("CSP-007", () -> {
            stage("CONFIGMAP_DATA_READ");
            ConfigMap cm = configMap();
            List<Map<String, String>> data = service().getDataFromConfigMap(exact(cm.getMetadata().getName()));
            assertEquals(1, data.size());
            assertTrue(cm.getData().equals(data.get(0)), "Framework data does not match native API");
            detail("dataKeyCount", data.get(0).size());
        });
    }

    public void pkg008() {
        execute("CSP-008", () -> {
            stage("REAL_CONFIGMAP_YAML_CONTRACT");
            ConfigMap cm = configMap();
            List<String> yaml = cm.getData().keySet().stream()
                    .filter(k -> k.endsWith("yaml") || k.endsWith("yml")).toList();
            detail("yamlKeyCount", yaml.size());
            if (yaml.size() != 1) {
                detail("yamlContract", "NOT_APPLICABLE_AMBIGUOUS_REAL_CONFIGMAP");
                detail("yamlContractReason", "Expected exactly one YAML document, observed " + yaml.size()
                        + "; the live ConfigMap is left unchanged and the diagnostic completes without a skip");
                return;
            }
            List<String> direct = service().getApplicationYml(exact(cm.getMetadata().getName()));
            List<String> viaFlow = flow.applicationPlatform(settings().environment)
                    .getApplicationYml(exact(cm.getMetadata().getName()));
            assertEquals(1, direct.size());
            assertEquals(1, viaFlow.size());
            assertTrue(cm.getData().get(yaml.get(0)).equals(direct.get(0)), "Wrong YAML returned");
            assertTrue(direct.equals(viaFlow), "Service and flow YAML differ");
        });
    }

    public void pkg009() {
        execute("CSP-009", () -> {
            stage("DEFAULT_CONTAINER_LOGS");
            Pod selected = selectedPod();
            String pod = selected.getMetadata().getName();
            List<String> containers = selected.getSpec().getContainers().stream()
                    .map(c -> c.getName()).sorted().toList();
            String annotation = selected.getMetadata().getAnnotations() == null ? null
                    : selected.getMetadata().getAnnotations().get("kubectl.kubernetes.io/default-container");
            detail("regularContainers", containers);
            detail("defaultContainerAnnotationPresent", annotation != null && !annotation.isBlank());
            detail("defaultContainerResolvable", annotation != null && containers.contains(annotation));
            try {
                Map<String, List<String>> logs = service().getLogsFromPod(exact(pod), logWindow());
                assertEquals(Set.of(pod), logs.keySet(), "Default-container log target mismatch");
                assertNotNull(logs.get(pod));
                detail("defaultOverloadStatus", "SUPPORTED");
                detail("logLineCount", logs.get(pod).size());
            } catch (RuntimeException failure) {
                KubernetesClientException kubernetes = kubernetesFailure(failure);
                if (containers.size() < 2 || kubernetes == null || kubernetes.getCode() != 400) throw failure;
                detail("defaultOverloadStatus", "MULTI_CONTAINER_HTTP_400");
                detail("defaultOverloadHttpStatus", 400);
                String namedContainer = settings().resource("container");
                assertTrue(containers.contains(namedContainer), "Configured named fallback container is absent");
                Map<String, List<String>> named = service().getLogsFromPod(
                        exact(pod), namedContainer, logWindow());
                assertEquals(Set.of(pod), named.keySet(), "Named-container fallback target mismatch");
                assertNotNull(named.get(pod));
                detail("namedFallbackContainer", namedContainer);
                detail("namedFallbackStatus", "SUPPORTED");
                detail("namedFallbackLineCount", named.get(pod).size());
            }
            detail("logBody", "not attached; credentials and application data remain outside diagnostics");
        });
    }

    public void pkg010() {
        execute("CSP-010", () -> {
            stage("NAMED_CONTAINER_LOGS");
            Pod pod = selectedPod();
            String container = settings().resource("container");
            assertTrue(pod.getSpec().getContainers().stream().anyMatch(c -> container.equals(c.getName())),
                    "Configured container absent");
            Map<String, List<String>> logs = service().getLogsFromPod(exact(pod.getMetadata().getName()),
                    container, logWindow());
            assertEquals(Set.of(pod.getMetadata().getName()), logs.keySet());
            assertNotNull(logs.get(pod.getMetadata().getName()));
            detail("logLineCount", logs.get(pod.getMetadata().getName()).size());
        });
    }

    public void pkg011() {
        execute("CSP-011", () -> {
            stage("FILTERED_CONTAINER_LOGS");
            String pod = selectedPod().getMetadata().getName();
            List<String> filters = List.of("INFO", "WARN");
            Map<String, List<String>> logs = service().getLogsFromPod(exact(pod), settings().resource("container"),
                    logWindow(), filters);
            assertTrue(Set.of(pod).containsAll(logs.keySet()), "Unexpected log source");
            assertTrue(logs.values().stream().flatMap(List::stream)
                    .allMatch(line -> filters.stream().anyMatch(line::contains)), "Log filter contract violated");
            detail("matchedLineCount", logs.values().stream().mapToInt(List::size).sum());
            detail("filterNote", "Empty result is valid when the selected time window has no INFO/WARN lines");
        });
    }

    public void pkg012() {
        execute("CSP-012", () -> {
            stage("ABSENT_RESOURCE_CONTRACTS");
            String missing = "csp-absent-" + UUID.randomUUID().toString().substring(0, 12);
            // Establish API access first: a 401/403 must not masquerade as an expected negative case.
            assertNotNull(api().services().withName(settings().resource("service")).get());
            assertTrue(service().checkPodsStatus(exact(missing)).isEmpty(), "Absent pod must not appear");
            assertTrue(service().getConfigMap(missing).isEmpty(), "Absent ConfigMap must not appear");
            assertThrows(ru.sber.qa.containers.services.exeptions.impl.GetDataFromPodFailureException.class,
                    () -> service().getApplicationYml(exact(missing)));
            assertThrows(ru.sber.qa.containers.services.exeptions.impl.GetDataFromPodFailureException.class,
                    () -> service().getDataFromConfigMap(exact(missing)));
            assertThrows(ru.sber.qa.containers.services.exeptions.impl.GetDataFromPodFailureException.class,
                    () -> service().getLogsFromPod(exact(missing), Duration.ofSeconds(1)));
        });
    }

    public void pkg013() {
        execute("CSP-013", () -> {
            assertTrue(new ContainerPackageMutationSteps(this, flow).yamlFixture(), "YAML operations, restoration and deletion must be confirmed");
        });
    }
    public void pkg014() {
        execute("CSP-014", () -> {
            destructive("CSP-014", () -> new ContainerPackageMutationSteps(this, flow).restart("service"),
                    "Pod recreation must be confirmed");
        });
    }
    public void pkg015() {
        execute("CSP-015", () -> {
            destructive("CSP-015", () -> new ContainerPackageMutationSteps(this, flow).restart("flow"),
                    "Flow pod recreation must be confirmed");
        });
    }
    public void pkg016() {
        execute("CSP-016", () -> {
            destructive("CSP-016", () -> new ContainerPackageMutationSteps(this, flow)
                    .restart("without-unavailability"), "Replacement readiness must be confirmed");
        });
    }
    public void pkg017() {
        execute("CSP-017", () -> {
            destructive("CSP-017", () -> new ContainerPackageMutationSteps(this, flow).liveConfigMap(),
                    "Live ConfigMap restoration must be confirmed");
        });
    }

    private void destructive(String caseId, MutationAction action, String assertion) throws Exception {
        String blockedBy = DESTRUCTIVE_BLOCKER.get();
        if (blockedBy != null) {
            stage("DESTRUCTIVE_CASCADE_GUARD");
            detail("cascadeRootCase", blockedBy);
            detail("mutationDispatched", false);
            throw new IllegalStateException("CASCADE_FROM_" + blockedBy
                    + ": previous replacement did not become Ready; no mutation sent");
        }
        try {
            assertTrue(action.run(), assertion);
        } catch (RuntimeException | Error failure) {
            String category = String.valueOf(ContainerDiagnosticEvidence.classify(failure).get("category"));
            if ("NO_READY_POD".equals(category)) {
                DESTRUCTIVE_BLOCKER.compareAndSet(null, caseId);
                detail("destructiveBlockerEstablished", DESTRUCTIVE_BLOCKER.get());
            }
            throw failure;
        }
    }

    private KubernetesClientException kubernetesFailure(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 12; depth++, current = current.getCause())
            if (current instanceof KubernetesClientException result) return result;
        return null;
    }

    private Duration logWindow() {
        return Duration.ofSeconds(settings().number("logs.window.seconds", 3, 1, 30));
    }

    private <T> T diagnosticRead(KubernetesClient client, String operation, String kind, String name,
                                 Supplier<T> request) {
        try {
            return request.get();
        } catch (KubernetesClientException failure) {
            Map<String, Object> diagnostics = new LinkedHashMap<>(
                    ContainerServiceKubernetesAccess.failureDiagnostics(settings().connection(), client, failure));
            diagnostics.put("operation", operation);
            diagnostics.put("resourceKind", kind);
            diagnostics.put("resourceName", name);
            detail("safeKubernetesFailure", diagnostics);
            Map<String, Object> console = new LinkedHashMap<>();
            for (String key : List.of("operation", "resourceKind", "resourceName", "failureCode", "httpStatus",
                    "rootCauseType", "exceptionTypes", "mappingPath", "mappingLine", "mappingColumn",
                    "diagnosticFrame", "fabric8ClientVersion", "fabric8ModelVersion", "jacksonDatabindVersion",
                    "javaVersion")) {
                if (diagnostics.containsKey(key)) console.put(key, diagnostics.get(key));
            }
            System.err.println("[container-package-debug] "
                    + steps.rest.scheduler.SchedulerSteps.JSON.valueToTree(console));
            throw ContainerServiceKubernetesAccess.safeFailure(settings().connection(), operation, failure);
        }
    }
}
