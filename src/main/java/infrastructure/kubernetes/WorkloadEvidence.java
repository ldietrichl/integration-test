package infrastructure.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import config.services.container.ContainerServiceKubernetesAccess;
import config.services.container.KubernetesTunnelSettings;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.qameta.allure.Allure;

import java.time.Instant;
import java.util.*;

/** Read-only, bounded evidence through the workload-owned Fabric8 client. */
final class WorkloadEvidence {
    private final KubernetesTunnelSettings settings;
    private final KubernetesClient api;
    private final Instant started = Instant.now();
    private final Map<String, String> known = new LinkedHashMap<>();
    private final ArrayNode timeline = WorkloadJson.JSON.createArrayNode();
    private JsonNode lastEvents = WorkloadJson.JSON.createArrayNode();
    private boolean allureAttachmentsEnabled = true;
    private Instant scenarioStarted;
    private Instant operationStarted;
    private Instant operationFinished;
    private final Map<String, OcServiceLogs.Capture> operationLogs = new LinkedHashMap<>();

    boolean scenarioActive() { return scenarioStarted != null; }
    void startOperation(Instant started) {
        operationStarted = Objects.requireNonNull(started);
        operationFinished = null;
        operationLogs.clear();
    }
    void finishOperation(Instant finished) { operationFinished = Objects.requireNonNull(finished); }

    /** Opt-in scope: routine readiness checks still execute, without repeated attachments. */
    void beginScenario(Instant started) {
        scenarioStarted = Objects.requireNonNull(started);
        timeline.removeAll();
        startOperation(started);
    }

    void endScenario() {
        scenarioStarted = null; operationStarted = null; operationFinished = null; operationLogs.clear();
    }

    void disableAllureAttachments() {
        allureAttachmentsEnabled = false;
    }

    WorkloadEvidence(KubernetesTunnelSettings settings, KubernetesClient api) {
        this.settings = settings;
        this.api = api;
    }

    private void remember(JsonNode resource) {
        if (resource == null) return;
        String uid = resource.at("/metadata/uid").asText();
        if (!uid.isBlank()) known.put(uid, resource.at("/metadata/name").asText());
    }

    boolean record(String phase, JsonNode deployment, List<JsonNode> sets,
                   List<JsonNode> pods, Instant phaseStarted) {
        List<JsonNode> safeSets = sets == null ? List.of() : sets;
        List<JsonNode> safePods = pods == null ? List.of() : pods;
        remember(deployment);
        safeSets.forEach(this::remember);
        safePods.forEach(this::remember);
        ObjectNode row = WorkloadJson.JSON.createObjectNode();
        row.put("observedUtc", Instant.now().toString()).put("phase", phase);
        row.set("deployment", evidenceSummary(deployment));
        ArrayNode podRows = row.putArray("pods");
        safePods.forEach(pod -> podRows.add(evidenceSummary(pod)));
        ArrayNode rsRows = row.putArray("replicaSets");
        safeSets.forEach(rs -> rsRows.add(evidenceSummary(rs)));
        if (timeline.size() >= 128) timeline.remove(0);
        timeline.add(row);

        JsonNode events = events();
        ArrayNode selected = WorkloadJson.JSON.createArrayNode();
        boolean currentQuota = false;
        Set<String> activeSets = new HashSet<>();
        for (JsonNode rs : safeSets)
            if (rs.at("/spec/replicas").asInt() > 0) activeSets.add(rs.at("/metadata/uid").asText());
        for (JsonNode event : events.path("items")) {
            String target = event.at("/regarding/uid").asText(event.at("/involvedObject/uid").asText());
            if (!known.containsKey(target)) continue;
            String observed = event.at("/series/lastObservedTime").asText(
                    event.path("eventTime").asText(event.path("deprecatedLastTimestamp").asText(
                            event.at("/metadata/creationTimestamp").asText())));
            boolean fresh = false;
            try { fresh = !Instant.parse(observed).isBefore(phaseStarted); }
            catch (RuntimeException ignored) { }
            String message = event.path("note").asText(event.path("message").asText());
            ObjectNode item = selected.addObject();
            item.put("resource", known.get(target)).put("uid", target)
                    .put("lastObservedUtc", observed).put("currentOperation", fresh)
                    .put("reason", event.path("reason").asText())
                    .put("type", event.path("type").asText())
                    .put("count", event.path("deprecatedCount").asInt(event.path("count").asInt(1)))
                    .put("message", OcServiceLogs.sanitize(message));
            if (fresh && activeSets.contains(target)
                    && "FailedCreate".equals(event.path("reason").asText())
                    && message.toLowerCase(Locale.ROOT).contains("exceeded quota")) currentQuota = true;
        }
        lastEvents = events.has("captureStatus") ? events : selected;
        row.put("freshFailedCreateQuota", currentQuota);
        return currentQuota;
    }

