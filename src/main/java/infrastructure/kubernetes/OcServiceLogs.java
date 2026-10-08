package infrastructure.kubernetes;

import config.services.container.ContainerServiceKubernetesAccess;
import config.services.container.KubernetesTunnelSettings;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;
import ru.sber.qa.containers.client.ContainerServiceClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Bounded scheduler logs collected by ContainerService from one explicitly selected pod. */
public final class OcServiceLogs {
    private OcServiceLogs() { }

    public record Target(String pod, String uid, String container, int port, int restartCount) {
        public Map<String, Object> description() {
            return Map.of("pod", pod, "podUid", uid, "container", container,
                    "targetPort", port, "restartCountAtSelection", restartCount);
        }
    }

    public record Capture(Map<String, Object> metadata, String text) { }

    public static Capture captureNative(KubernetesTunnelSettings settings, KubernetesClient api,
                                        Target target, String scenario, Instant started, Instant finished) {
        return capture(settings, null, api, target, scenario, started, finished);
    }

    /** Kubernetes timestamps bound both ends; only used by callers requesting an exact operation window. */
    public static Capture captureNativeWindow(KubernetesTunnelSettings settings, KubernetesClient api,
                                              Target target, String scenario, Instant started, Instant finished) {
        return capture(settings, null, api, target, scenario, started, finished, true);
    }

    public static Target discover(KubernetesTunnelSettings settings, KubernetesClient api) {
        var service = api.services().inNamespace(settings.namespace).withName(settings.service).get();
        if (service == null || service.getSpec() == null
                || service.getSpec().getSelector() == null || service.getSpec().getSelector().isEmpty())
            throw new IllegalStateException("CONTAINER_SERVICE_SELECTOR_MISSING");
        ServicePort servicePort = service.getSpec().getPorts().stream()
                .filter(port -> port.getPort() != null && port.getPort() == settings.servicePort)
                .findFirst().orElseThrow(() -> new IllegalStateException("CONTAINER_SERVICE_TCP_PORT_MISSING"));
        Pod pod = api.pods().inNamespace(settings.namespace).withLabels(service.getSpec().getSelector())
                .list().getItems().stream().filter(OcServiceLogs::ready)
                .sorted(Comparator.comparing(item -> item.getMetadata().getName()))
                .findFirst().orElseThrow(() -> new IllegalStateException("CONTAINER_SERVICE_NO_READY_POD"));
        boolean containerFound = pod.getSpec().getContainers().stream()
                .anyMatch(container -> settings.logsContainer.equals(container.getName()));
        if (!containerFound) throw new IllegalStateException("CONTAINER_SERVICE_LOG_CONTAINER_NOT_FOUND");
        return new Target(pod.getMetadata().getName(), pod.getMetadata().getUid(), settings.logsContainer,
                resolvePort(servicePort, pod), restartCount(pod, settings.logsContainer));
    }

    public static Capture capture(KubernetesTunnelSettings settings, ContainerServiceClient service,
                                  KubernetesClient api, Target target, String scenario,
                                  Instant started, Instant finished) {
        return capture(settings, service, api, target, scenario, started, finished, false);
    }

