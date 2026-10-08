package infrastructure.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;

final class WorkloadConfigMapEvidence {
    private WorkloadConfigMapEvidence() { }
    static ObjectNode sanitize(JsonNode source, Set<String> booleanKeys) {
        ObjectNode result = WorkloadJson.JSON.createObjectNode();
        result.put("apiVersion", "v1").put("kind", "ConfigMap");
        ObjectNode metadata = result.putObject("metadata");
        for (String key : new String[]{"name", "namespace", "uid", "resourceVersion"})
            metadata.put(key, source.path("metadata").path(key).asText());
        ObjectNode data = result.putObject("data");
        source.path("data").fields().forEachRemaining(entry -> {
            String value = entry.getValue().asText();
            data.put(entry.getKey(), booleanKeys.contains(entry.getKey())
                    && Set.of("true", "false").contains(value) ? value : "[REDACTED]");
        });
        result.put("redaction", "Only explicitly approved boolean switches are disclosed");
        return result;
    }
}
