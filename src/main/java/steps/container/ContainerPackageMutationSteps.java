package steps.container;

import config.services.container.ContainerDiagnosticsSettings;
import flow.ContainerPackageDiagnosticFlow;
import infrastructure.kubernetes.ContainerDiagnosticEvidence;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.yaml.snakeyaml.Yaml;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static infrastructure.kubernetes.ContainerDiagnosticEvidence.detail;
import static infrastructure.kubernetes.ContainerDiagnosticEvidence.stage;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Mutations are opt-in, one-cluster and one-workload. No ArgoCD or replica changes. */
public final class ContainerPackageMutationSteps {
    private final ContainerPackageDiagnosticSteps parent;
    private final ContainerPackageDiagnosticFlow flow;

    public ContainerPackageMutationSteps(ContainerPackageDiagnosticSteps parent, ContainerPackageDiagnosticFlow flow) {
        this.parent = parent;
        this.flow = flow;
    }

    private ContainerDiagnosticsSettings settings() { return parent.settings(); }
    private KubernetesClient api() { return parent.api(); }

    private void gate(String operation) {
        ContainerDiagnosticsSettings s = settings();
        assumeTrue(s.flag("mutations.enabled", false), "All mutations disabled in diagnostic profile");
        assumeTrue(s.flag("mutations." + operation + ".enabled", false), "This mutation is explicitly disabled");
        assertTrue(s.flag("dedicated-stand", false), "Mutation requires an explicitly dedicated stand");
        assertEquals(s.requiredMutationApproval(), s.required("mutations.approval"),
                "Mutation approval must identify the exact stand, namespace and deployment");
        detail("mutationApproval", s.requiredMutationApproval());
        detail("operation", operation);
    }

