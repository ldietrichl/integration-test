package infrastructure.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;

import config.services.container.ContainerServiceKubernetesAccess;
import config.services.container.KubernetesTunnelSettings;
import config.services.core.StandSettings;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.qameta.allure.Allure;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A narrowly-scoped mutation session owning a Fabric8 client until rollback finishes.
 * Field writes are resourceVersion guarded; pod deletion rechecks the exact UID immediately before dispatch.
 * Every change saves private pre-images first; cleanup restores configuration before recreating pods.
 * No shell command, exec, Secret read, cluster mutation, force replace, or automatic request replay.
 */
public final class KubernetesWorkloadControl implements AutoCloseable {
    private final KubernetesTunnelSettings settings;
    private final KubernetesClient api;
    private final StandSettings stand = new StandSettings();
    private final String prefix;
    private final List<Undo> undo = new ArrayList<>();
    private WorkloadArtifacts artifacts;
    private final Set<String> originalSaved = new HashSet<>();
    private final WorkloadEvidence evidence;
    private JsonNode baseline;
    private String baselineReplicaSetUid;
    private boolean podMutationAttempted;
    private boolean fieldMutationAttempted;
    private boolean restoredConfigNeedsReplacement;
    private int expectedReplicas = 1;
    private Instant operationStarted = Instant.now();
    private final Set<String> deletedPodUids = new HashSet<>();
    private record State(JsonNode deployment, List<JsonNode> replicaSets, List<JsonNode> pods) {}

    private boolean closed;
    private boolean restored;
    private boolean configurationRestored;
    private WorkloadReadinessProbe rootProbe;
    private boolean rootHttpReady;
    private WorkloadAvailabilityProbe availabilityProbe;

    public void availabilityProbe(WorkloadAvailabilityProbe probe) {
        if (closed || mutationAttempted() || availabilityProbe != null)
            throw new IllegalStateException("Availability probe must be registered before mutations");
        availabilityProbe = Objects.requireNonNull(probe);
    }

    public void awaitAvailability() {
        if (closed) throw new IllegalStateException("Mutation session closed");
        if (availabilityProbe == null) return;
        int timeout = stand.integer(prefix + "readiness.timeout.seconds", 120, 1, 600);
        int poll = stand.integer(prefix + "readiness.poll.millis", 2000, 10, 10000);
        try {
            WorkloadReadinessWait.await(this::availabilitySample, timeout, poll);
        } catch (KubernetesDiagnosticException safe) {
            throw safe;
        } catch (RuntimeException failure) {
            throw new KubernetesDiagnosticException("WORKLOAD_READINESS_KUBERNETES_"
                    + ContainerServiceKubernetesAccess.failureCode(failure), "SERVICE_ENDPOINTS", failure);
        }
    }

    private boolean availabilitySample() {
        State current = state();
        int desired = current.deployment().at("/spec/replicas").asInt(1);
        if (!deploymentReady(current.deployment()) || desired < 1 || current.pods().size() != desired
                || current.pods().stream().anyMatch(p -> !podReady(p))) return false;
        JsonNode service = WorkloadJson.JSON.valueToTree(api.services().inNamespace(settings.namespace)
                .withName(settings.service).get());
        JsonNode endpoints = WorkloadJson.JSON.valueToTree(api.endpoints().inNamespace(settings.namespace)
                .withName(settings.service).get());
        boolean endpointsReady = ServiceEndpointReadiness.ready(service, endpoints, current.pods(), settings.servicePort);
        System.out.println("[workload-endpoints-ready] ready=" + endpointsReady + "; currentOwnedPodUidsRequired=true");
        return endpointsReady && availabilityProbe.ready();
    }

    public void rootReadinessProbe(WorkloadReadinessProbe probe) {
        if (rootProbe != null || closed) throw new IllegalStateException("Root readiness probe already registered/closed");
        rootProbe = Objects.requireNonNull(probe);
    }
    private String manualRecoveryMessage;
    private boolean diagnosticsAttached;
    private boolean allureAttachmentsEnabled = true;
    private final Map<String, String> originalFixtureFlags = new TreeMap<>();
    private String fixtureConfigMapUid;
    private WorkloadRunLease regressionLease;
    private final Map<String, WorkloadRunLease> stateLeases = new TreeMap<>();
    private Map<String, ConfigMapState> managedState = Map.of();

    public int configurationWrites() { return configWritesConfirmed; }

    /** Resolve an existing consumed environment key, never invent a key in an arbitrary ConfigMap. */
    public List<ConfigMapState> environmentState(Map<String, String> desired) {
        if (closed) throw new IllegalStateException("Mutation session closed");
        JsonNode deployment = read("deployment", name("deployment"));
        JsonNode container = null;
        for (JsonNode item : deployment.at("/spec/template/spec/containers"))
            if (settings.logsContainer.equals(item.path("name").asText())) container = item;
        if (container == null) throw new IllegalStateException("WORKLOAD_CONFIG_CONTAINER_NOT_FOUND");
        Map<String, Map<String, String>> maps = new TreeMap<>();
        for (var entry : desired.entrySet()) {
            String cmName = null;
            String dataKey = null;
            for (JsonNode env : container.path("env")) if (entry.getKey().equals(env.path("name").asText())) {
                JsonNode ref = env.at("/valueFrom/configMapKeyRef");
                if (!ref.isObject()) throw new IllegalStateException("WORKLOAD_CONFIG_NOT_CONFIGMAP_BOUND: " + entry.getKey());
                cmName = ref.path("name").asText();
                dataKey = ref.path("key").asText();
            }
            if (cmName == null) for (JsonNode source : container.path("envFrom")) {
                String sourceName = source.at("/configMapRef/name").asText(null);
                String envPrefix = source.path("prefix").asText("");
                if (sourceName == null || !entry.getKey().startsWith(envPrefix)) continue;
                String candidateKey = entry.getKey().substring(envPrefix.length());
                if (read("configmap", sourceName).path("data").has(candidateKey)) {
                    if (cmName != null) throw new IllegalStateException("WORKLOAD_CONFIG_AMBIGUOUS: " + entry.getKey());
                    cmName = sourceName; dataKey = candidateKey;
                }
            }
            if (cmName == null || !read("configmap", cmName).path("data").has(dataKey))
                throw new IllegalStateException("WORKLOAD_CONFIG_BINDING_REQUIRED: " + entry.getKey()
                        + "; expected an existing envFrom/configMapKeyRef binding on the application container");
            assertConfigMapReference(deployment, cmName);
            maps.computeIfAbsent(cmName, ignored -> new TreeMap<>()).put(dataKey, entry.getValue());
            System.out.println("[workload-config-binding] env=" + entry.getKey() + "; configMap=" + cmName + "; key=" + dataKey);
        }
        return maps.entrySet().stream().map(e -> new ConfigMapState(e.getKey(), e.getValue())).toList();
    }

    /** A cached session is never trusted without checking ownership and the last applied values. */
    public void requireManagedState() {
        if (closed || managedState.isEmpty()) throw new IllegalStateException("WORKLOAD_STATE_NOT_PREPARED");
        stateLeases.values().forEach(WorkloadRunLease::requireHeld);
        assertBaseline(read("deployment", name("deployment")));
        for (ConfigMapState state : managedState.values()) {
            JsonNode current = read("configmap", state.name());
            state.data().forEach((key, value) -> assertTrue(ConfigMapState.equivalent(key, value,
                    current.at("/data/" + escape(key)).asText(null)), "WORKLOAD_STATE_CHANGED: " + state.name() + "/" + key));
        }
    }

    /** Validate every target before writing data. Locks use the existing Scheduler protocol for compatibility. */
    public void applyState(List<ConfigMapState> states) {
        if (closed) throw new IllegalStateException("Mutation session closed");
        if (states == null || states.isEmpty()) throw new IllegalArgumentException("ConfigMap state required");
        Map<String, ConfigMapState> ordered = new TreeMap<>();
        for (ConfigMapState state : states)
            if (ordered.putIfAbsent(state.name(), state) != null)
                throw new IllegalArgumentException("Duplicate ConfigMap in one state: " + state.name());
        if (!stateLeases.isEmpty()) {
            requireManagedState();
            if (!ordered.keySet().equals(managedState.keySet()) || ordered.values().stream().anyMatch(
                    state -> !state.data().keySet().equals(managedState.get(state.name()).data().keySet())))
                throw new IllegalStateException("WORKLOAD_STATE_SCOPE_CHANGED: keep the same ConfigMaps and keys within a run");
            for (ConfigMapState state : ordered.values()) {
                stateLeases.values().forEach(WorkloadRunLease::requireHeld);
                applyReferencedConfigMapData(state.name(), state.data(), "managed-transition");
            }
            managedState = Map.copyOf(ordered);
            compactManagedUndo();
            return;
        }
        permit("configmap");
        permit("restart");
        permit("pod-delete");
        prepareDeletion();
        for (ConfigMapState state : ordered.values()) {
            assertConfigMapReference(baseline, state.name());
            JsonNode cm = read("configmap", state.name());
            if (cm.path("immutable").asBoolean(false) || !cm.path("data").isObject())
                throw new IllegalStateException("Mutable ConfigMap data required: " + state.name());
        }
        Set<String> lockNames = new TreeSet<>(ordered.keySet());
        String workloadLock = name("configmap");
        assertConfigMapReference(baseline, workloadLock);
        lockNames.add(workloadLock);
        for (String name : lockNames) {
            WorkloadRunLease lease = new WorkloadRunLease(() -> read("configmap", name), operations ->
                    api.configMaps().inNamespace(settings.namespace).withName(name).patch(
                            io.fabric8.kubernetes.client.dsl.base.PatchContext.of(
                                    io.fabric8.kubernetes.client.dsl.base.PatchType.JSON), operations));
            stateLeases.put(name, lease);
            artifacts.event("LEASE_ACQUIRE_INTENT", WorkloadJson.JSON.valueToTree(Map.of(
                    "namespace", settings.namespace, "context", settings.context(), "configMap", name,
                    "uid", read("configmap", name).at("/metadata/uid").asText(), "owner", lease.owner())));
            lease.acquire();
            artifacts.event("LEASE_ACQUIRED", WorkloadJson.JSON.valueToTree(Map.of("configMap", name, "owner", lease.owner())));
        }
        for (ConfigMapState state : ordered.values()) {
            stateLeases.values().forEach(WorkloadRunLease::requireHeld);
            applyReferencedConfigMapData(state.name(), state.data(), "managed-state");
        }
        managedState = Map.copyOf(ordered);
    }

