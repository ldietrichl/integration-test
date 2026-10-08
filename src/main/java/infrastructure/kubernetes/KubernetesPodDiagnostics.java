package infrastructure.kubernetes;

import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Allowlisted observations only: no pod specs, env, annotations, event messages or credentials. */
public final class KubernetesPodDiagnostics {
    private KubernetesPodDiagnostics() { }

    public static boolean matchesSelector(Pod pod, Map<String, String> selector) {
        if (pod == null || pod.getMetadata() == null || selector == null || selector.isEmpty()) return false;
        var labels = pod.getMetadata().getLabels();
        return labels != null && selector.entrySet().stream()
                .allMatch(entry -> entry.getValue().equals(labels.get(entry.getKey())));
    }

    public static List<String> rejectionReasons(Pod pod, Map<String, String> selector) {
        List<String> reasons = new ArrayList<>();
        if (!matchesSelector(pod, selector)) reasons.add("SERVICE_SELECTOR_MISMATCH");
        if (pod == null || pod.getMetadata() == null) {
            reasons.add("POD_METADATA_MISSING"); return reasons;
        }
        if (pod.getMetadata().getName() == null || pod.getMetadata().getUid() == null)
            reasons.add("POD_IDENTITY_MISSING");
        if (pod.getMetadata().getDeletionTimestamp() != null) reasons.add("POD_TERMINATING");
        if (pod.getStatus() == null) { reasons.add("POD_STATUS_MISSING"); return reasons; }
        if (!"Running".equals(pod.getStatus().getPhase())) reasons.add("POD_NOT_RUNNING");
        if (pod.getStatus().getContainerStatuses() != null
                && pod.getStatus().getContainerStatuses().stream().anyMatch(c -> c.getState() != null
                && c.getState().getWaiting() != null
                && isImagePullFailure(c.getState().getWaiting().getReason())))
            reasons.add("IMAGE_PULL_FAILURE");
        boolean ready = pod.getStatus().getConditions() != null
                && pod.getStatus().getConditions().stream()
                .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
        if (!ready) reasons.add("POD_READY_CONDITION_NOT_TRUE");
        return reasons;
    }

    public static Map<String, Object> describe(Pod pod, Map<String, String> selector) {
        Map<String, Object> result = new LinkedHashMap<>();
        var rejected = rejectionReasons(pod, selector);
        result.put("selectionEligible", rejected.isEmpty()); result.put("rejectionReasons", rejected);
        result.put("matchesServiceSelector", matchesSelector(pod, selector));
        if (pod == null || pod.getMetadata() == null) return result;
        var m = pod.getMetadata();
        result.put("name", m.getName()); result.put("uid", m.getUid());
        result.put("resourceVersion", m.getResourceVersion());
        result.put("creationTimestamp", m.getCreationTimestamp());
        result.put("deletionTimestamp", m.getDeletionTimestamp());
        result.put("deletionGracePeriodSeconds", m.getDeletionGracePeriodSeconds());
        Map<String, String> labels = new LinkedHashMap<>();
        if (selector != null) for (String key : selector.keySet())
            labels.put(key, m.getLabels() == null ? null : m.getLabels().get(key));
        result.put("observedSelectorLabels", labels);
        List<Map<String, Object>> owners = new ArrayList<>();
        if (m.getOwnerReferences() != null) for (var owner : m.getOwnerReferences()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("kind", owner.getKind()); item.put("name", owner.getName());
            item.put("uid", owner.getUid()); item.put("controller", owner.getController()); owners.add(item);
        }
        result.put("ownerReferences", owners);
        var status = pod.getStatus();
        if (status == null) return result;
        result.put("phase", status.getPhase()); result.put("reason", status.getReason());
        result.put("startTime", status.getStartTime());
        List<Map<String, Object>> conditions = new ArrayList<>();
        if (status.getConditions() != null) for (var c : status.getConditions()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", c.getType()); item.put("status", c.getStatus());
            item.put("reason", c.getReason()); item.put("lastTransitionTime", c.getLastTransitionTime());
            conditions.add(item);
        }
        result.put("conditions", conditions);
        List<Map<String, Object>> containers = new ArrayList<>();
        if (status.getContainerStatuses() != null) for (var c : status.getContainerStatuses()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", c.getName()); item.put("ready", c.getReady()); item.put("restarts", c.getRestartCount());
            item.put("state", state(c.getState())); item.put("lastState", state(c.getLastState())); containers.add(item);
        }
        result.put("containers", containers);
        return result;
    }