    /** Covers both update overloads and every ContainerServiceFlow YAML method on owned, harmless data. */
    public boolean yamlFixture() throws Exception {
        gate("yaml-fixture");
        stage("YAML_FIXTURE_CREATE");
        String run = UUID.randomUUID().toString();
        String name = "csp-yaml-" + run.substring(0, 12);
        Map<String, String> original = Map.of(
                "application.yml", "diagnostic:\n  mode: before\n  count: 1\n  items:\n    - before\nother:\n  value: keep\n",
                "diagnostic_marker", "before");
        ConfigMap template = new ConfigMapBuilder().withNewMetadata()
                .withName(name).withNamespace(api().getNamespace())
                .addToLabels("container-diagnostics-owner", run).endMetadata().withData(original).build();
        ConfigMap created = null;
        boolean attempted = false;
        boolean restored = false;
        boolean deleted = false;
        try {
            assertNull(api().configMaps().withName(name).get(), "Fixture name is already occupied");
            attempted = true;
            created = api().configMaps().resource(template).create();
            assertNotNull(created, "Fixture creation failed");
            ContainerDiagnosticEvidence.privateSnapshot("original-configmap", Serialization.asJson(created));
            uniqueMutableConfigMap(name);
            stage("YAML_SERVICE_AND_FLOW_READ");
            String selector = ContainerPackageDiagnosticSteps.exact(name);
            assertEquals(1, parent.service().getApplicationYml(selector).size());
            assertTrue(original.get("application.yml").equals(parent.service().getApplicationYml(selector).get(0)));
            assertTrue(original.get("application.yml").equals(
                    flow.applicationPlatform(settings().environment).getApplicationYml(selector).get(0)));

            stage("YAML_SINGLE_VALUE_UPDATE_READBACK");
            parent.service().updateValuesInApplicationYml(name, "diagnostic.mode", "single");
            assertYaml(name, "mode", "single");
            stage("YAML_MAP_UPDATE_READBACK");
            parent.service().updateValuesInApplicationYml(name,
                    Map.of("diagnostic.mode", "bulk", "diagnostic.count", 2, "diagnostic.items", "after"));
            assertYaml(name, "mode", "bulk");
            assertYaml(name, "count", 2);
            assertYaml(name, "items", "after");
            stage("YAML_FLOW_UPDATE_READBACK");
            flow.applicationPlatform(settings().environment).updateValuesInApplicationYml(name, "diagnostic.mode", "flow");
            assertYaml(name, "mode", "flow");
            ConfigMap updated = api().configMaps().withName(name).get();
            assertEquals("before", updated.getData().get("diagnostic_marker"), "Non-YAML key changed");
            Object other = ((Map<?, ?>) new Yaml().load(updated.getData().get("application.yml"))).get("other");
            assertEquals(Map.of("value", "keep"), other, "Unrelated YAML section changed");
        } finally {
            // Handle an interrupted JUnit test long enough to attempt restoration with bounded API calls.
            boolean interrupted = Thread.interrupted();
            try {
                if (attempted) {
                    detail("cleanupOperation", "YAML_FIXTURE_RESTORE_AND_DELETE");
                    ConfigMap current = api().configMaps().withName(name).get();
                    if (current != null) {
                        assertEquals(run, current.getMetadata().getLabels().get("container-diagnostics-owner"),
                                "Refusing to clean a fixture owned by someone else");
                        if (created != null)
                            assertEquals(created.getMetadata().getUid(), current.getMetadata().getUid(),
                                    "Fixture UID changed; manual recovery required");
                        current.setData(original);
                        api().configMaps().resource(current).replace();
                        ConfigMap after = api().configMaps().withName(name).get();
                        assertNotNull(after);
                        restored = original.equals(after.getData());
                        detail("fixtureRestorationConfirmed", restored);
                        assertTrue(restored, "Fixture restoration readback failed");
                        assertEquals(current.getMetadata().getUid(), after.getMetadata().getUid());
                        api().configMaps().resource(after).delete();
                        long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
                        while (System.nanoTime() < until && api().configMaps().withName(name).get() != null)
                            Thread.sleep(500);
                        deleted = api().configMaps().withName(name).get() == null;
                        detail("fixtureDeletionConfirmed", deleted);
                        assertTrue(deleted, "Owned fixture deletion not confirmed");
                    }
                }
            } finally { if (interrupted) Thread.currentThread().interrupt(); }
        }
        detail("fixtureRestoredAndDeleted", restored && deleted);
        // Fixture is not mounted by scheduler, so no scheduler restart is needed for this contract test.
        return restored && deleted;
    }

    private void assertYaml(String name, String key, Object expected) {
        ConfigMap cm = api().configMaps().withName(name).get();
        assertNotNull(cm, "Fixture ConfigMap disappeared");
        Object root = new Yaml().load(cm.getData().get("application.yml"));
        assertTrue(root instanceof Map, "YAML root is not a mapping");
        Object diagnostic = ((Map<?, ?>) root).get("diagnostic");
        assertTrue(diagnostic instanceof Map, "Diagnostic YAML block missing");
        assertTrue(Objects.equals(expected, ((Map<?, ?>) diagnostic).get(key)),
                "YAML readback failed for diagnostic key " + key);
    }

    private void uniqueMutableConfigMap(String name) {
        List<ConfigMap> matches = api().configMaps().list().getItems().stream()
                .filter(cm -> cm.getMetadata().getName().contains(name)).toList();
        assertEquals(1, matches.size(), "Framework substring selector is ambiguous; mutation prohibited");
        assertEquals(name, matches.get(0).getMetadata().getName(), "Framework would select another ConfigMap");
    }

    public boolean restart(String mode) throws Exception {
        gate("without-unavailability".equals(mode) ? "restart-without-unavailability" : "restart");
        return replaceOnePod(mode);
    }

    private boolean replaceOnePod(String mode) throws Exception {
        return replaceOnePod(mode, true);
    }