    private void compactManagedUndo() {
        // Successful transitions retain the original pre-image and latest owned value, not intermediate modes.
        Map<String, Undo> fields = new LinkedHashMap<>();
        for (Undo change : undo) {
            String key = change.kind() + "/" + change.name() + change.pointer();
            Undo first = fields.get(key);
            if (first == null) fields.put(key, change);
            else {
                assertEquals(first.uid(), change.uid(), "Managed resource identity changed");
                fields.put(key, new Undo(change.kind(), change.name(), change.uid(), change.pointer(), first.before(), change.written()));
            }
        }
        undo.clear();
        undo.addAll(fields.values());
    }
    public void acquireRegressionLease() {
        permit("configmap");
        String cmName = name("configmap");
        if (regressionLease != null) throw new IllegalStateException("Regression lease already initialized");
        regressionLease = new WorkloadRunLease(() -> read("configmap", cmName), operations ->
                api.configMaps().inNamespace(settings.namespace).withName(cmName)
                        .patch(io.fabric8.kubernetes.client.dsl.base.PatchContext.of(
                                io.fabric8.kubernetes.client.dsl.base.PatchType.JSON), operations));
        artifacts.event("LEASE_ACQUIRE_INTENT", WorkloadJson.JSON.valueToTree(Map.of("namespace", settings.namespace,
                "context", settings.context(), "configMap", cmName, "owner", regressionLease.owner(),
                "uid", read("configmap", cmName).at("/metadata/uid").asText())));
        regressionLease.acquire();
        artifacts.event("LEASE_ACQUIRED", WorkloadJson.JSON.valueToTree(Map.of("configMap", cmName, "owner", regressionLease.owner())));
    }
    public void assertRegressionLease() {
        if (regressionLease == null || !regressionLease.acquired())
            throw new IllegalStateException("REGRESSION_RUN_LEASE_REQUIRED");
        regressionLease.requireHeld();
    }

    private int configWritesConfirmed;
    private int configRestoreGetsConfirmed;
    private int podReplacementsConfirmed;
    private int podDeleteDispatches;
    private String replacementReason = "explicit-workload-operation";
    public void replacementReason(String reason) {
        if (reason == null || !reason.matches("[a-z0-9-]{1,80}")) throw new IllegalArgumentException("Safe reason code required");
        replacementReason = reason;
    }
    private boolean rollbackPodRecreated;
    private Set<String> lastReadyPodUids = Set.of();
    public boolean isRestored() { return restored; }

    public String restorationSummary() {
        return "configState=" + (!configurationRestored ? "UNCONFIRMED"
                : configWritesConfirmed == 0 ? "NO_CHANGE" : "RESTORED")
                + "; configWritesConfirmed=" + configWritesConfirmed
                + "; configRestoreGetsConfirmed=" + configRestoreGetsConfirmed
                + "; originalFlagsVerified=" + (configurationRestored && !originalFixtureFlags.isEmpty())
                + "; rollbackPodRecreated=" + rollbackPodRecreated
                + "; podDeleteDispatches=" + podDeleteDispatches
                + "; podReplacementsConfirmed=" + podReplacementsConfirmed
                + "; currentReadyPodUids=" + lastReadyPodUids
                + "; rootHttp=" + (rootProbe == null ? "NOT_REQUESTED" : rootHttpReady ? "READY" : "UNCONFIRMED");
    }
    public boolean mutationAttempted() { return fieldMutationAttempted || podMutationAttempted; }
    public void disableAllureAttachments() {
        allureAttachmentsEnabled = false;
        evidence.disableAllureAttachments();
    }
    private record Undo(String kind, String name, String uid, String pointer, JsonNode before, JsonNode written) {}

    private void attachment(String name, String value) {
        if (allureAttachmentsEnabled) Allure.addAttachment(name, value);
    }

    private void attachment(String name, String type, String value, String extension) {
        if (allureAttachmentsEnabled) Allure.addAttachment(name, type, value, extension);
    }
    public KubernetesWorkloadControl(KubernetesTunnelSettings settings, String workload) {
        this(settings, workload, () -> ContainerServiceKubernetesAccess.ownedNativeWorkloadClient(settings));
    }

    KubernetesWorkloadControl(KubernetesTunnelSettings settings, String workload,
                              java.util.function.Supplier<KubernetesClient> clientFactory) {
        if (!workload.matches("[a-z][a-z0-9-]*")) throw new IllegalArgumentException("Workload profile name required");
        this.settings = settings;
        this.api = Objects.requireNonNull(clientFactory.get(), "Native workload client");
        this.evidence = new WorkloadEvidence(settings, api);
        this.prefix = "workloads." + workload + ".";
    }
    private void permit(String operation) {
        assumeTrue(stand.flag("mutations.enabled")
                && stand.flag("mutations." + operation + ".enabled"),
                "SKIPPED: explicit namespace and per-operation mutation permission required");
        if (!Set.of("dev", "ift", "lt").contains(settings.environment)
                || !settings.environment.equals(stand.environment)
                || !settings.namespace.equals(stand.required("mutations.namespace-confirmation")))
            throw new IllegalStateException("Mutation namespace/environment confirmation does not match selected stand");
        settings.requireNativeManagement();
        if (!diagnosticsAttached) {
            diagnosticsAttached = true;
            attachment("ContainerService management context", "application/json",
                    WorkloadJson.JSON.valueToTree(ContainerServiceKubernetesAccess.safeDiagnostics(settings, api))
                            .toPrettyString(), ".json");
        }
        if (artifacts == null) artifacts = new WorkloadArtifacts(settings.environment, () -> allureAttachmentsEnabled);
    }
    private String name(String suffix) {
        String value = stand.required(prefix + suffix);
        return resourceName(value, suffix);
    }