    void capture(String phase, JsonNode deployment, List<JsonNode> sets,
                 List<JsonNode> pods, Instant phaseStarted) {
        capture(phase, deployment, sets, pods, phaseStarted, false, true);
    }

    void finishScenario(String phase, JsonNode deployment, List<JsonNode> sets,
                        List<JsonNode> pods, Instant phaseStarted, boolean failed) {
        try { capture(phase, deployment, sets, pods, phaseStarted, true, failed); }
        finally { endScenario(); }
    }

    private void capture(String phase, JsonNode deployment, List<JsonNode> sets,
                         List<JsonNode> pods, Instant phaseStarted, boolean scenarioEnd, boolean failed) {
        List<JsonNode> safeSets = sets == null ? List.of() : sets;
        List<JsonNode> safePods = pods == null ? List.of() : pods;
        try {
            record(phase, deployment, safeSets, safePods, phaseStarted);
            boolean scoped = scenarioStarted != null;
            if (scoped) {
                if (phase.contains("before-delete")) collectOperationLogs(safePods, phase, Instant.now());
                if (scenarioEnd) {
                    if (failed) {
                        attach("Состояние pod при ошибке", timeline.get(timeline.size() - 1));
                        attach("События pod при ошибке", lastEvents);
                    }
                    collectOperationLogs(safePods, phase, operationFinished == null ? Instant.now() : operationFinished);
                    attachOperationLogs();
                }
                return;
            }
            boolean exceptionalPhase = phase.contains("fail") || phase.contains("replacement") || phase.contains("rollback");
            if (scoped && !scenarioEnd && !exceptionalPhase) return;
            boolean detailed = !scoped || exceptionalPhase || (scenarioEnd && failed);
            if (detailed) attach(phase + " - readiness timeline", timeline);
            else attach(phase + " - workload state", timeline.get(timeline.size() - 1));
            if (detailed) {
                attach(phase + " - workload events (historical/current distinguished)", lastEvents);
                JsonNode quota = quotas();
                ArrayNode quotas = WorkloadJson.JSON.createArrayNode();
                for (JsonNode item : quota.path("items")) {
                    ObjectNode q = quotas.addObject();
                    q.put("name", item.at("/metadata/name").asText());
                    q.set("hard", item.at("/status/hard"));
                    q.set("used", item.at("/status/used"));
                }
                attach(phase + " - namespace quota (declared limits, not RAM usage)",
                        quota.has("captureStatus") ? quota : quotas);
            }
            if (allureAttachmentsEnabled && settings.logsEnabled) for (JsonNode pod : safePods) {
                String podName = pod.at("/metadata/name").asText();
                String uid = pod.at("/metadata/uid").asText();
                if (podName.isBlank() || uid.isBlank()) continue;
                int restarts = -1;
                for (JsonNode container : pod.at("/status/containerStatuses"))
                    if (settings.logsContainer.equals(container.path("name").asText()))
                        restarts = container.path("restartCount").asInt(-1);
                var target = new OcServiceLogs.Target(podName, uid, settings.logsContainer,
                        settings.servicePort, restarts);
                var logs = OcServiceLogs.captureNative(settings, api, target,
                        "workload-" + phase, scoped ? scenarioStarted : started, Instant.now());
                attach(phase + " - log capture " + podName, WorkloadJson.JSON.valueToTree(logs.metadata()));
                if (allureAttachmentsEnabled && !logs.text().isBlank()) Allure.addAttachment(phase + " - service log " + podName, logs.text());
            }
        } catch (RuntimeException failure) {
            unavailable(phase, failure);
        }
    }