    private static Map<String, Object> state(ContainerState s) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (s == null) { result.put("kind", "UNKNOWN"); return result; }
        if (s.getWaiting() != null) {
            result.put("kind", "WAITING"); result.put("reason", s.getWaiting().getReason());
            if (isImagePullFailure(s.getWaiting().getReason()))
                result.put("messageCategory", imagePullMessageCategory(s.getWaiting().getMessage()));
        } else if (s.getTerminated() != null) {
            var t = s.getTerminated();
            result.put("kind", "TERMINATED"); result.put("reason", t.getReason());
            result.put("exitCode", t.getExitCode()); result.put("signal", t.getSignal());
            result.put("startedAt", t.getStartedAt()); result.put("finishedAt", t.getFinishedAt());
        } else if (s.getRunning() != null) {
            result.put("kind", "RUNNING"); result.put("startedAt", s.getRunning().getStartedAt());
        } else result.put("kind", "UNKNOWN");
        return result;
    }

    private static boolean isImagePullFailure(String reason) {
        return "ImagePullBackOff".equals(reason) || "ErrImagePull".equals(reason)
                || "InvalidImageName".equals(reason);
    }

    /** Classification only; free-form registry messages may contain credentials and stay private. */
    private static String imagePullMessageCategory(String message) {
        String text = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        if (text.contains("unauthorized") || text.contains("authentication required")
                || text.contains("pull access denied") || text.contains("denied:"))
            return "REGISTRY_AUTH_OR_ACCESS";
        if (text.contains("manifest unknown") || text.contains("name unknown") || text.contains("not found"))
            return "IMAGE_OR_TAG_NOT_FOUND";
        if (text.contains("x509") || text.contains("certificate") || text.contains("tls"))
            return "REGISTRY_TLS";
        if (text.contains("toomanyrequests") || text.contains("too many requests"))
            return "REGISTRY_RATE_LIMIT";
        if (text.contains("timeout") || text.contains("deadline exceeded") || text.contains("connection refused")
                || text.contains("no such host"))
            return "REGISTRY_NETWORK";
        return "IMAGE_PULL_CAUSE_NOT_PROVEN";
    }

    public static Map<String, Object> selection(String namespace, String service,
                                                 Map<String, String> selector, List<Pod> pods) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capturedAtUtc", Instant.now().toString());
        result.put("namespace", namespace); result.put("service", service);
        result.put("serviceSelector", selector == null ? Map.of() : new LinkedHashMap<>(selector));
        result.put("candidateCount", pods.size());
        result.put("eligibleCount", pods.stream().filter(p -> rejectionReasons(p, selector).isEmpty()).count());
        result.put("observedBlockingReasons", pods.stream().flatMap(p -> rejectionReasons(p, selector).stream())
                .distinct().sorted().toList());
        result.put("pods", pods.stream().limit(30).map(p -> describe(p, selector)).toList());
        result.put("podsTruncated", pods.size() > 30);
        result.put("scope", "OBSERVATION_ONLY_NOT_HTTP_BACKEND_OR_SHUTDOWN_INITIATOR_PROOF");
        return result;
    }

    /** Optional APIs may be forbidden. Keep the primary HTTP failure and expose collection status. */
    public static Map<String, Object> controlPlane(KubernetesClient api, String namespace, String service,
                                                    String deployment, List<Pod> pods) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capturedAtUtc", Instant.now().toString()); result.put("shutdownInitiator", "NOT_PROVEN");
        try {
            var d = api.apps().deployments().inNamespace(namespace).withName(deployment).get();
            Map<String, Object> item = new LinkedHashMap<>(); item.put("found", d != null);
            if (d != null) {
                item.put("uid", d.getMetadata().getUid()); item.put("generation", d.getMetadata().getGeneration());
                item.put("deletionTimestamp", d.getMetadata().getDeletionTimestamp());
                if (d.getSpec() != null) item.put("desiredReplicas", d.getSpec().getReplicas());
                if (d.getStatus() != null) {
                    var s = d.getStatus();
                    item.put("observedGeneration", s.getObservedGeneration()); item.put("replicas", s.getReplicas());
                    item.put("readyReplicas", s.getReadyReplicas()); item.put("availableReplicas", s.getAvailableReplicas());
                    item.put("updatedReplicas", s.getUpdatedReplicas());
                    List<Map<String, Object>> conditions = new ArrayList<>();
                    if (s.getConditions() != null) for (var c : s.getConditions()) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("type", c.getType()); entry.put("status", c.getStatus());
                        entry.put("reason", c.getReason()); entry.put("lastTransitionTime", c.getLastTransitionTime());
                        conditions.add(entry);
                    }
                    item.put("conditions", conditions);
                }
            }
            result.put("deployment", item);
        } catch (RuntimeException failure) { result.put("deployment", unavailable(failure)); }
        try {
            var e = api.endpoints().inNamespace(namespace).withName(service).get();
            Map<String, Object> item = new LinkedHashMap<>(); item.put("found", e != null);
            List<Map<String, Object>> targets = new ArrayList<>();
            if (e != null && e.getSubsets() != null) for (var subset : e.getSubsets()) {
                if (subset.getAddresses() != null) for (var a : subset.getAddresses()) {
                    if (targets.size() >= 100) break;
                    Map<String, Object> t = new LinkedHashMap<>(); t.put("ready", true);
                    t.put("podUid", a.getTargetRef() == null ? null : a.getTargetRef().getUid());
                    t.put("podName", a.getTargetRef() == null ? null : a.getTargetRef().getName()); targets.add(t);
                }
                if (subset.getNotReadyAddresses() != null) for (var a : subset.getNotReadyAddresses()) {
                    if (targets.size() >= 100) break;
                    Map<String, Object> t = new LinkedHashMap<>(); t.put("ready", false);
                    t.put("podUid", a.getTargetRef() == null ? null : a.getTargetRef().getUid());
                    t.put("podName", a.getTargetRef() == null ? null : a.getTargetRef().getName()); targets.add(t);
                }
            }
            item.put("targets", targets); item.put("targetLimit", 100); result.put("serviceEndpoints", item);
        } catch (RuntimeException failure) { result.put("serviceEndpoints", unavailable(failure)); }
        // One pod per snapshot, never a namespace-wide events dump.
        Pod observed = pods.stream().filter(p -> p.getMetadata() != null && p.getMetadata().getUid() != null)
                .findFirst().orElse(null);
        if (observed != null) {
            try {
                var events = api.v1().events().inNamespace(namespace)
                        .withField("involvedObject.uid", observed.getMetadata().getUid()).list().getItems();
                List<Map<String, Object>> items = new ArrayList<>();
                for (var e : events.stream().limit(30).toList()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("type", e.getType()); item.put("reason", e.getReason()); item.put("count", e.getCount());
                    item.put("firstTimestamp", e.getFirstTimestamp()); item.put("lastTimestamp", e.getLastTimestamp());
                    item.put("reportingComponent", e.getReportingComponent());
                    if ("Failed".equals(e.getReason()) || "BackOff".equals(e.getReason()))
                        item.put("messageCategory", imagePullMessageCategory(e.getMessage()));
                    items.add(item);
                }
                result.put("podEvents", Map.of("podUid", observed.getMetadata().getUid(),
                        "count", events.size(), "events", items, "messagesOmitted", true));
            } catch (RuntimeException failure) { result.put("podEvents", unavailable(failure)); }
        } else result.put("podEvents", Map.of("status", "NO_POD_IDENTITY"));
        return result;
    }

    public static Map<String, Object> unavailable(RuntimeException failure) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UNAVAILABLE"); result.put("exceptionType", failure.getClass().getSimpleName());
        var safe = KubernetesDiagnosticException.find(failure);
        if (safe != null) result.putAll(safe.safeDetails());
        else if (failure instanceof KubernetesClientException apiFailure) result.put("httpStatus", apiFailure.getCode());
        result.put("exceptionMessages", "suppressed"); return result;
    }
}
