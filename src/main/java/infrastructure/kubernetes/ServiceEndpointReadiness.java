package infrastructure.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Endpoints must publish the current owned pods on the selected Service port, not stale pod UIDs. */
final class ServiceEndpointReadiness {
    private ServiceEndpointReadiness() { }

    static boolean ready(JsonNode service, JsonNode endpoints, List<JsonNode> pods, int servicePort) {
        if (service == null || service.isNull()) return false;
        JsonNode selector = service.at("/spec/selector");
        if (!selector.isObject() || selector.isEmpty())
            throw WorkloadReadinessWait.failure("WORKLOAD_READINESS_SERVICE_SELECTOR");
        JsonNode selectedPort = null;
        for (JsonNode port : service.at("/spec/ports"))
            if (port.path("port").asInt() == servicePort && "TCP".equals(port.path("protocol").asText("TCP")))
                selectedPort = port;
        if (selectedPort == null) throw WorkloadReadinessWait.failure("WORKLOAD_READINESS_SERVICE_PORT");
        Set<String> expected = new HashSet<>();
        Set<Integer> targets = new HashSet<>();
        for (JsonNode pod : pods) {
            var labels = selector.fields();
            while (labels.hasNext()) {
                var label = labels.next();
                if (!label.getValue().equals(pod.at("/metadata/labels").path(label.getKey())))
                    throw WorkloadReadinessWait.failure("WORKLOAD_READINESS_SERVICE_SELECTOR");
            }
            String uid = pod.at("/metadata/uid").asText();
            if (uid.isBlank()) return false;
            expected.add(uid);
            JsonNode target = selectedPort.path("targetPort");
            if (target.isTextual()) {
                Set<Integer> podTargets = new HashSet<>();
                for (JsonNode container : pod.at("/spec/containers"))
                    for (JsonNode port : container.path("ports"))
                        if (target.asText().equals(port.path("name").asText())) podTargets.add(port.path("containerPort").asInt());
                if (podTargets.size() != 1) throw WorkloadReadinessWait.failure("WORKLOAD_READINESS_TARGET_PORT");
                targets.addAll(podTargets);
            } else targets.add(target.asInt(servicePort));
        }
        if (expected.isEmpty()) return false;
        if (targets.size() != 1) throw WorkloadReadinessWait.failure("WORKLOAD_READINESS_TARGET_PORT");
        if (endpoints == null || endpoints.isNull()) return false;
        Set<String> published = new HashSet<>();
        for (JsonNode subset : endpoints.path("subsets")) {
            boolean selected = false;
            for (JsonNode port : subset.path("ports"))
                if (targets.contains(port.path("port").asInt())
                        && selectedPort.path("name").asText("").equals(port.path("name").asText(""))
                        && "TCP".equals(port.path("protocol").asText("TCP"))) selected = true;
            if (!selected) continue;
            if (!subset.path("notReadyAddresses").isEmpty()) return false;
            for (JsonNode address : subset.path("addresses")) {
                if (!"Pod".equals(address.at("/targetRef/kind").asText())) return false;
                published.add(address.at("/targetRef/uid").asText());
            }
        }
        return published.equals(expected);
    }
}