    private void collectOperationLogs(List<JsonNode> pods, String phase, Instant finished) {
        if (!allureAttachmentsEnabled) return;
        for (JsonNode pod : pods) {
            String name = pod.at("/metadata/name").asText(), uid = pod.at("/metadata/uid").asText();
            if (name.isBlank() || uid.isBlank()) continue;
            int restarts = -1;
            for (JsonNode container : pod.at("/status/containerStatuses"))
                if (settings.logsContainer.equals(container.path("name").asText())) restarts = container.path("restartCount").asInt(-1);
            var target = new OcServiceLogs.Target(name, uid, settings.logsContainer, settings.servicePort, restarts);
            var logs = OcServiceLogs.captureNativeWindow(settings, api, target, phase, operationStarted, finished);
            // Keep the pre-delete snapshot when the old UID is no longer available at teardown.
            if (!logs.text().isBlank() || !operationLogs.containsKey(uid)) operationLogs.put(uid, logs);
        }
    }

    private void attachOperationLogs() {
        if (!allureAttachmentsEnabled) return;
        if (operationLogs.isEmpty()) {
            Allure.addAttachment("Лог pod сервиса — недоступен", "text/plain",
                    "status=NO_POD_SELECTED\noperationStartedUtc=" + operationStarted
                            + "\noperationFinishedUtc=" + operationFinished + "\nТекст лога не получен.", ".txt");
        }
        for (var capture : operationLogs.values()) {
            StringBuilder text = new StringBuilder();
            capture.metadata().forEach((key, value) -> text.append(key).append('=').append(value).append('\n'));
            text.append("\n----- Лог сервиса -----\n");
            text.append(capture.text().isBlank() ? "[Строки лога не получены; причина указана в status выше.]\n" : capture.text());
            Allure.addAttachment("Лог pod сервиса — " + capture.metadata().getOrDefault("pod", settings.service),
                    "text/plain", text.toString(), ".txt");
        }
    }

    static String configMapText(JsonNode value, Set<String> keys) {
        StringBuilder text = new StringBuilder();
        for (String key : List.of("name", "namespace", "uid", "resourceVersion"))
            text.append(key).append('=').append(value.path("metadata").path(key).asText()).append('\n');
        text.append("\nПараметры ConfigMap, используемые сценарием (фактический GET):\n");
        for (String key : new TreeSet<>(keys)) {
            String actual = value.path("data").path(key).asText("[MISSING]");
            text.append(key).append("=\n");
            actual.lines().forEach(line -> text.append("  ").append(OcServiceLogs.sanitize(line)).append('\n'));
        }
        return text.toString();
    }

