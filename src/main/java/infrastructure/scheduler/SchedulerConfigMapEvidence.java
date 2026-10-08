package infrastructure.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;

/** Default deny: attach real scheduler switches, redact every other ConfigMap value. */
public final class SchedulerConfigMapEvidence {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> FLAGS = Set.of("SCHEDULER_SERVICE_TASK_JOB_ENABLED",
            "SCHEDULER_SERVICE_CLEAR_HUNG_TASKS_JOB_ENABLED", "SCHEDULER_SERVICE_CLEAR_OUTDATED_TASKS_JOB_ENABLED",
            "SCHEDULER_SERVICE_TASK_SCHEDULER_V2_ENABLED", "SCHEDULER_SERVICE_TASK_JOB_V2_ENABLED",
            "SCHEDULER_SERVICE_CLEAR_HUNG_TASKS_JOB_V2_ENABLED", "SCHEDULER_SERVICE_CLEAR_OUTDATED_TASKS_JOB_V2_ENABLED");
    private SchedulerConfigMapEvidence() { }
    public static ObjectNode sanitize(JsonNode configMap) {
        ObjectNode result = JSON.createObjectNode();
        result.put("apiVersion", "v1"); result.put("kind", "ConfigMap");
        ObjectNode metadata = result.putObject("metadata");
        for (String field : new String[]{"name", "namespace", "uid", "resourceVersion"})
            metadata.put(field, configMap.path("metadata").path(field).asText());
        ObjectNode data = result.putObject("data");
        configMap.path("data").fields().forEachRemaining(entry -> {
            String value = entry.getValue().asText();
            data.put(entry.getKey(), FLAGS.contains(entry.getKey()) && Set.of("true", "false").contains(value)
                    ? value : "[REDACTED]");
        });
        result.put("redaction", "Only approved scheduler boolean switches are disclosed; annotations and binaryData omitted");
        return result;
    }
}