    private String resourceName(String value, String suffix) {
        if (!value.matches("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?"))
            throw new IllegalArgumentException("Exact Kubernetes name required: " + suffix);
        return value;
    }
    private JsonNode read(String kind, String name) {
        if (!Set.of("deployment", "configmap", "service", "pods", "replicasets", "hpa", "resourcequota").contains(kind))
            throw new IllegalArgumentException("Unsupported read kind");
        boolean retryUsed = false;
        while (true) {
            try {
                return readOnce(kind, name);
            } catch (RuntimeException failure) {
                if (retryUsed || !retryableReadFailure(failure)) throw failure;
                retryUsed = true;
                Thread.interrupted();
                System.out.println("[workload-control] Retrying one Fabric8 GET/LIST after interrupted/read I/O; no mutation is replayed");
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ignored) {
                    Thread.interrupted();
                }
            }
        }
    }

    private JsonNode readOnce(String kind, String name) {
        try {
            Object resource = switch (kind) {
                case "deployment" -> api.apps().deployments().inNamespace(settings.namespace).withName(name).get();
                case "configmap" -> api.configMaps().inNamespace(settings.namespace).withName(name).get();
                case "service" -> api.services().inNamespace(settings.namespace).withName(name).get();
                case "pods" -> api.pods().inNamespace(settings.namespace).list();
                case "replicasets" -> api.apps().replicaSets().inNamespace(settings.namespace).list();
                case "hpa" -> listHorizontalPodAutoscalers();
                case "resourcequota" -> api.resourceQuotas().inNamespace(settings.namespace).list();
                default -> throw new IllegalArgumentException("Unsupported read kind");
            };
            if (resource == null) throw new IllegalStateException("WORKLOAD_READ_RESOURCE_NOT_FOUND");
            return WorkloadJson.JSON.readTree(Serialization.asJson(resource));
        } catch (KubernetesClientException failure) {
            Map<String, Object> context = new java.util.LinkedHashMap<>();
            context.put("resourceKind", kind);
            if (name != null) context.put("resourceName", name);
            context.put("operation", name == null ? "LIST" : "GET");
            context.put("expectedModel", switch (kind) {
                case "deployment" -> "io.fabric8.kubernetes.api.model.apps.Deployment";
                case "configmap" -> "io.fabric8.kubernetes.api.model.ConfigMap";
                case "service" -> "io.fabric8.kubernetes.api.model.Service";
                default -> "Fabric8 list model";
            });
            throw kubernetesFailure("WORKLOAD_READ", failure, "", context);
        } catch (IllegalStateException failure) {
            throw failure;
        } catch (Exception invalid) {
            throw new IllegalStateException("WORKLOAD_READ_INVALID_JSON");
        }
    }
    private IllegalStateException kubernetesFailure(
            String operation, KubernetesClientException failure, String suffix) {
        return kubernetesFailure(operation, failure, suffix, Map.of());
    }
    private IllegalStateException kubernetesFailure(
            String operation, KubernetesClientException failure, String suffix, Map<String, Object> context) {
        Map<String, Object> diagnostics = new LinkedHashMap<>(
                ContainerServiceKubernetesAccess.failureDiagnostics(settings, api, failure));
        diagnostics.put("operation", operation);
        diagnostics.put("recoveryNote", suffix);
        diagnostics.putAll(context);
        Map<String, Object> console = new LinkedHashMap<>();
        for (String key : List.of("operation", "resourceKind", "resourceName", "expectedModel", "failureCode",
                "httpStatus", "rootCauseType", "exceptionTypes", "mappingPath", "mappingLine", "mappingColumn",
                "diagnosticFrame", "fabric8ClientVersion", "fabric8ModelVersion", "jacksonDatabindVersion",
                "javaVersion")) {
            if (diagnostics.containsKey(key)) console.put(key, diagnostics.get(key));
        }
        System.err.println("[workload-kubernetes-debug] " + WorkloadJson.JSON.valueToTree(console));
        try {
            if (artifacts == null) artifacts = new WorkloadArtifacts(settings.environment, () -> allureAttachmentsEnabled);
            artifacts.save(operation.toLowerCase(Locale.ROOT) + "-transport-"
                    + UUID.randomUUID() + ".json", WorkloadJson.JSON.valueToTree(diagnostics));
        } catch (RuntimeException ignored) {
            // A private artifact is best effort and must not replace the original Kubernetes failure.
        }
        try {
            attachment(operation + " - safe Kubernetes transport diagnostics", "application/json",
                    WorkloadJson.JSON.valueToTree(diagnostics).toPrettyString(), ".json");
        } catch (RuntimeException ignored) {
            // Diagnostic attachment failure must not replace the original infrastructure failure.
        }
        return ContainerServiceKubernetesAccess.safeFailure(settings, operation, failure);
    }
    private JsonNode deployment() {
        JsonNode deployment = read("deployment", name("deployment"));
        JsonNode service = read("service", settings.service);
        JsonNode selector = service.at("/spec/selector");
        if (!selector.isObject() || selector.isEmpty())
            throw new IllegalStateException("Service selector required");
        selector.fields().forEachRemaining(e -> {
            if (!e.getValue().equals(deployment.at("/spec/template/metadata/labels/" + escape(e.getKey()))))
                throw new IllegalStateException("Configured Deployment is not selected by the service");
        });
        if (!deployment.at("/spec/selector/matchExpressions").isMissingNode()
                && !deployment.at("/spec/selector/matchExpressions").isEmpty())
            throw new IllegalStateException("Expression-based Deployment selectors require a dedicated implementation");
        if (deployment.at("/spec/replicas").asInt(1) < 1 || deployment.at("/spec/paused").asBoolean(false))
            throw new IllegalStateException("Ready, non-paused Deployment with replicas >= 1 required");
        return deployment;
    }
    private Object listHorizontalPodAutoscalers() {
        try {
            Object result = api.autoscaling().v2().horizontalPodAutoscalers()
                    .inNamespace(settings.namespace).list();
            attachHpaApiSelection("autoscaling/v2", false, "READY");
            return result;
        } catch (KubernetesClientException v2Failure) {
            if (v2Failure.getCode() != 404) throw v2Failure;
            try {
                Object result = api.autoscaling().v1().horizontalPodAutoscalers()
                        .inNamespace(settings.namespace).list();
                attachHpaApiSelection("autoscaling/v1", true, "READY");
                return result;
            } catch (KubernetesClientException v1Failure) {
                if (v1Failure.getCode() != 404) throw v1Failure;
                attachHpaApiSelection("none", true, "API_UNAVAILABLE");
                throw new IllegalStateException("WORKLOAD_HPA_API_UNAVAILABLE: neither autoscaling/v2 nor "
                        + "autoscaling/v1 is served; replica mutation was not dispatched", v1Failure);
            }
        }
    }

    private void attachHpaApiSelection(String selectedApi, boolean fallbackUsed, String status) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("selectedApi", selectedApi);
        details.put("fallbackUsed", fallbackUsed);
        details.put("status", status);
        details.put("namespace", settings.namespace);
        details.put("safetyContract", "HPA availability must be established before replica mutation");
        details.put("credentials", "not attached");
        attachment("HPA API selection", WorkloadJson.JSON.valueToTree(details).toPrettyString());
    }

    private String configMapOwnerTestFullName;
    private void snapshot(String kind, String name, JsonNode value) {
        if (originalSaved.add(kind + "/" + name)) {
            artifacts.save("original-" + kind + "-" + name + ".json", value);
            if ("configmap".equals(kind)) {
                configMapOwnerTestFullName = WorkloadReportContext.currentFullName();
                attachConfigMap("ConfigMap: исходное состояние (чувствительные значения скрыты)", value);
            }
        }
    }
    private void attachConfigMap(String title, JsonNode value) {
        attachment(title, safeConfigMap(value).toPrettyString());
    }
    private JsonNode safeConfigMap(JsonNode value) {
        Set<String> keys = new HashSet<>(Arrays.asList(stand.optional(prefix + "fixtures.job-keys", "").split(",")));
        keys.removeIf(key -> !key.matches("[A-Z0-9_]+_ENABLED"));
        return WorkloadConfigMapEvidence.sanitize(value, keys);
    }
    public void attachCurrentConfigMap() {
        attachConfigMap("ConfigMap перед сценарием: подтверждённые переключатели jobs",
                read("configmap", name("configmap")));
    }

    private void stageSnapshot(String name, JsonNode value) {
        if (originalSaved.add("stage/" + name)) artifacts.save(name, value);
    }
    private void write(String kind, String name, JsonNode current, String pointer, JsonNode value) {
        if (closed) throw new IllegalStateException("Mutation session closed");
        snapshot(kind, name, current);
        JsonNode before = current.at(pointer).deepCopy();
        String uid = current.at("/metadata/uid").asText();
        if (uid.isBlank() || !settings.namespace.equals(current.at("/metadata/namespace").asText()))
            throw new IllegalStateException("Resource identity is not in the approved namespace");
        // Must be registered even if oc times out after the API applied the patch.
        fieldMutationAttempted = true;
        undo.add(new Undo(kind, name, uid, pointer, before, value.deepCopy()));
        journalChange("WRITE_INTENT", kind, name, uid, pointer, before, value);
        patch(kind, name, current, pointer, value);
        JsonNode actual = read(kind, name);
        assertEquals(uid, actual.at("/metadata/uid").asText(), "Resource was replaced");
        assertEquals(value, actual.at(pointer), "API must persist the exact requested field");
        journalChange("WRITE_CONFIRMED", kind, name, uid, pointer, before, value);
        if ("configmap".equals(kind)) {
            configWritesConfirmed++;
            logBooleanChange("PATCH_GET_CONFIRMED", name, pointer, before, actual.at(pointer), actual);
        }
    }
    private void patch(String kind, String name, JsonNode current, String pointer, JsonNode value) {
        JsonNode uid = current.at("/metadata/uid");
        JsonNode revision = current.at("/metadata/resourceVersion");
        if (!uid.isTextual() || uid.asText().isBlank() || !revision.isTextual() || revision.asText().isBlank())
            throw new IllegalStateException("WORKLOAD_CAS_IDENTITY_MISSING: no patch dispatched");
        try {
            if ("configmap".equals(kind)) {
                if (!pointer.startsWith("/data/")) throw new IllegalArgumentException("Only ConfigMap data writes are supported");
                // One server-side JSON Patch tests UID+resourceVersion and changes exactly one field.
                // A preceding GET followed by resource(live).patch() is not an atomic CAS.
                var operations = WorkloadJson.JSON.createArrayNode();
                operations.addObject().put("op", "test").put("path", "/metadata/uid").set("value", uid);
                operations.addObject().put("op", "test").put("path", "/metadata/resourceVersion").set("value", revision);
                var operation = operations.addObject().put("op", value.isMissingNode() ? "remove"
                        : current.at(pointer).isMissingNode() ? "add" : "replace").put("path", pointer);
                if (!value.isMissingNode()) operation.set("value", value);
                api.configMaps().inNamespace(settings.namespace).withName(name).patch(
                        io.fabric8.kubernetes.client.dsl.base.PatchContext.of(
                                io.fabric8.kubernetes.client.dsl.base.PatchType.JSON), operations.toString());
            } else if ("deployment".equals(kind) && "/spec/replicas".equals(pointer)) {
                Deployment live = api.apps().deployments().inNamespace(settings.namespace).withName(name).get();
                assertLiveIdentity(live == null ? null : live.getMetadata().getUid(),
                        live == null ? null : live.getMetadata().getResourceVersion(), uid.asText(), revision.asText());
                live.getSpec().setReplicas(value.asInt());
                api.apps().deployments().inNamespace(settings.namespace).resource(live).replace();
            } else {
                throw new IllegalArgumentException("Unsupported ContainerService field mutation");
            }
        } catch (KubernetesClientException failure) {
            throw kubernetesFailure("WORKLOAD_PATCH", failure,
                    "; rollback will inspect actual state");
        }
    }

    private static void assertLiveIdentity(String actualUid, String actualRevision,
                                           String expectedUid, String expectedRevision) {
        if (!expectedUid.equals(actualUid) || !expectedRevision.equals(actualRevision))
            throw new IllegalStateException("WORKLOAD_CAS_CONFLICT: no write dispatched");
    }
    /** Compatibility entry: restart now means exactly one graceful pod deletion, never rollout restart. */
    public Set<String> restart() {
        permit("restart");
        permit("pod-delete");
        try {
            prepareDeletion();
            return recreate(false, deadline(), "pod-replacement");
        } catch (RuntimeException | AssertionError failure) {
            captureEvidence("replacement-failed-before-rollback");
            throw failure;
        }
    }

    private long deadline() {
        return System.nanoTime() + TimeUnit.SECONDS.toNanos(
                stand.integer("mutations.timeout.seconds", 600, 10, 600));
    }

    private void prepareDeletion() {
        if (baseline != null) return;
        JsonNode value = deployment();
        State state = state();
        assertEquals(value.at("/metadata/uid"), state.deployment().at("/metadata/uid"), "Deployment identity changed");
        if (state.deployment().at("/spec/replicas").asInt(1) != 1
                || !deploymentReady(state.deployment()) || state.pods().size() != 1
                || !podReady(state.pods().get(0)))
            throw new IllegalStateException("POD_DELETE_PREFLIGHT_UNSTABLE: require one Ready pod and a completed rollout; no mutation sent");
        JsonNode pod = state.pods().get(0);
        String rsUid = controller(pod, "ReplicaSet").path("uid").asText();
        long activeSets = state.replicaSets().stream().filter(rs -> rs.at("/spec/replicas").asInt() > 0).count();
        if (activeSets != 1 || state.replicaSets().stream().noneMatch(rs ->
                rsUid.equals(rs.at("/metadata/uid").asText()) && rs.at("/spec/replicas").asInt() == 1))
            throw new IllegalStateException("POD_DELETE_PREFLIGHT_ROLLOUT_IN_PROGRESS");
        String podName = pod.at("/metadata/name").asText();
        settings.requireNativeManagement();
        baseline = state.deployment().deepCopy();
        expectedReplicas = baseline.at("/spec/replicas").asInt(1);
        baselineReplicaSetUid = rsUid;
        snapshot("deployment", name("deployment"), baseline);
        snapshot("pod", podName, pod);
        evidence.capture("stable-baseline", state.deployment(), state.replicaSets(), state.pods(), Instant.now());
    }

    private Set<String> recreate(boolean recovery, long deadline, String phase) {
        Instant phaseStarted = Instant.now();
        long phaseNanos = System.nanoTime();
        String reason = recovery ? "apply-restored-configuration" : replacementReason;
        System.out.println("[workload-pod] phase=" + phase + "; reason=" + reason
                + "; event=START; pollSleepMillis=2000; evidenceIntervalMillis=15000"
                + "; remainingMillis=" + TimeUnit.NANOSECONDS.toMillis(Math.max(0, deadline - phaseNanos)));
        operationStarted = phaseStarted;
        State selected;
        while (true) {
            selected = state();
            assertBaseline(selected.deployment());
            List<JsonNode> active = selected.pods().stream().filter(pod -> !terminating(pod)).toList();
            if (active.size() == 1 && selected.pods().size() == 1) break;
            if (!recovery)
                throw new IllegalStateException("POD_DELETE_PREFLIGHT_CHANGED: selection is no longer exactly one active pod");
            evidence.record(phase + "-await-candidate", selected.deployment(),
                    selected.replicaSets(), selected.pods(), phaseStarted);
            pause(deadline);
        }
        JsonNode pod = selected.pods().get(0);
        assertEquals(baselineReplicaSetUid, controller(pod, "ReplicaSet").path("uid").asText(),
                "Do not delete a pod from an external rollout");
        if (!recovery && !podReady(pod))
            throw new IllegalStateException("POD_DELETE_PREFLIGHT_CHANGED: selected pod is no longer Ready");
        String podName = pod.at("/metadata/name").asText();
        String podUid = pod.at("/metadata/uid").asText();
        snapshot("pod", podName, pod);
        evidence.capture(phase + "-before-delete", selected.deployment(),
                selected.replicaSets(), selected.pods(), phaseStarted);
        // Re-read after evidence collection: it can take time; do not act on a stale selection.
        State fresh = state();
        assertBaseline(fresh.deployment());
        if (fresh.pods().size() != 1 || !podUid.equals(fresh.pods().get(0).at("/metadata/uid").asText())
                || terminating(fresh.pods().get(0)))
            throw new IllegalStateException("POD_DELETE_SELECTION_CHANGED: no delete dispatched");
        Pod exact = api.pods().inNamespace(settings.namespace).withName(podName).get();
        if (exact == null || !podUid.equals(exact.getMetadata().getUid()))
            throw new IllegalStateException("POD_DELETE_SELECTION_CHANGED: no delete dispatched");
        // Grace period is deliberately omitted: Kubernetes uses the pod's normal termination policy.
        podMutationAttempted = true;
        deletedPodUids.add(podUid);
        podDeleteDispatches++;
        System.out.println("[workload-pod] phase=" + phase + "; deleteDispatch=true; pod="
                + podName + "; uid=" + podUid + "; reason=" + reason
                + "; deleteDispatches=" + podDeleteDispatches);
        try {
            artifacts.event("POD_DELETE_INTENT", WorkloadJson.JSON.valueToTree(Map.of("namespace", settings.namespace,
                    "pod", podName, "uid", podUid, "phase", phase)));
            api.pods().inNamespace(settings.namespace).withName(podName).delete();
            artifacts.event("POD_DELETE_DISPATCHED", WorkloadJson.JSON.valueToTree(Map.of("pod", podName, "uid", podUid)));
        } catch (KubernetesClientException failure) {
            throw kubernetesFailure("POD_DELETE", failure,
                    "; recovery will inspect actual state, not replay delete");
        }
        attachment(phase + " - delete dispatch", "pod=" + podName + "\nuid=" + podUid
                + "\nstatus=DISPATCHED_VIA_FABRIC8"
                + "\nNo force, grace-period override, Deployment patch or automatic command replay.");
        State ready = awaitReplacement(deadline, phase, phaseStarted);
        Set<String> next = new HashSet<>();
        ready.pods().forEach(item -> next.add(item.at("/metadata/uid").asText()));
        assertTrue(Collections.disjoint(deletedPodUids, next), "Ready pod must have a new UID");
        evidence.capture(phase + "-ready", ready.deployment(), ready.replicaSets(), ready.pods(), phaseStarted);
        attachment(phase + " - replacement identity",
                WorkloadJson.JSON.valueToTree(Map.of("deletedPodUid", podUid, "readyPodUids", next,
                        "deploymentTemplateUnchanged", true)).toPrettyString());
        restoredConfigNeedsReplacement = false;
        lastReadyPodUids = Set.copyOf(next);
        podReplacementsConfirmed++;
        System.out.println("[workload-pod] phase=" + phase + "; replacementReady=true; oldUid="
                + podUid + "; newUids=" + next + "; replacementsConfirmed=" + podReplacementsConfirmed
                + "; elapsedMillis=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - phaseNanos));
        return next;
    }

    private State awaitReplacement(long deadline, String phase, Instant started) {
        return awaitReady(deadline, phase, started, true);
    }

    private State awaitReady(long deadline, String phase, Instant started, boolean requireReplacement) {
        long quotaSince = 0;
        long nextEvidence = 0;
        boolean quota = false;
        long waitStarted = System.nanoTime();
        int polls = 0;
        while (true) {
            polls++;
            State value = state();
            assertBaseline(value.deployment());
            boolean previousPresent = value.pods().stream()
                    .anyMatch(pod -> deletedPodUids.contains(pod.at("/metadata/uid").asText()));
            if ((!requireReplacement || !previousPresent) && value.pods().size() == expectedReplicas
                    && value.pods().stream().allMatch(KubernetesWorkloadControl::podReady)
                    && deploymentReady(value.deployment())) return value;
            long now = System.nanoTime();
            if (now >= nextEvidence) {
                System.out.println("[workload-pod-wait] phase=" + phase + "; poll=" + polls
                        + "; pollSleepMillis=2000; elapsedMillis=" + TimeUnit.NANOSECONDS.toMillis(now - waitStarted)
                        + "; remainingMillis=" + TimeUnit.NANOSECONDS.toMillis(Math.max(0, deadline - now))
                        + "; oldUidStillPresent=" + previousPresent + "; observedPods=" + value.pods().size()
                        + "; readyPods=" + value.pods().stream().filter(KubernetesWorkloadControl::podReady).count()
                        + "; stage=" + (previousPresent ? "WAIT_OLD_UID_REMOVAL"
                        : value.pods().isEmpty() ? "WAIT_NEW_POD" : "WAIT_CONTAINERS_AND_DEPLOYMENT_READY"));
                quota = evidence.record(phase, value.deployment(), value.replicaSets(), value.pods(), started);
                nextEvidence = now + TimeUnit.SECONDS.toNanos(15);
            }
            // Quota rejection during graceful termination can be transient. Never abort on historical events.
            if (quota && value.pods().isEmpty()) {
                if (quotaSince == 0) quotaSince = now;
                int seconds = stand.integer("mutations.quota.fail-fast.seconds", 60, 15, 300);
                if (now - quotaSince >= TimeUnit.SECONDS.toNanos(seconds))
                    throw new AssertionError("POD_CREATE_QUOTA_BLOCKED: fresh FailedCreate/exceeded quota and no owned pod for "
                            + seconds + " seconds; inspect namespace quota and Allure events");
            } else quotaSince = 0;
            pause(deadline);
        }
    }

    private static boolean retryableReadFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof java.io.InterruptedIOException) return true;
            String message = Objects.toString(current.getMessage(), "");
            if (message.contains("WORKLOAD_READ_IO_TIMEOUT") || message.contains("WORKLOAD_READ_IO_FAILURE"))
                return true;
        }
        return false;
    }

    private static void pause(long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0)
            throw new AssertionError("WORKLOAD_READINESS_TIMEOUT: new UID/Ready or Deployment stability not reached; see workload evidence");
        try { TimeUnit.NANOSECONDS.sleep(Math.min(remaining, TimeUnit.SECONDS.toNanos(2))); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted awaiting workload; restoring owned changes");
        }
    }

    private void assertBaseline(JsonNode current) {
        if (baseline == null) throw new IllegalStateException("Stable pre-image required");
        assertEquals(baseline.at("/metadata/uid"), current.at("/metadata/uid"), "Deployment replaced by another actor");
        for (String pointer : List.of("/spec/template", "/spec/selector", "/spec/paused"))
            assertEquals(baseline.at(pointer), current.at(pointer), "WORKLOAD_EXTERNAL_CHANGE at " + pointer);
        assertEquals(expectedReplicas, current.at("/spec/replicas").asInt(1),
                "WORKLOAD_EXTERNAL_CHANGE at /spec/replicas");
    }

    private State state() {
        JsonNode dep = read("deployment", name("deployment"));
        List<JsonNode> sets = new ArrayList<>();
        for (JsonNode rs : read("replicasets", null).path("items"))
            if (dep.at("/metadata/uid").asText().equals(controller(rs, "Deployment").path("uid").asText()))
                sets.add(rs);
        Set<String> setUids = new HashSet<>();
        sets.forEach(rs -> setUids.add(rs.at("/metadata/uid").asText()));
        List<JsonNode> pods = new ArrayList<>();
        JsonNode labels = dep.at("/spec/selector/matchLabels");
        if (!labels.isObject() || labels.isEmpty()) throw new IllegalStateException("Deployment matchLabels required");
        for (JsonNode pod : read("pods", null).path("items")) {
            boolean matches = true;
            var entries = labels.fields();
            while (entries.hasNext()) {
                var entry = entries.next();
                if (!entry.getValue().equals(pod.at("/metadata/labels/" + escape(entry.getKey())))) matches = false;
            }
            if (!matches) continue;
            if (!settings.namespace.equals(pod.at("/metadata/namespace").asText())
                    || !setUids.contains(controller(pod, "ReplicaSet").path("uid").asText()))
                throw new IllegalStateException("POD_OWNER_CONFLICT: matching labels are not proof of Deployment ownership");
            pods.add(pod);
        }
        return new State(dep, sets, pods);
    }

    private static JsonNode controller(JsonNode resource, String kind) {
        for (JsonNode ref : resource.at("/metadata/ownerReferences"))
            if (ref.path("controller").asBoolean(false) && kind.equals(ref.path("kind").asText())) return ref;
        return WorkloadJson.JSON.createObjectNode();
    }

    private static boolean terminating(JsonNode pod) {
        JsonNode timestamp = pod.at("/metadata/deletionTimestamp");
        return !timestamp.isMissingNode() && !timestamp.isNull();
    }

    private static boolean podReady(JsonNode pod) {
        if (terminating(pod) || !"Running".equals(pod.at("/status/phase").asText())) return false;
        boolean ready = false;
        for (JsonNode condition : pod.at("/status/conditions"))
            if ("Ready".equals(condition.path("type").asText()) && "True".equals(condition.path("status").asText())) ready = true;
        if (!ready) return false;
        for (JsonNode container : pod.at("/spec/containers")) {
            boolean found = false;
            for (JsonNode status : pod.at("/status/containerStatuses"))
                if (container.path("name").equals(status.path("name")) && status.path("ready").asBoolean(false)) found = true;
            if (!found) return false;
        }
        return true;
    }

    private static boolean deploymentReady(JsonNode value) {
        int replicas = value.at("/spec/replicas").asInt(1);
        return value.at("/status/observedGeneration").asLong(-1) >= value.at("/metadata/generation").asLong()
                && value.at("/status/updatedReplicas").asInt() == replicas
                && value.at("/status/readyReplicas").asInt() == replicas
                && value.at("/status/availableReplicas").asInt() == replicas
                && value.at("/status/replicas").asInt() == replicas;
    }

    public void captureEvidence(String phase) {
        try {
            State value = state();
            evidence.capture(phase, value.deployment(), value.replicaSets(), value.pods(), operationStarted);
        } catch (RuntimeException failure) { evidence.unavailable(phase, failure); }
    }

    /** Diagnostics only; does not alter readiness, ownership, mutations or rollback. */
    public void beginScenarioEvidence(Instant started) { evidence.beginScenario(started); }
    public void startScenarioOperation(Instant started) { evidence.startOperation(started); }
    public void finishScenarioOperation(Instant finished) { evidence.finishOperation(finished); }

    /** Actual values of the explicitly managed non-secret keys, read through the shared Fabric8 session. */
    public void captureScenarioConfigMaps(String phase, List<ConfigMapState> states) {
        for (ConfigMapState state : states) {
            try {
                state.data().keySet().forEach(this::approvedDataKey);
                JsonNode actual = read("configmap", resourceName(state.name(), "configmap"));
                attachment("ConfigMap — " + phase + " — " + state.name(), "text/plain",
                        WorkloadEvidence.configMapText(actual, state.data().keySet()), ".txt");
            } catch (RuntimeException failure) {
                attachment("ConfigMap — " + phase + " — " + state.name(), "text/plain",
                        "status=UNAVAILABLE\nfailureType=" + failure.getClass().getSimpleName(), ".txt");
            }
        }
    }

    public void finishScenarioEvidence(String phase, boolean failed) {
        try {
            State value = state();
            evidence.finishScenario(phase, value.deployment(), value.replicaSets(), value.pods(), operationStarted, failed);
        } catch (RuntimeException failure) { evidence.unavailable(phase, failure); }
        finally { evidence.endScenario(); }
    }

    /**
     * Mandatory regression gate. It exercises only read operations and never creates an undo entry.
     * The summary intentionally excludes ConfigMap values, pod environment and credentials.
     */
    public void requireReadOnlyRegressionBaseline() {
        settings.requireNativeManagement();
        JsonNode dep = deployment();
        State current = state();
        int desired = dep.at("/spec/replicas").asInt(1);
        long readyPods = current.pods().stream().filter(KubernetesWorkloadControl::podReady).count();
        if (!deploymentReady(dep) || readyPods < desired)
            throw new IllegalStateException("REGRESSION_PREFLIGHT_NO_READY_WORKLOAD: deployment="
                    + name("deployment") + ", desired=" + desired + ", readyPods=" + readyPods);

        String configMapName = name("configmap");
        JsonNode configMap = read("configmap", configMapName);
        assertConfigMapReference(dep, configMapName);
        String configMapKey = approvedKey();
        if (!configMap.at("/data/" + escape(configMapKey)).isTextual())
            throw new IllegalStateException("REGRESSION_PREFLIGHT_CONFIGMAP_KEY_MISSING: " + configMapKey);

        JsonNode hpas = read("hpa", null);
        JsonNode quotas = read("resourcequota", null);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("environment", settings.environment);
        summary.put("namespace", settings.namespace);
        summary.put("deployment", name("deployment"));
        summary.put("desiredReplicas", desired);
        summary.put("readyPods", readyPods);
        summary.put("configMap", configMapName);
        summary.put("configMapKeyPresent", true);
        summary.put("horizontalPodAutoscalers", hpas.path("items").size());
        summary.put("resourceQuotas", quotas.path("items").size());
        summary.put("transport", settings.transport);
        summary.put("mutationsDispatched", false);
        summary.put("credentialsAttached", false);
        attachment("Scheduler regression Kubernetes read-only baseline", "application/json",
                WorkloadJson.JSON.valueToTree(summary).toPrettyString(), ".json");
        evidence.capture("regression-preflight", dep, current.replicaSets(), current.pods(), operationStarted);
    }

    public void inspectConfigMap() {
        settings.clientConfiguration();
        if (artifacts == null) artifacts = new WorkloadArtifacts(settings.environment, () -> allureAttachmentsEnabled);
        JsonNode dep = deployment();
        String cmName = name("configmap");
        JsonNode cm = read("configmap", cmName);
        assertConfigMapReference(dep, cmName);
        snapshot("configmap", cmName, cm);
        String key = approvedKey();
        assertTrue(cm.at("/data/" + escape(key)).isTextual(), "Configured data key must exist");
        attachment("ConfigMap read-only diagnostic",
                "name=" + cmName + "\nuid=" + cm.at("/metadata/uid").asText()
                + "\nresourceVersion=" + cm.at("/metadata/resourceVersion").asText()
                + "\nkey=" + key + "\nNo mutation and no pod deletion performed.");
    }

    private String approvedKey() {
        String key = stand.required(prefix + "configmap.key");
        if (!key.matches("[A-Za-z0-9._-]+")
                || key.toLowerCase(Locale.ROOT).matches(".*(password|secret|token|credential|private|keystore).*"))
            throw new IllegalArgumentException("An explicitly approved non-secret ConfigMap data key is required");
        return key;
    }

    private void assertConfigMapReference(JsonNode dep, String cmName) {
        if (!ConfigMapReferences.names(dep).contains(cmName))
            throw new IllegalStateException("ConfigMap must be consumed by the exact service Deployment");
    }

    public void changeConfigMap() {
        // Confirm BOTH mutation permissions and delete RBAC before changing configuration.
        permit("configmap");
        permit("restart");
        permit("pod-delete");
        // Read the approved probe before pod/quota preparation; a no-op is not a successful mutation test.
        String cmName = name("configmap");
        JsonNode cm = read("configmap", cmName);
        if (cm.path("immutable").asBoolean(false)) throw new IllegalStateException("Immutable ConfigMap is not changed");
        String key = approvedKey();
        String pointer = "/data/" + escape(key);
        if (!cm.at(pointer).isTextual()) throw new IllegalStateException("Existing textual ConfigMap data key required");
        String probe = stand.required(prefix + "configmap.probe-value");
        int probeBytes = probe.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (probeBytes > 65536)
            throw new IllegalStateException("CONFIGMAP_PROBE_TOO_LARGE: approved UTF-8 probe exceeds 64 KiB; no mutation dispatched");
        snapshot("configmap", cmName, cm);
        stageSnapshot("configmap-before.json", cm);
        boolean changesValue = !probe.equals(cm.at(pointer).asText());
        attachment("ConfigMap probe precondition", "ConfigMap=" + cmName + "\nkey=" + key
                + "\nprobeBytes=" + probeBytes + "\nchangesValue=" + changesValue
                + "\nphase=PRECONDITION_ONLY; no PATCH or pod deletion dispatched");
        assumeTrue(changesValue, "CONFIGMAP_PROBE_NO_CHANGE: approved probe equals the live value; "
                + "change/restart/rollback NOT tested. Select a distinct approved value; job flags are never inverted automatically.");
        prepareDeletion();
        assertConfigMapReference(baseline, cmName);
        try {
            write("configmap", cmName, cm, pointer, WorkloadJson.JSON.getNodeFactory().textNode(probe));
            stageSnapshot("configmap-patched.json", read("configmap", cmName));
            attachment("ConfigMap GET assertion after patch",
                    "ConfigMap=" + cmName + "\nkey=" + key + "\nexpected=" + probe
                    + "\nSeparate GET equals requested value. Full pre-image remains private.");
        } catch (RuntimeException | AssertionError failure) {
            captureEvidence("configmap-change-failed-before-rollback");
            throw failure;
        }
    }

    /**
     * Apply a ticket-owned state to an explicitly selected ConfigMap used by this workload.
     * The target ConfigMap is verified against the live Deployment before any patch is sent.
     */
    public void applyReferencedConfigMapData(String configMapName,
                                             Map<String, String> desired,
                                             String phase) {
        permit("configmap");
        permit("restart");
        permit("pod-delete");
        desired = new ConfigMapState(configMapName, desired).data();
        String cmName = resourceName(configMapName, "configmap");
        String safePhase = phase == null || phase.isBlank() ? "configmap-state" : phase;
        if (!safePhase.matches("[a-z0-9-]{1,80}"))
            throw new IllegalArgumentException("Safe phase code required");
        prepareDeletion();
        assertConfigMapReference(baseline, cmName);
        JsonNode original = read("configmap", cmName);
        if (original.path("immutable").asBoolean(false) || !original.path("data").isObject())
            throw new IllegalStateException("Mutable ConfigMap with data object required: " + cmName);
        snapshot("configmap", cmName, original);
        stageSnapshot(safePhase + "-before.json", original);
        int writesBefore = configWritesConfirmed;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("configMap", cmName);
        summary.put("deployment", name("deployment"));
        summary.put("verifiedReferencedByDeployment", true);
        Map<String, Object> keys = new TreeMap<>();
        for (Map.Entry<String, String> entry : new TreeMap<>(desired).entrySet()) {
            String key = approvedDataKey(entry.getKey());
            String value = Objects.requireNonNull(entry.getValue(), "ConfigMap value must not be null");
            int bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (bytes > 262144)
                throw new IllegalStateException("CONFIGMAP_VALUE_TOO_LARGE: " + key);
            String pointer = "/data/" + escape(key);
            JsonNode fresh = read("configmap", cmName);
            assertEquals(original.at("/metadata/uid"), fresh.at("/metadata/uid"), "ConfigMap replaced: " + cmName);
            assertEquals(original.at(pointer), fresh.at(pointer), "ConfigMap key changed externally: " + key);
            if (ConfigMapState.equivalent(key, value, fresh.at(pointer).asText(null))) {
                keys.put(key, Map.of("changed", false, "bytes", bytes));
                continue;
            }
            try {
                write("configmap", cmName, fresh, pointer,
                        WorkloadJson.JSON.getNodeFactory().textNode(value));
                keys.put(key, Map.of("changed", true, "bytes", bytes));
            } catch (RuntimeException | AssertionError failure) {
                captureEvidence(safePhase + "-failed-before-rollback");
                throw failure;
            }
        }
        JsonNode observed = read("configmap", cmName);
        desired.forEach((key, value) -> assertTrue(ConfigMapState.equivalent(key, value,
                observed.at("/data/" + escape(approvedDataKey(key))).asText(null)),
                "Separate ConfigMap GET after ticket state apply: " + key));
        int changed = configWritesConfirmed - writesBefore;
        summary.put("keys", keys);
        summary.put("changedKeys", changed);
        stageSnapshot(safePhase + "-after.json", observed);
        evidence.capture(safePhase, baseline, null, null, Instant.now());
        if (!evidence.scenarioActive()) attachment("Managed ticket ConfigMap state",
                WorkloadJson.JSON.valueToTree(summary).toPrettyString());
    }

    public void requireStableReadyWorkload(String phase) {
        if (closed) throw new IllegalStateException("Mutation session closed");
        settings.requireNativeManagement();
        String safePhase = phase == null || phase.isBlank() ? "workload-ready" : phase;
        if (!safePhase.matches("[a-z0-9-]{1,80}"))
            throw new IllegalArgumentException("Safe phase code required");
        State current = state();
        int desired = current.deployment().at("/spec/replicas").asInt(1);
        long readyPods = current.pods().stream().filter(KubernetesWorkloadControl::podReady).count();
        if (!deploymentReady(current.deployment()) || desired < 1 || readyPods != desired)
            throw new IllegalStateException("WORKLOAD_READY_PREFLIGHT_FAILED");
        evidence.capture(safePhase, current.deployment(), current.replicaSets(), current.pods(), Instant.now());
        if (!evidence.scenarioActive()) attachment("Managed ticket workload readiness", WorkloadJson.JSON.valueToTree(Map.of(
                "deployment", name("deployment"),
                "service", settings.service,
                "expectedReplicas", desired,
                "readyPods", readyPods,
                "mutationDispatched", false
        )).toPrettyString());
    }

    private String approvedDataKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._-]+")
                || key.toLowerCase(Locale.ROOT).matches(".*(password|secret|token|credential|private|keystore).*"))
            throw new IllegalArgumentException("An explicitly approved non-secret ConfigMap data key is required");
        return key;
    }
    /** Generic managed-fixture primitive: approved boolean switches only, with private pre-images and CAS undo. */
    public void pauseConfigMapFlags(Set<String> keys) {
        long phaseStartedNanos = System.nanoTime();
        permit("configmap");
        permit("restart");
        permit("pod-delete");
        if (!stand.flag(prefix + "fixtures.prepare.enabled"))
            throw new IllegalStateException("Managed fixture preparation is not explicitly enabled for this workload");
        if (keys.isEmpty() || keys.size() > 16 || keys.stream().anyMatch(k -> !k.matches("[A-Z0-9_]+_ENABLED")))
            throw new IllegalArgumentException("A bounded explicit set of non-secret boolean switches is required");
        prepareDeletion();
        String cmName = name("configmap");
        JsonNode original = read("configmap", cmName);
        if (original.path("immutable").asBoolean(false) || !original.path("data").isObject())
            throw new IllegalStateException("ConfigMap must be mutable with existing data object");
        assertConfigMapReference(baseline, cmName);
        // Validate the complete allowlist and save the pre-image BEFORE the first mutation.
        for (String key : keys) {
            JsonNode value = original.at("/data/" + escape(key));
            if (!value.isTextual() || !Set.of("true", "false").contains(value.asText()))
                throw new IllegalStateException("ConfigMap boolean data key required: " + key);
        }
        snapshot("configmap", cmName, original);
        rememberFixtureFlags(original, keys);
        int writesBefore = configWritesConfirmed;
        for (String key : keys) {
            String pointer = "/data/" + escape(key);
            JsonNode fresh = read("configmap", cmName);
            assertEquals(original.at("/metadata/uid"), fresh.at("/metadata/uid"), "ConfigMap replaced");
            assertEquals(original.at(pointer), fresh.at(pointer), "ConfigMap switch changed externally: " + key);
            String probe = "false";
            if (probe.equals(fresh.at(pointer).asText())) continue;
            try {
                write("configmap", cmName, fresh, pointer, WorkloadJson.JSON.getNodeFactory().textNode(probe));
            } catch (RuntimeException | AssertionError failure) {
                captureEvidence("pause-configmap-flags-failed-before-rollback");
                throw failure;
            }
        }
        assertConfigMapFlagsDisabled(keys);
        int changed = configWritesConfirmed - writesBefore;
        System.out.println("[workload-configmap] phase=PAUSE; result="
                + (changed == 0 ? "NO_CHANGE" : "PATCHED_AND_GET_CONFIRMED") + "; changedKeys=" + changed
                + "; actualWritePerformed=" + (changed > 0)
                + "; elapsedMillis=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - phaseStartedNanos));
        evidence.capture("pause-configmap-flags", original, null, null, Instant.now());
        attachment("pauseConfigMapFlags", WorkloadJson.JSON.valueToTree(
                        Map.of("configmap", cmName, "keys", keys, "probe", "false",
                                "originalFlags", originalFixtureFlags, "changedKeys", changed,
                                "result", changed == 0 ? "NO_CHANGE" : "PATCHED_AND_GET_CONFIRMED"))
                .toPrettyString());
    }

    /** Managed-fixture assertion that all approved switches are disabled after the replacement pod is Ready. */
    public void assertConfigMapFlagsDisabled(Set<String> keys) {
        JsonNode cm = read("configmap", name("configmap"));
        if (cm.path("immutable").asBoolean(false) || !cm.path("data").isObject())
            throw new IllegalStateException("ConfigMap must be mutable with existing data object");
        for (String key : keys) {
            JsonNode node = cm.at("/data/" + escape(key));
            if (node.isMissingNode()) throw new IllegalStateException("ConfigMap data key not present: " + key);
            assertEquals("false", node.asText(), "Approved scheduler switch must remain disabled: " + key);
        }
    }

    /** Change only the pre-approved fixture switches, with GET assertions and the usual CAS undo. */
    public void applyFixtureJobProfile(Map<String, Boolean> desired) {
        permit("configmap");
        permit("restart");
        permit("pod-delete");
        if (!stand.flag(prefix + "fixtures.prepare.enabled")
                || !stand.flag(prefix + "regression.jobs.enabled"))
            throw new IllegalStateException("Real scheduler job execution requires explicit stand permission");
        Set<String> approved = new TreeSet<>(Arrays.asList(
                stand.required(prefix + "fixtures.job-keys").split(",")));
        if (desired.isEmpty() || desired.size() > 16 || !desired.keySet().equals(approved)
                || desired.keySet().stream().anyMatch(k -> !k.matches("[A-Z0-9_]+_ENABLED"))
                || desired.values().stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Exact approved boolean-switch profile required");
        prepareDeletion();
        String cmName = name("configmap");
        JsonNode original = read("configmap", cmName);
        assertConfigMapReference(baseline, cmName);
        if (original.path("immutable").asBoolean(false) || !original.path("data").isObject())
            throw new IllegalStateException("Mutable existing ConfigMap required");
        for (String key : approved)
            if (!"false".equals(original.at("/data/" + escape(key)).asText()))
                throw new IllegalStateException("All fixture job switches must be paused before enabling a profile");
        snapshot("configmap", cmName, original);
        rememberFixtureFlags(original, approved);
        for (String key : new TreeSet<>(desired.keySet())) {
            String value = desired.get(key).toString();
            JsonNode fresh = read("configmap", cmName);
            assertEquals(original.at("/metadata/uid"), fresh.at("/metadata/uid"), "ConfigMap replaced");
            assertEquals(original.at("/data/" + escape(key)), fresh.at("/data/" + escape(key)),
                    "Job switch changed externally before writing: " + key);
            if (!value.equals(fresh.at("/data/" + escape(key)).asText()))
                write("configmap", cmName, fresh, "/data/" + escape(key),
                        WorkloadJson.JSON.getNodeFactory().textNode(value));
        }
        JsonNode observed = read("configmap", cmName);
        assertEquals(original.at("/metadata/uid"), observed.at("/metadata/uid"));
        desired.forEach((key, value) -> assertEquals(value.toString(),
                observed.at("/data/" + escape(key)).asText(), "Separate profile GET: " + key));
        attachment("Real scheduler job profile", WorkloadJson.JSON.valueToTree(desired).toPrettyString());
    }

    public void scale() {
        scale(stand.integer(prefix + "scale.replicas", 2, 1, 10));
    }

    public void scale(int desired) {
        if (desired < 1 || desired > 10) throw new IllegalArgumentException("Replicas must be in 1..10");
        assumeTrue(stand.flag("dedicated"), "Scaling is permitted only on an explicitly dedicated stand");
        permit("scale");
        permit("restart");
        assumeTrue(stand.flag(prefix + "scale.enabled"),
                "SKIPPED: enable the exact workload scale profile explicitly on an approved dedicated stand");
        prepareDeletion();
        String depName = name("deployment");
        if (desired == expectedReplicas) throw new IllegalArgumentException("Scale target must differ from the baseline");
        for (JsonNode hpa : read("hpa", null).path("items")) {
            if ("Deployment".equals(hpa.at("/spec/scaleTargetRef/kind").asText())
                    && depName.equals(hpa.at("/spec/scaleTargetRef/name").asText()))
                throw new IllegalStateException("WORKLOAD_HPA_PRESENT: refusing competing replica control");
        }
        JsonNode current = read("deployment", depName);
        assertBaseline(current);
        assertScaleMemoryQuota(current, desired);
        write("deployment", depName, current, "/spec/replicas", WorkloadJson.JSON.getNodeFactory().numberNode(desired));
        expectedReplicas = desired;
        evidence.capture("scale-up", current, null, null, Instant.now());
        awaitReady(deadline(), "scale", Instant.now(), false);
        JsonNode scaled = read("deployment", depName);
        assertEquals(desired, scaled.at("/spec/replicas").asInt(), "Deployment replicas must match target");
        attachment("scale", WorkloadJson.JSON.valueToTree(
                        Map.of("deployment", depName, "desiredReplicas", desired, "ready", true))
                .toPrettyString());
    }

    /** Marks owned changes so that close() refuses auto-restore and instructs manual recovery. */
    public void retainForManualRecovery(String message) {
        manualRecoveryMessage = message;
        try {
            WorkloadArtifacts artifactsLocal = artifacts == null ? new WorkloadArtifacts(settings.environment, () -> allureAttachmentsEnabled) : artifacts;
            artifactsLocal.save("recovery-status.txt",
                    WorkloadJson.JSON.getNodeFactory().textNode(message
                            + "\nRestore original ConfigMap keys from saved pre-images, then recreate the scheduler pod."));
        } catch (RuntimeException ignored) { }
    }

    private String escape(String value) {
        if (!value.matches("[A-Za-z0-9._-]+"))
            throw new IllegalArgumentException("Expected a single Kubernetes JSON pointer segment");
        return value;
    }

    public void restoreConfigMapFlags() {
        permit("configmap");
        permit("restart");
        permit("pod-delete");
        if (!stand.flag(prefix + "fixtures.prepare.enabled"))
            throw new IllegalStateException("Managed fixture preparation is not explicitly enabled for this workload");
        if (closed) throw new IllegalStateException("Mutation session closed");
        String cmName = name("configmap");
        List<Undo> reversed = new ArrayList<>(undo);
        Collections.reverse(reversed);
        for (Undo change : reversed) {
            if ("configmap".equals(change.kind()) && cmName.equals(change.name())) {
                restoreOwned(change);
                undo.remove(change);
            }
        }
        evidence.capture("restore-configmap-flags", read("configmap", cmName), null, null, Instant.now());
        attachment("restoreConfigMapFlags", "ConfigMap=" + cmName
                + "\nOwned keys verified by GET; recreationRequired=" + restoredConfigNeedsReplacement);
    }

    /** Intermediate per-field snapshots must never masquerade as the final rollback state. */
    private void saveFinalRestoredConfigMap() {
        if (configRestoreGetsConfirmed == 0) return;
        if (undo.stream().anyMatch(change -> "configmap".equals(change.kind())))
            throw new IllegalStateException("CONFIGMAP_FINAL_SNAPSHOT_PENDING_UNDO");
        if (!stateLeases.isEmpty()) {
            for (String saved : new TreeSet<>(originalSaved)) if (saved.startsWith("configmap/")) {
                String cmName = saved.substring("configmap/".length());
                JsonNode current = read("configmap", cmName);
                artifacts.save("configmap-restored-" + cmName + ".json", current);
                artifacts.save("configmap-restored-" + cmName + ".safe.json", safeConfigMap(current));
            }
            return;
        }
        JsonNode current = read("configmap", name("configmap"));
        if (!originalFixtureFlags.isEmpty()) {
            assertEquals(fixtureConfigMapUid, current.at("/metadata/uid").asText(), "Final snapshot ConfigMap identity");
            originalFixtureFlags.forEach((key, value) -> assertEquals(value,
                    current.at("/data/" + escape(key)).asText(), "Final snapshot must match original: " + key));
        }
        artifacts.save("configmap-restored.json", current);
        var safe = WorkloadJson.JSON.createObjectNode();
        safe.put("scope", "RUN_ROOT_RESTORATION");
        safe.put("capturedAfterAllScenarios", true);
        safe.put("originalFlagsVerified", !originalFixtureFlags.isEmpty());
        if (configMapOwnerTestFullName != null) safe.put("ownerTestFullName", configMapOwnerTestFullName);
        safe.set("configMap", safeConfigMap(current));
        artifacts.save("configmap-restored.safe.json", safe);
    }

    private void rememberFixtureFlags(JsonNode configMap, Set<String> keys) {
        if (!originalFixtureFlags.isEmpty())
            throw new IllegalStateException("Fixture flags already captured in this mutation session");
        fixtureConfigMapUid = configMap.at("/metadata/uid").asText();
        for (String key : keys) {
            String value = configMap.at("/data/" + escape(key)).asText();
            if (!key.startsWith("SCHEDULER_SERVICE_") || !key.endsWith("_ENABLED")
                    || !Set.of("true", "false").contains(value))
                throw new IllegalStateException("Only approved scheduler boolean flags may enter diagnostic output");
            originalFixtureFlags.put(key, value);
        }
    }

    private void verifyOriginalFixtureFlags() {
        if (originalFixtureFlags.isEmpty()) return;
        JsonNode current = read("configmap", name("configmap"));
        assertEquals(fixtureConfigMapUid, current.at("/metadata/uid").asText(), "ConfigMap replaced before final GET");
        originalFixtureFlags.forEach((key, value) -> assertEquals(value,
                current.at("/data/" + escape(key)).asText(), "Final ConfigMap GET must match original: " + key));
        attachment("Original scheduler flags confirmed by final GET",
                WorkloadJson.JSON.valueToTree(Map.of("uid", fixtureConfigMapUid,
                        "flags", originalFixtureFlags, "configWritesConfirmed", configWritesConfirmed)).toPrettyString());
    }

    private void logBooleanChange(String operation, String name, String pointer,
                                  JsonNode before, JsonNode after, JsonNode resource) {
        String key = pointer.startsWith("/data/") ? pointer.substring(6) : "";
        boolean safe = key.startsWith("SCHEDULER_SERVICE_") && key.endsWith("_ENABLED")
                && Set.of("true", "false").contains(before.asText())
                && Set.of("true", "false").contains(after.asText());
        System.out.println("[workload-configmap] operation=" + operation + "; name=" + name
                + "; key=" + key + "; uid=" + resource.at("/metadata/uid").asText()
                + "; resourceVersion=" + resource.at("/metadata/resourceVersion").asText()
                + "; before=" + (safe ? before.asText() : "<omitted>")
                + "; after=" + (safe ? after.asText() : "<omitted>"));
    }

    /** Restore only our field, without recording a second undo or overwriting a concurrent edit. */
    private void restoreOwned(Undo change) {
        for (WorkloadRunLease lease : stateLeases.values()) if (lease.acquired()) lease.requireHeld();
        JsonNode current = read(change.kind(), change.name());
        if (!change.uid().equals(current.at("/metadata/uid").asText())
                || !settings.namespace.equals(current.at("/metadata/namespace").asText()))
            throw new IllegalStateException("WORKLOAD_RESTORE_IDENTITY_CONFLICT: manual recovery required");
        JsonNode actual = current.at(change.pointer());
        if (!actual.equals(change.before())) {
            if (!actual.equals(change.written()))
                throw new IllegalStateException("WORKLOAD_RESTORE_FIELD_CONFLICT: external value preserved; manual recovery required");
            journalChange("RESTORE_INTENT", change.kind(), change.name(), change.uid(), change.pointer(), change.written(), change.before());
            patch(change.kind(), change.name(), current, change.pointer(), change.before());
        }
        JsonNode restoredResource = read(change.kind(), change.name());
        assertEquals(change.uid(), restoredResource.at("/metadata/uid").asText(), "Restore resource UID changed");
        assertEquals(change.before(), restoredResource.at(change.pointer()), "Restore GET must match the pre-image");
        journalChange("RESTORE_CONFIRMED", change.kind(), change.name(), change.uid(), change.pointer(), change.written(), change.before());
        if ("configmap".equals(change.kind())) {
            configRestoreGetsConfirmed++;
            logBooleanChange("RESTORE_GET_CONFIRMED", change.name(), change.pointer(),
                    actual, restoredResource.at(change.pointer()), restoredResource);
            stageSnapshot("configmap-restored-field-" + escape(change.pointer().substring(6)) + ".json", restoredResource);
        }
        if ("deployment".equals(change.kind()) && "/spec/replicas".equals(change.pointer()))
            expectedReplicas = change.before().asInt(1);
        if ("configmap".equals(change.kind()) && !change.before().equals(change.written()))
            restoredConfigNeedsReplacement = true;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        restored = false;
        Throwable primary = null;
        System.out.println("[workload-control] Closing owned Fabric8 workload session; thread="
                + Thread.currentThread().getName() + "; pendingUndo=" + undo.size()
                + "; manualRecovery=" + (manualRecoveryMessage != null));
        try {
            if (manualRecoveryMessage != null) return;
            if (regressionLease != null && regressionLease.acquired()) regressionLease.requireHeld();
            for (WorkloadRunLease lease : stateLeases.values()) if (lease.acquired()) lease.requireHeld();
            List<Undo> reversed = new ArrayList<>(undo);
            Collections.reverse(reversed);
            for (Undo change : reversed) {
                restoreOwned(change);
                undo.remove(change); // Retain unconfirmed undo entries on failure.
            }
            if (baseline != null) {
                if (restoredConfigNeedsReplacement && podMutationAttempted) {
                    // The probe may already be in a pod's environment. Apply restored config once.
                    recreate(true, deadline(), "rollback-recreate");
                    rollbackPodRecreated = true;
                } else {
                    // Pod-only and scale cleanup must not dispatch another delete.
                    State ready = awaitReady(deadline(), "rollback-ready", Instant.now(), false);
                    Set<String> current = new TreeSet<>();
                    ready.pods().forEach(pod -> current.add(pod.at("/metadata/uid").asText()));
                    lastReadyPodUids = Set.copyOf(current);
                }
            }
            verifyOriginalFixtureFlags();
            saveFinalRestoredConfigMap();
            configurationRestored = true;
            if (availabilityProbe != null && mutationAttempted()) {
                awaitAvailability();
                System.out.println("[workload-readiness] phase=restored; ready=true; beforeLeaseRelease=true");
            }
            if (rootProbe != null) {
                assertRegressionLease();
                if (rootProbe.ingress()) rootProbe.verify(null);
                else verifyRootThroughOwnedTunnel();
                rootHttpReady = true;
                artifacts.save("root-http-ready.json", WorkloadJson.JSON.valueToTree(Map.of(
                        "ready", true, "transport", rootProbe.ingress() ? "MTLS_INGRESS" : "FABRIC8_LOOPBACK",
                        "registryReadOnly", true, "verifiedBeforeLeaseRelease", true)));
            }
            if (regressionLease != null) regressionLease.close();
            for (WorkloadRunLease lease : stateLeases.values()) lease.close();
            stateLeases.clear();
            if (artifacts != null) artifacts.event("RUN_RESTORED_AND_RELEASED", WorkloadJson.JSON.createObjectNode().put("namespace", settings.namespace));
            restored = true;
            System.out.println("[workload-control] " + restorationSummary());
            closed = true;
        } catch (RuntimeException | Error failure) {
            primary = failure;
            if (retryableReadFailure(failure)) {
                System.err.println("[workload-control] Rollback GET/LIST failed after the bounded retry; "
                        + "remote evidence collection is skipped. Saved transport diagnostics and private pre-images remain authoritative.");
            } else {
                captureEvidence("rollback-unconfirmed");
            }
            retainForManualRecovery(configurationRestored
                    ? "Original configuration and pod were restored, but final HTTP/lease release was NOT confirmed. "
                            + "Do not replay mutations; inspect root HTTP evidence and the retained run lease."
                    : "Rollback was NOT confirmed. Do not resume fixture runs; "
                            + "inspect owned pre-images and unconfirmed field changes before manual recovery.");
            throw failure;
        } finally {
            closed = true;
            try {
                api.close();
                System.out.println("[workload-control] Owned Fabric8 workload client closed; rollbackConfirmed="
                        + restored);
            } catch (RuntimeException closeFailure) {
                if (primary != null) primary.addSuppressed(closeFailure);
                else throw closeFailure;
            }
        }
    }
    private void journalChange(String stage, String kind, String name, String uid, String pointer,
                               JsonNode before, JsonNode written) {
        var details = WorkloadJson.JSON.createObjectNode().put("namespace", settings.namespace)
                .put("context", settings.context()).put("kind", kind).put("name", name).put("uid", uid)
                .put("pointer", pointer).put("beforeMissing", before.isMissingNode()).put("writtenMissing", written.isMissingNode());
        details.set("before", before);
        details.set("written", written);
        var owners = details.putObject("owners");
        stateLeases.forEach((map, lease) -> owners.put(map, lease.owner()));
        if (regressionLease != null) owners.put(name("configmap"), regressionLease.owner());
        artifacts.event(stage, details);
    }
    /** IFT/LT without ingress: select the current owned Ready pod and resolve the actual Service targetPort. */
    private void verifyRootThroughOwnedTunnel() {
        State current = state();
        JsonNode selected = current.pods().stream().filter(KubernetesWorkloadControl::podReady)
                .findFirst().orElseThrow(() -> new IllegalStateException("ROOT_HTTP_NO_READY_POD"));
        var service = api.services().inNamespace(settings.namespace).withName(settings.service).get();
        if (service == null || service.getSpec() == null || service.getSpec().getSelector() == null
                || service.getSpec().getSelector().isEmpty())
            throw new IllegalStateException("ROOT_HTTP_SERVICE_SELECTOR");
        for (var label : service.getSpec().getSelector().entrySet())
            if (!label.getValue().equals(selected.at("/metadata/labels/" + escape(label.getKey())).asText()))
                throw new IllegalStateException("ROOT_HTTP_SERVICE_POD_MISMATCH");
        var port = service.getSpec().getPorts().stream().filter(p -> p.getPort() == settings.servicePort)
                .findFirst().orElseThrow(() -> new IllegalStateException("ROOT_HTTP_SERVICE_PORT"));
        if (port.getProtocol() != null && !"TCP".equals(port.getProtocol()))
            throw new IllegalStateException("ROOT_HTTP_TCP_REQUIRED");
        int target = settings.servicePort;
        if (port.getTargetPort() != null) {
            if (port.getTargetPort().getIntVal() != null) target = port.getTargetPort().getIntVal();
            else {
                Set<Integer> targets = new HashSet<>();
                for (JsonNode container : selected.at("/spec/containers"))
                    for (JsonNode p : container.path("ports"))
                        if (port.getTargetPort().getStrVal().equals(p.path("name").asText()))
                            targets.add(p.path("containerPort").asInt());
                if (targets.size() != 1) throw new IllegalStateException("ROOT_HTTP_NAMED_PORT_AMBIGUOUS");
                target = targets.iterator().next();
            }
        }
        try (var forward = api.pods().inNamespace(settings.namespace)
                .withName(selected.at("/metadata/name").asText())
                .portForward(target, java.net.InetAddress.getByName("127.0.0.1"), 0)) {
            if (!forward.isAlive()) throw new IllegalStateException("ROOT_HTTP_FORWARD_NOT_ALIVE");
            rootProbe.verify("http://127.0.0.1:" + forward.getLocalPort());
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("ROOT_HTTP_TUNNEL_IO: type=" + failure.getClass().getSimpleName());
        }
    }

    private void assertScaleMemoryQuota(JsonNode deployment, int desiredReplicas) {
        int additionalReplicas = desiredReplicas - deployment.at("/spec/replicas").asInt(1);
        if (additionalReplicas <= 0) return;

        JsonNode livePod = readyPodForDeployment(deployment);
        java.math.BigDecimal regularContainers = containerMemoryLimits(livePod.at("/spec/containers"));
        java.math.BigDecimal restartableInitContainers = java.math.BigDecimal.ZERO;
        java.math.BigDecimal largestBlockingInitContainer = java.math.BigDecimal.ZERO;
        for (JsonNode initContainer : livePod.at("/spec/initContainers")) {
            java.math.BigDecimal memory = containerMemoryLimits(
                    WorkloadJson.JSON.getNodeFactory().arrayNode().add(initContainer));
            if ("Always".equals(initContainer.path("restartPolicy").asText()))
                restartableInitContainers = restartableInitContainers.add(memory);
            else largestBlockingInitContainer = largestBlockingInitContainer.max(memory);
        }
        java.math.BigDecimal podOverhead = memoryLimit(livePod.at("/spec/overhead/memory"));
        java.math.BigDecimal runningContainers = regularContainers.add(restartableInitContainers);
        java.math.BigDecimal perPodBytes = runningContainers.max(largestBlockingInitContainer).add(podOverhead);
        if (perPodBytes.signum() <= 0)
            throw new IllegalStateException("QUOTA_PREFLIGHT_LIMITS_MEMORY_MISSING: live pod containers do not "
                    + "declare limits.memory; no scale mutation was sent");

        JsonNode quotas = read("resourcequota", null).path("items");
        int evaluated = 0;
        for (JsonNode quota : quotas) {
            String hardText = quota.at("/status/hard/limits.memory").asText("");
            String usedText = quota.at("/status/used/limits.memory").asText("");
            if (hardText.isBlank() || usedText.isBlank()) continue;
            evaluated++;
            java.math.BigDecimal hardBytes = kubernetesMemoryBytes(hardText);
            java.math.BigDecimal usedBytes = kubernetesMemoryBytes(usedText);
            java.math.BigDecimal availableBytes = hardBytes.subtract(usedBytes).max(java.math.BigDecimal.ZERO);
            java.math.BigDecimal requiredBytes = perPodBytes.multiply(java.math.BigDecimal.valueOf(additionalReplicas));
            String quotaName = quota.at("/metadata/name").asText("unknown");
            steps.container.KubernetesTunnelSteps.evidence("Scale memory quota preflight", Map.of(
                    "quota", quotaName, "hard", hardText, "used", usedText,
                    "availableBytes", availableBytes.toPlainString(),
                    "requiredBytes", requiredBytes.toPlainString(),
                    "perPodBytes", perPodBytes.toPlainString(),
                    "additionalReplicas", additionalReplicas,
                    "resourceSource", "live-ready-pod-including-injected-containers",
                    "pod", livePod.at("/metadata/name").asText(), "mutationDispatched", false));
            if (requiredBytes.compareTo(availableBytes) > 0)
                throw new IllegalStateException("QUOTA_INSUFFICIENT: limits.memory required="
                        + requiredBytes.toPlainString() + " bytes, available=" + availableBytes.toPlainString()
                        + " bytes in resourcequota/" + quotaName + "; no scale mutation was sent");
        }
        if (evaluated == 0)
            steps.container.KubernetesTunnelSteps.evidence("Scale memory quota preflight", Map.of(
                    "status", "NO_LIMITS_MEMORY_QUOTA", "perPodBytes", perPodBytes.toPlainString(),
                    "additionalReplicas", additionalReplicas,
                    "resourceSource", "live-ready-pod-including-injected-containers",
                    "pod", livePod.at("/metadata/name").asText(), "mutationDispatched", false));
    }

    private JsonNode readyPodForDeployment(JsonNode deployment) {
        JsonNode selector = deployment.at("/spec/selector/matchLabels");
        for (JsonNode pod : read("pods", null).path("items")) {
            boolean labelsMatch = true;
            var fields = selector.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (!entry.getValue().asText().equals(pod.at("/metadata/labels/" + escape(entry.getKey())).asText())) {
                    labelsMatch = false;
                    break;
                }
            }
            if (!labelsMatch) continue;
            boolean ready = false;
            for (JsonNode condition : pod.at("/status/conditions"))
                if ("Ready".equals(condition.path("type").asText())
                        && "True".equals(condition.path("status").asText())) ready = true;
            if (ready && "Running".equals(pod.at("/status/phase").asText())) return pod;
        }
        throw new IllegalStateException("NO_READY_POD: cannot calculate injected-container quota before scale; "
                + "no mutation was sent");
    }

    private static java.math.BigDecimal containerMemoryLimits(JsonNode containers) {
        java.math.BigDecimal result = java.math.BigDecimal.ZERO;
        if (containers.isArray())
            for (JsonNode container : containers)
                result = result.add(memoryLimit(container.at("/resources/limits/memory")));
        return result;
    }

    private static java.math.BigDecimal memoryLimit(JsonNode value) {
        return value.isTextual() && !value.asText().isBlank()
                ? kubernetesMemoryBytes(value.asText()) : java.math.BigDecimal.ZERO;
    }

    private static java.math.BigDecimal kubernetesMemoryBytes(String raw) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^([0-9]+(?:\\.[0-9]+)?)(Ki|Mi|Gi|Ti|Pi|Ei|[kKMGTPE]|m)?$")
                .matcher(raw.trim());
        if (!matcher.matches()) throw new IllegalStateException("Unsupported Kubernetes memory quantity: " + raw);
        java.math.BigDecimal value = new java.math.BigDecimal(matcher.group(1));
        String unit = matcher.group(2) == null ? "" : matcher.group(2);
        if ("m".equals(unit)) return value.movePointLeft(3);
        int power = switch (unit.toUpperCase(java.util.Locale.ROOT)) {
            case "K", "KI" -> 1;
            case "M", "MI" -> 2;
            case "G", "GI" -> 3;
            case "T", "TI" -> 4;
            case "P", "PI" -> 5;
            case "E", "EI" -> 6;
            default -> 0;
        };
        java.math.BigDecimal base = java.math.BigDecimal.valueOf(unit.endsWith("i") ? 1024L : 1000L);
        return value.multiply(base.pow(power));
    }
}