    private JsonNode evidenceSummary(JsonNode resource) {
        if (scenarioStarted == null) return summary(resource);
        ObjectNode result = WorkloadJson.JSON.createObjectNode();
        if (resource == null) return result;
        for (String key : List.of("name", "uid", "generation", "deletionTimestamp"))
            if (resource.path("metadata").has(key)) result.set(key, resource.path("metadata").path(key));
        result.put("desiredReplicas", resource.at("/spec/replicas").asInt(-1));
        ObjectNode status = result.putObject("status");
        for (String key : List.of("phase", "observedGeneration", "replicas", "readyReplicas", "availableReplicas", "updatedReplicas"))
            if (resource.path("status").has(key)) status.set(key, resource.path("status").path(key));
        ArrayNode conditions = status.putArray("conditions");
        for (JsonNode condition : resource.at("/status/conditions")) {
            ObjectNode item = conditions.addObject();
            for (String key : List.of("type", "status", "reason", "lastTransitionTime"))
                if (condition.has(key)) item.set(key, condition.path(key));
        }
        ArrayNode containers = status.putArray("containers");
        for (JsonNode container : resource.at("/status/containerStatuses")) {
            ObjectNode item = containers.addObject();
            for (String key : List.of("name", "ready", "restartCount", "started"))
                if (container.has(key)) item.set(key, container.path(key));
            item.set("state", safeStatus(container.path("state")));
        }
        return result;
    }

    void unavailable(String phase, RuntimeException failure) {
        if (!allureAttachmentsEnabled) return;
        try {
            Allure.addAttachment("Workload evidence unavailable: " + phase,
                    "failureType=" + failure.getClass().getSimpleName()
                            + "\nfailureCode=" + ContainerServiceKubernetesAccess.failureCode(failure)
                            + "\nEvidence failure must not replace the original test/rollback error.");
        } catch (RuntimeException ignored) { }
    }

    private JsonNode events() {
        try { return WorkloadJson.JSON.readTree(Serialization.asJson(
                api.events().v1().events().inNamespace(settings.namespace).list())); }
        catch (KubernetesClientException failure) {
            return WorkloadJson.JSON.createObjectNode().put("captureStatus",
                    ContainerServiceKubernetesAccess.failureCode(failure));
        } catch (Exception failure) {
            return WorkloadJson.JSON.createObjectNode().put("captureStatus", "EVENTS_READ_UNAVAILABLE");
        }
    }

    private JsonNode quotas() {
        try { return WorkloadJson.JSON.readTree(Serialization.asJson(
                api.resourceQuotas().inNamespace(settings.namespace).list())); }
        catch (KubernetesClientException failure) {
            return WorkloadJson.JSON.createObjectNode().put("captureStatus",
                    ContainerServiceKubernetesAccess.failureCode(failure));
        } catch (Exception failure) {
            return WorkloadJson.JSON.createObjectNode().put("captureStatus", "QUOTA_READ_UNAVAILABLE");
        }
    }

    private static ObjectNode summary(JsonNode resource) {
        ObjectNode result = WorkloadJson.JSON.createObjectNode();
        if (resource == null) return result;
        for (String key : List.of("name", "uid", "generation", "deletionTimestamp"))
            if (resource.path("metadata").has(key)) result.set(key, resource.path("metadata").path(key));
        result.set("status", safeStatus(resource.path("status")));
        if (resource.path("spec").has("replicas")) result.set("desiredReplicas", resource.at("/spec/replicas"));
        ArrayNode resources = result.putArray("containerResources");
        for (JsonNode container : resource.at("/spec/containers")) {
            ObjectNode row = resources.addObject().put("name", container.path("name").asText());
            row.set("resources", container.path("resources"));
        }
        return result;
    }

    private static JsonNode safeStatus(JsonNode value) {
        if (value.isTextual()) return WorkloadJson.JSON.getNodeFactory().textNode(OcServiceLogs.sanitize(value.asText()));
        if (value.isObject()) {
            ObjectNode result = WorkloadJson.JSON.createObjectNode();
            value.fields().forEachRemaining(entry -> result.set(entry.getKey(), safeStatus(entry.getValue())));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = WorkloadJson.JSON.createArrayNode();
            value.forEach(item -> result.add(safeStatus(item)));
            return result;
        }
        return value;
    }

    private void attach(String name, JsonNode value) {
        if (allureAttachmentsEnabled)
            Allure.addAttachment(name, "application/json", value.toPrettyString(), ".json");
    }
}