    private boolean replaceOnePod(String mode, boolean requireInitialReady) throws Exception {
        stage("POD_RECREATION_PREFLIGHT");
        var deployment = api().apps().deployments().withName(settings().resource("deployment")).get();
        assertNotNull(deployment, "Deployment missing");
        assertTrue(deployment.getSpec().getReplicas() != null && deployment.getSpec().getReplicas() > 0,
                "Controller must have a positive desired replica count");
        assertFalse(Boolean.TRUE.equals(deployment.getSpec().getPaused()), "Paused deployment is not a valid target");
        Pod pod = parent.selectedPod();
        if (requireInitialReady && !ContainerPackageDiagnosticSteps.ready(pod)) {
            detail("replacementFailureCode", "NO_READY_POD");
            detail("replacementReadiness", replacementReadiness(
                    deployment.getSpec().getSelector().getMatchLabels()));
            throw new IllegalStateException("NO_READY_POD: selected pod must initially be Ready; no mutation sent");
        }
        OwnerReference owner = pod.getMetadata().getOwnerReferences().stream()
                .filter(o -> Boolean.TRUE.equals(o.getController()) && "ReplicaSet".equals(o.getKind()))
                .findFirst().orElseThrow(() -> new AssertionError("Pod lacks a ReplicaSet controller"));
        var rs = api().apps().replicaSets().withName(owner.getName()).get();
        assertNotNull(rs, "ReplicaSet missing");
        assertEquals(owner.getUid(), rs.getMetadata().getUid(), "ReplicaSet identity changed");
        assertTrue(rs.getMetadata().getOwnerReferences().stream().anyMatch(o ->
                Boolean.TRUE.equals(o.getController()) && "Deployment".equals(o.getKind())
                        && deployment.getMetadata().getUid().equals(o.getUid())),
                "Selected pod does not belong to the approved Deployment");
        Map<String, String> selector = deployment.getSpec().getSelector().getMatchLabels();
        assertNotNull(selector, "Deployment selector missing");
        assertFalse(selector.isEmpty(), "Unbounded pod selection prohibited");
        List<Pod> before = api().pods().withLabels(selector).list().getItems();
        var oldUids = before.stream().map(p -> p.getMetadata().getUid()).collect(java.util.stream.Collectors.toSet());
        String name = pod.getMetadata().getName();
        Pod immediate = api().pods().withName(name).get();
        assertNotNull(immediate, "Target disappeared before deletion");
        assertEquals(pod.getMetadata().getUid(), immediate.getMetadata().getUid(), "Target UID changed");
        String mask = ContainerPackageDiagnosticSteps.exact(name);
        detail("deletedPodUid", pod.getMetadata().getUid());
        detail("restartContract", "Deletes one exact pod. No zero-downtime guarantee; controller creates replacement");
        stage("FRAMEWORK_POD_DELETE_" + mode.toUpperCase(java.util.Locale.ROOT));
        if ("flow".equals(mode)) flow.applicationPlatform(settings().environment).restartPods(mask);
        else if ("without-unavailability".equals(mode)) parent.service().restartPodsWithOutUnavailability(mask);
        else parent.service().restartPods(mask);

        stage("WAIT_NEW_UID_AND_READY");
        long replacementTimeoutSeconds = replacementTimeoutSeconds(pod);
        detail("effectiveReplacementTimeoutSeconds", replacementTimeoutSeconds);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(replacementTimeoutSeconds);
        while (System.nanoTime() < deadline) {
            var currentDeployment = api().apps().deployments().withName(settings().resource("deployment")).get();
            assertNotNull(currentDeployment, "Deployment disappeared");
            assertEquals(deployment.getMetadata().getUid(), currentDeployment.getMetadata().getUid(),
                    "Deployment replaced concurrently");
            assertEquals(deployment.getMetadata().getGeneration(), currentDeployment.getMetadata().getGeneration(),
                    "Deployment configuration changed concurrently; stop diagnostics");
            Pod old = api().pods().withName(name).get();
            List<Pod> replacements = api().pods().withLabels(selector).list().getItems().stream()
                    .filter(p -> !oldUids.contains(p.getMetadata().getUid()))
                    .filter(p -> p.getMetadata().getDeletionTimestamp() == null)
                    .filter(p -> p.getMetadata().getOwnerReferences().stream().anyMatch(o ->
                            owner.getUid().equals(o.getUid()) && Boolean.TRUE.equals(o.getController())))
                    .filter(ContainerPackageDiagnosticSteps::ready).toList();
            if (old == null && !replacements.isEmpty()) {
                Pod replacement = replacements.get(0);
                detail("replacementPod", replacement.getMetadata().getName());
                detail("replacementPodUid", replacement.getMetadata().getUid());
                return !pod.getMetadata().getUid().equals(replacement.getMetadata().getUid())
                        && ContainerPackageDiagnosticSteps.ready(replacement);
            }
            Thread.sleep(2000);
        }
        detail("replacementFailureCode", "NO_READY_POD");
        detail("replacementReadiness", replacementReadiness(selector));
        throw new IllegalStateException(
                "NO_READY_POD: POD_RECREATION_TIMEOUT; old pod disappearance and new Ready UID not confirmed");
    }

