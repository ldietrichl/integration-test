package infrastructure.kubernetes;

import config.services.container.ContainerServiceKubernetesAccess;
import config.services.container.KubernetesTunnelSettings;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import ru.sber.qa.containers.client.ContainerServiceClient;

import java.time.Instant;
import java.util.*;

/**
 * Read-only, bounded observation through the existing ContainerService/Fabric8 access checks.
 * No client allocation or close, shell fallback, Secret reads, or resource mutations.
 * Restart/configuration changes belong to KubernetesWorkloadControl.
 */
public final class KubernetesServiceObservation {
    public record Snapshot(String status, List<Pod> pods, int matchedPods, int podLimit) {
        public boolean truncated() { return matchedPods > podLimit; }
    }
    public record PodLog(OcServiceLogs.Target target, OcServiceLogs.Capture capture) { }

    private final KubernetesTunnelSettings settings;
    private final ContainerServiceClient framework;
    private final KubernetesClient api;
    private final int maxPods;
    private final Map<String, ConfigMap> configMaps = new HashMap<>();
    private final Map<String, OcServiceLogs.Target> targets = new LinkedHashMap<>();

    public KubernetesServiceObservation(KubernetesTunnelSettings settings, int maxPods) {
        if (maxPods < 1 || maxPods > 32) throw new IllegalArgumentException("Observation pod limit must be 1..32");
        this.settings = Objects.requireNonNull(settings);
        settings.requireContainerServiceManagement();
        this.maxPods = maxPods;
        this.framework = ContainerServiceKubernetesAccess.frameworkClient(settings);
        this.api = ContainerServiceKubernetesAccess.client(settings);
    }

    public Map<String, Object> connectionDiagnostics() {
        return ContainerServiceKubernetesAccess.safeDiagnostics(settings, api);
    }

    public Snapshot snapshot() {
        configMaps.clear(); // current ConfigMap revision must be read again in the next phase
        var service = api.services().inNamespace(settings.namespace).withName(settings.service).get();
        if (service == null || service.getSpec() == null || service.getSpec().getSelector() == null
                || service.getSpec().getSelector().isEmpty())
            return new Snapshot("SERVICE_SELECTOR_MISSING", List.of(), 0, maxPods);
        List<Pod> matched = api.pods().inNamespace(settings.namespace).withLabels(service.getSpec().getSelector())
                .list().getItems().stream().sorted(Comparator.comparing(p -> p.getMetadata().getName())).toList();
        List<Pod> selected = matched.stream().limit(maxPods).toList();
        for (Pod pod : selected) {
            if (pod.getSpec() == null || pod.getSpec().getContainers() == null
                    || pod.getSpec().getContainers().stream().noneMatch(c -> settings.logsContainer.equals(c.getName()))) continue;
            if (targets.size() < 2 * maxPods) {
                var target = new OcServiceLogs.Target(pod.getMetadata().getName(), pod.getMetadata().getUid(),
                        settings.logsContainer, settings.servicePort, restartCount(pod, settings.logsContainer));
                targets.putIfAbsent(target.uid(), target);
            }
        }
        return new Snapshot(matched.isEmpty() ? "NO_PODS" : "SNAPSHOT_COLLECTED", selected, matched.size(), maxPods);
    }

    /** Raw resource stays in memory; callers must whitelist values before attaching any evidence. */
    public ConfigMap configMap(String name) {
        if (!configMaps.containsKey(name))
            configMaps.put(name, api.configMaps().inNamespace(settings.namespace).withName(name).get());
        return configMaps.get(name);
    }

    public int selectedPodIncarnations() { return targets.size(); }

    public List<PodLog> captureLogs(String scenario, Instant started, Instant finished) {
        List<PodLog> result = new ArrayList<>();
        for (var target : targets.values()) {
            OcServiceLogs.Capture capture;
            try { capture = OcServiceLogs.capture(settings, framework, api, target, scenario, started, finished); }
            catch (RuntimeException failure) {
                capture = new OcServiceLogs.Capture(Map.of("pod", target.pod(), "status",
                        ContainerServiceKubernetesAccess.failureCode(failure)), "");
            }
            result.add(new PodLog(target, capture));
        }
        return List.copyOf(result);
    }

    public static int restartCount(Pod pod, String container) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) return -1;
        return pod.getStatus().getContainerStatuses().stream().filter(c -> container.equals(c.getName()))
                .map(c -> c.getRestartCount() == null ? -1 : c.getRestartCount()).findFirst().orElse(-1);
    }
}