    private static Capture capture(KubernetesTunnelSettings settings, ContainerServiceClient service,
                                   KubernetesClient api, Target target, String scenario,
                                   Instant started, Instant finished, boolean exactWindow) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("scenario", scenario);
        metadata.put("enabled", settings.logsEnabled);
        metadata.put("mode", service == null ? "Fabric8 bounded snapshot" : "ContainerService bounded duration snapshot after scenario");
        metadata.put("scope", "exact pod UID and named container; concurrent requests may be included");
        if (!settings.logsEnabled) return status(metadata, "DISABLED");
        if (started == null || target == null) return status(metadata, "NO_TARGET_NO_SERVICE_REQUEST");
        metadata.putAll(target.description());
        metadata.put("namespace", settings.namespace);
        metadata.put("service", settings.service);
        metadata.put("scenarioStartedUtc", started.toString());
        metadata.put("scenarioFinishedUtc", finished.toString());
        Instant since = exactWindow ? started : started.minusSeconds(settings.logsClockSkewSeconds);
        Instant until = exactWindow ? finished : finished.plusSeconds(settings.logsClockSkewSeconds);
        metadata.put("exactOperationWindow", exactWindow);
        metadata.put("sinceUtc", since.toString());
        metadata.put("untilUtc", until.toString());
        metadata.put("tailLimit", settings.logsTailLines);
        metadata.put("byteLimit", settings.logsMaxBytes);
        try {
            Pod current = api.pods().inNamespace(settings.namespace).withName(target.pod()).get();
            if (current == null) return status(metadata, "POD_REMOVED");
            if (!target.uid().equals(current.getMetadata().getUid())) return status(metadata, "POD_UID_CHANGED");
            int currentRestarts = restartCount(current, target.container());
            metadata.put("restartCountAtCollection", currentRestarts);
            boolean restarted = target.restartCount() >= 0 && currentRestarts != target.restartCount();
            metadata.put("containerRestarted", restarted);
            long seconds = Math.max(1L, Duration.between(since, until).getSeconds());
            List<String> all;
            if (service == null) {
                var container = api.pods().inNamespace(settings.namespace).withName(target.pod()).inContainer(target.container());
                String logs = (exactWindow ? container.usingTimestamps() : container)
                        .limitBytes(settings.logsMaxBytes).sinceTime(since.toString())
                        .tailingLines(settings.logsTailLines).getLog();
                all = logs == null ? List.of() : logs.lines().toList();
            } else {
                Map<String, List<String>> collected = service.getLogsFromPod(
                        "^" + Pattern.quote(target.pod()) + "$", target.container(), Duration.ofSeconds(seconds));
                all = new ArrayList<>(collected.getOrDefault(target.pod(), List.of()));
            }
            int from = Math.max(0, all.size() - settings.logsTailLines);
            List<String> bounded = all.subList(from, all.size());
            StringBuilder text = new StringBuilder();
            int bytes = 0;
            int redacted = 0;
            int outsideWindow = 0;
            int unparsedTimestamps = 0;
            // The server can already have applied its byte/line limits before local processing.
            boolean truncated = from > 0 || (service == null && (all.size() >= settings.logsTailLines
                    || all.stream().mapToLong(row -> row.getBytes(StandardCharsets.UTF_8).length + 1L).sum() >= settings.logsMaxBytes));
            boolean pem = false;
            for (String row : bounded) {
                if (exactWindow) {
                    int separator = row.indexOf(' ');
                    try {
                        Instant timestamp = Instant.parse(separator < 0 ? row : row.substring(0, separator));
                        if (timestamp.isBefore(started) || timestamp.isAfter(finished)) { outsideWindow++; continue; }
                    } catch (RuntimeException malformed) {
                        // Never silently drop an unparseable line or claim its boundary was verified.
                        unparsedTimestamps++;
                    }
                }
                if (row.contains("-----BEGIN ")) pem = true;
                String safe = pem ? "[PEM block suppressed]" : sanitize(row);
                if (row.contains("-----END ")) pem = false;
                if (!safe.equals(row)) redacted++;
                int lineBytes = safe.getBytes(StandardCharsets.UTF_8).length + 1;
                if (bytes + lineBytes > settings.logsMaxBytes) { truncated = true; break; }
                text.append(safe).append('\n');
                bytes += lineBytes;
            }
            metadata.put("receivedLines", all.size());
            metadata.put("redactedLines", redacted);
            metadata.put("outsideWindowLines", outsideWindow);
            metadata.put("unparsedTimestampLines", unparsedTimestamps);
            metadata.put("capturedBytes", bytes);
            metadata.put("mayBeTruncated", truncated);
            metadata.put("previousContainerLogsCollected", false);
            metadata.put("status", restarted ? "PARTIAL_CONTAINER_RESTARTED"
                    : truncated ? "PARTIAL_LIMIT" : unparsedTimestamps > 0 ? "PARTIAL_UNVERIFIED_TIMESTAMPS" : text.isEmpty() ? "EMPTY_WINDOW" : "COLLECTED");
            return new Capture(metadata, text.toString());
        } catch (RuntimeException failure) {
            metadata.put("failureType", failure.getClass().getSimpleName());
            return status(metadata, ContainerServiceKubernetesAccess.failureCode(failure));
        }
    }

    private static Capture status(Map<String, Object> metadata, String code) {
        metadata.put("status", code);
        return new Capture(metadata, "");
    }

    private static int restartCount(Pod pod, String container) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) return -1;
        return pod.getStatus().getContainerStatuses().stream()
                .filter(state -> container.equals(state.getName()))
                .map(state -> state.getRestartCount() == null ? -1 : state.getRestartCount())
                .findFirst().orElse(-1);
    }

    private static boolean ready(Pod pod) {
        return pod.getMetadata() != null && pod.getMetadata().getDeletionTimestamp() == null
                && pod.getStatus() != null && "Running".equals(pod.getStatus().getPhase())
                && pod.getStatus().getConditions() != null
                && pod.getStatus().getConditions().stream().anyMatch(condition ->
                "Ready".equals(condition.getType()) && "True".equals(condition.getStatus()));
    }

    private static int resolvePort(ServicePort servicePort, Pod pod) {
        IntOrString target = servicePort.getTargetPort();
        if (target == null) return servicePort.getPort();
        if (target.getIntVal() != null) return target.getIntVal();
        List<Integer> ports = pod.getSpec().getContainers().stream()
                .filter(container -> container.getPorts() != null)
                .flatMap(container -> container.getPorts().stream())
                .filter(port -> Objects.equals(target.getStrVal(), port.getName()))
                .map(ContainerPort::getContainerPort).filter(Objects::nonNull).distinct().toList();
        if (ports.size() != 1) throw new IllegalStateException("CONTAINER_SERVICE_TARGET_PORT_AMBIGUOUS");
        return ports.get(0);
    }

    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)authorization|cookie|password|passwd|passphrase|secret|token|private.?key|client-key-data|BEGIN .*KEY");
    private static final Pattern PAYLOAD = Pattern.compile(
            "(?i)\\b(?:request|response|body|payload|parameters)\\s*[:=]");
    private static final Pattern PERSONAL = Pattern.compile(
            "(?i)((?:employeeId|sessionLogin|sessionId|clientIp|FIO|email|login)\\s*[=:]\\s*)\"[^\"]*\"");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern BEARER_VALUE = Pattern.compile(
            "sha256~[A-Za-z0-9_-]+|eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");

    static String sanitize(String line) {
        int space = line.indexOf(' ');
        String timestamp = space < 0 ? "" : line.substring(0, space);
        String content = space < 0 ? line : line.substring(space + 1).trim();
        if (content.matches("[A-Za-z0-9+/=]{40,}") || content.startsWith("{") || content.startsWith("[{"))
            return timestamp + " [encoded/structured payload suppressed]";
        if (SENSITIVE.matcher(line).find()) return timestamp + " [credential-related line suppressed]";
        boolean accessSummary = line.contains("Received request:") || line.contains("Sent response:");
        if (!accessSummary && PAYLOAD.matcher(line).find())
            return timestamp + " [request/response payload suppressed]";
        String result = PERSONAL.matcher(line).replaceAll("$1\"[REDACTED]\"");
        result = EMAIL.matcher(result).replaceAll("[REDACTED_EMAIL]");
        result = BEARER_VALUE.matcher(result).replaceAll("[REDACTED_TOKEN]");
        result = result.replaceAll("(?i)(https?://)[^\\s/@]+:[^\\s/@]+@", "$1[REDACTED]@");
        return result.replaceAll("([/?][^\\s?]*)\\?[^\\s]*", "$1?[REDACTED_QUERY]");
    }
}