    private Map<String, Object> replacementReadiness(Map<String, String> selector) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        var deployment = api().apps().deployments().withName(settings().resource("deployment")).get();
        snapshot.put("observedAt", java.time.Instant.now().toString());
        snapshot.put("deploymentName", settings().resource("deployment"));
        snapshot.put("deploymentUid", deployment == null ? null : deployment.getMetadata().getUid());
        snapshot.put("deploymentGeneration", deployment == null ? null : deployment.getMetadata().getGeneration());
        snapshot.put("deploymentStatus", deployment == null ? null : deployment.getStatus());

        List<Pod> pods = api().pods().withLabels(selector).list().getItems().stream()
                .sorted(java.util.Comparator.comparing(p -> p.getMetadata().getName())).toList();
        List<Map<String, Object>> podSnapshots = new ArrayList<>();
        java.util.Set<String> podUids = new java.util.HashSet<>();
        int readyCount = 0;
        for (Pod pod : pods) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", pod.getMetadata().getName());
            item.put("uid", pod.getMetadata().getUid());
            item.put("deletionTimestamp", pod.getMetadata().getDeletionTimestamp());
            item.put("ready", ContainerPackageDiagnosticSteps.ready(pod));
            item.put("status", pod.getStatus());
            List<Map<String, Object>> probes = new ArrayList<>();
            if (pod.getSpec() != null && pod.getSpec().getContainers() != null) {
                pod.getSpec().getContainers().forEach(container -> {
                    Map<String, Object> probe = new LinkedHashMap<>();
                    probe.put("container", container.getName());
                    probe.put("readiness", probeSummary(container.getReadinessProbe()));
                    probe.put("liveness", probeSummary(container.getLivenessProbe()));
                    probe.put("startup", probeSummary(container.getStartupProbe()));
                    probes.add(probe);
                });
            }
            item.put("probes", probes);
            podSnapshots.add(item);
            if (pod.getMetadata().getUid() != null) podUids.add(pod.getMetadata().getUid());
            if (ContainerPackageDiagnosticSteps.ready(pod)) readyCount++;
        }
        snapshot.put("podCount", pods.size());
        snapshot.put("readyPodCount", readyCount);
        snapshot.put("pods", podSnapshots);

        List<Map<String, Object>> events = new ArrayList<>();
        api().v1().events().list().getItems().stream()
                .filter(event -> event.getInvolvedObject() != null
                        && podUids.contains(event.getInvolvedObject().getUid()))
                .sorted(java.util.Comparator.comparing(event -> String.valueOf(event.getLastTimestamp())))
                .forEach(event -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("pod", event.getInvolvedObject().getName());
                    item.put("type", event.getType());
                    item.put("reason", event.getReason());
                    item.put("count", event.getCount());
                    item.put("lastTimestamp", event.getLastTimestamp());
                    item.put("message", safeDiagnosticText(event.getMessage()));
                    events.add(item);
                });
        snapshot.put("events", events);
        snapshot.put("boundedStartupLogs", boundedStartupLogs(pods));
        return snapshot;
    }

    private long replacementTimeoutSeconds(Pod pod) {
        long configured = settings().number("rollout.timeout.seconds", 180, 30, 900);
        long margin = settings().number("rollout.timeout.margin.seconds", 60, 0, 300);
        if (pod == null || pod.getSpec() == null || pod.getSpec().getContainers() == null) return configured;
        String applicationContainer = settings().resource("container");
        return pod.getSpec().getContainers().stream()
                .filter(container -> Objects.equals(applicationContainer, container.getName()))
                .map(container -> container.getStartupProbe())
                .filter(Objects::nonNull)
                .findFirst()
                .map(probe -> {
                    long initialDelay = positiveOr(probe.getInitialDelaySeconds(), 0);
                    long period = positiveOr(probe.getPeriodSeconds(), 10);
                    long timeout = positiveOr(probe.getTimeoutSeconds(), 1);
                    long failures = positiveOr(probe.getFailureThreshold(), 3);
                    long startupProbeBudget = initialDelay + period * failures + timeout;
                    detail("startupProbeBudgetSeconds", startupProbeBudget);
                    detail("replacementTimeoutMarginSeconds", margin);
                    return Math.min(900L, Math.max(configured, startupProbeBudget + margin));
                })
                .orElse(configured);
    }

    private static long positiveOr(Integer value, int fallback) {
        return value == null || value < 0 ? fallback : value.longValue();
    }

    private Map<String, Object> boundedStartupLogs(List<Pod> pods) {
        Map<String, Object> result = new LinkedHashMap<>();
        Set<String> requested = Set.of(settings().resource("container"), "istio-proxy", "vault-agent", "fluentbit");
        int maxLines = settings().number("logs.startup.max.lines", 200, 20, 500);
        long windowSeconds = settings().number("logs.startup.window.seconds", 600, 30, 900);
        for (Pod pod : pods) {
            if (pod == null || pod.getMetadata() == null || pod.getSpec() == null) continue;
            String podName = pod.getMetadata().getName();
            if (podName == null) continue;
            for (var container : pod.getSpec().getContainers()) {
                String containerName = container.getName();
                if (!requested.contains(containerName)) continue;
                String key = podName + "/" + containerName;
                try {
                    Map<String, List<String>> logs = parent.service().getLogsFromPod(
                            ContainerPackageDiagnosticSteps.exact(podName), containerName,
                            Duration.ofSeconds(windowSeconds));
                    List<String> lines = logs.values().stream().flatMap(List::stream).toList();
                    int first = Math.max(0, lines.size() - maxLines);
                    result.put(key, lines.subList(first, lines.size()).stream()
                            .map(this::safeDiagnosticText).toList());
                } catch (RuntimeException failure) {
                    result.put(key, Map.of("status", "UNAVAILABLE",
                            "failureType", failure.getClass().getSimpleName()));
                }
            }
        }
        return result;
    }

    private Map<String, Object> probeSummary(io.fabric8.kubernetes.api.model.Probe probe) {
        if (probe == null) return Map.of("configured", false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", true);
        result.put("mode", probe.getHttpGet() != null ? "HTTP_GET"
                : probe.getTcpSocket() != null ? "TCP_SOCKET"
                : probe.getExec() != null ? "EXEC"
                : probe.getGrpc() != null ? "GRPC" : "UNKNOWN");
        result.put("initialDelaySeconds", probe.getInitialDelaySeconds());
        result.put("periodSeconds", probe.getPeriodSeconds());
        result.put("timeoutSeconds", probe.getTimeoutSeconds());
        result.put("failureThreshold", probe.getFailureThreshold());
        result.put("successThreshold", probe.getSuccessThreshold());
        return result;
    }

    private String safeDiagnosticText(String value) {
        if (value == null) return null;
        String bounded = value.length() > 1000 ? value.substring(0, 1000) + "..." : value;
        return bounded
                .replaceAll("(?i)(bearer\\s+)[^\\s,;]+", "$1<redacted>")
                .replaceAll("(?i)((?:authorization|token|password|secret)\\s*[:=]\\s*)[^\\s,;]+",
                        "$1<redacted>")
                .replaceAll("(?i)(employeeId|sessionLogin|sessionId|clientIp)=[^,;\\s]+", "$1=<redacted>")
                .replaceAll("(?i)(firstName|lastName|middleName|fio)=[^,;]+", "$1=<redacted>");
    }

    /** Flat environment-variable data is tested separately from application.yml. */
    public boolean liveConfigMap() throws Exception {
        stage("LIVE_CONFIGMAP_PREFLIGHT");
        detail("configMapMutationDispatched", false);
        gate("configmap");
        assertTrue(settings().flag("mutations.restart.enabled", false),
                "Live ConfigMap update requires approved pod recreation for apply AND rollback");
        String name = settings().resource("configmap");
        uniqueMutableConfigMap(name);
        ConfigMap original = parent.configMap();
        String key = settings().required("mutations.configmap.key");
        String value = settings().required("mutations.configmap.value");
        assertTrue(key.matches("[A-Z][A-Z0-9_]+"), "Only an explicitly approved flat environment key is supported");
        assertFalse(key.matches(".*(TOKEN|PASSWORD|SECRET|KEYSTORE|TRUSTSTORE).*"), "Secret-like keys prohibited");
        assertTrue(original.getData().containsKey(key), "Framework cannot safely add a missing key");
        assertTrue(SetValues.BOOLEANS.contains(original.getData().get(key))
                        && SetValues.BOOLEANS.contains(value), "This diagnostic only toggles a boolean flag");
        // Log only after the approved key and both non-secret boolean values have been checked.
        detail("configMapProbeKey", key);
        detail("configMapProbeCurrentValue", original.getData().get(key));
        detail("configMapProbeConfiguredValue", value);
        if (value.equals(original.getData().get(key))) {
            stage("LIVE_CONFIGMAP_NO_CHANGE");
            detail("preflightReason", "CONFIGMAP_PROBE_NO_CHANGE");
            assumeTrue(false, "CONFIGMAP_PROBE_NO_CHANGE: configured probe already equals the live value; "
                    + "no ConfigMap mutation or pod deletion was sent");
        }
        assertMountedByDeployment(name);
        ContainerDiagnosticEvidence.privateSnapshot("original-" + name, Serialization.asJson(original));
        Map<String, String> expected = new LinkedHashMap<>(original.getData());
        expected.put(key, value);
        boolean attempted = false;
        boolean restored = false;
        boolean updatedReplacementReady = false;
        try {
            // Re-read before the library's non-CAS patch. Cooperating operators must not run concurrently.
            ConfigMap before = api().configMaps().withName(name).get();
            assertNotNull(before);
            assertEquals(original.getMetadata().getResourceVersion(), before.getMetadata().getResourceVersion(),
                    "ConfigMap changed after backup; no mutation performed");
            attempted = true;
            detail("configMapMutationDispatched", true);
            stage("FLAT_CONFIGMAP_UPDATE_AND_READBACK");
            parent.service().updateValuesInDataInConfigMap(name, Map.of(key, value));
            ConfigMap after = api().configMaps().withName(name).get();
            assertNotNull(after);
            assertEquals(original.getMetadata().getUid(), after.getMetadata().getUid(), "ConfigMap identity changed");
            assertTrue(expected.equals(after.getData()), "ConfigMap readback differs from the approved update");
            assertNotEquals(original.getMetadata().getResourceVersion(), after.getMetadata().getResourceVersion(),
                    "ResourceVersion must change");
            detail("configMapVersionAfterUpdate", after.getMetadata().getResourceVersion());
            assertTrue(replaceOnePod("service"), "Updated ConfigMap did not yield a Ready replacement");
            updatedReplacementReady = true;
            ConfigMap applied = api().configMaps().withName(name).get();
            assertTrue(applied != null && expected.equals(applied.getData()),
                    "ConfigMap reverted during pod recreation");
        } finally {
            boolean interrupted = Thread.interrupted();
            try {
                if (attempted) {
                    stage("LIVE_CONFIGMAP_RESTORE");
                    ConfigMap current = api().configMaps().withName(name).get();
                    assertNotNull(current, "ConfigMap missing; use private recovery artifact");
                    assertEquals(original.getMetadata().getUid(), current.getMetadata().getUid(),
                            "Refusing to overwrite a replaced ConfigMap");
                    assertTrue(current.getData().equals(expected) || current.getData().equals(original.getData()),
                            "Concurrent ConfigMap edit detected; refusing to overwrite it. Manual recovery required");
                    current.setData(new LinkedHashMap<>(original.getData()));
                    api().configMaps().resource(current).replace();
                    ConfigMap afterRestore = api().configMaps().withName(name).get();
                    assertNotNull(afterRestore);
                    assertTrue(original.getData().equals(afterRestore.getData()), "Restoration readback failed");
                    // Only a confirmed Ready pod may be replaced again. If the first replacement failed,
                    // restoring the ConfigMap is safe but a second automatic delete would amplify the outage.
                    if (updatedReplacementReady) {
                        assertTrue(replaceOnePod("service", false), "Restored ConfigMap pod recreation failed");
                    } else {
                        detail("rollbackPodRecreation", "DEFERRED_AFTER_UNREADY_REPLACEMENT");
                        detail("rollbackManualAction",
                                "ConfigMap restored by GET; inspect workload readiness before any further pod deletion");
                    }
                    ConfigMap finalState = api().configMaps().withName(name).get();
                    restored = finalState != null && original.getData().equals(finalState.getData());
                    assertTrue(restored, "ConfigMap changed again after restoration");
                    detail("restorationConfirmed", restored);
                }
            } finally { if (interrupted) Thread.currentThread().interrupt(); }
        }
        return restored;
    }

    private void assertMountedByDeployment(String name) {
        var deployment = api().apps().deployments().withName(settings().resource("deployment")).get();
        assertNotNull(deployment);
        var podSpec = deployment.getSpec().getTemplate().getSpec();
        boolean referenced = podSpec.getContainers().stream().anyMatch(c ->
                (c.getEnvFrom() != null && c.getEnvFrom().stream().anyMatch(e ->
                        e.getConfigMapRef() != null && name.equals(e.getConfigMapRef().getName())))
                || (c.getEnv() != null && c.getEnv().stream().anyMatch(e ->
                        e.getValueFrom() != null && e.getValueFrom().getConfigMapKeyRef() != null
                                && name.equals(e.getValueFrom().getConfigMapKeyRef().getName()))))
                || (podSpec.getVolumes() != null && podSpec.getVolumes().stream().anyMatch(v ->
                        v.getConfigMap() != null && name.equals(v.getConfigMap().getName())));
        assertTrue(referenced, "Deployment does not reference the selected ConfigMap");
        detail("configurationApplicationProof",
                "ConfigMap readback plus reference in pod template plus replacement Ready; not an application-level flag assertion");
    }

    private static final class SetValues {
        static final java.util.Set<String> BOOLEANS = java.util.Set.of("true", "false");
    }
}
