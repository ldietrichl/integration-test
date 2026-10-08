package infrastructure.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;

/** ConfigMaps consumed by normal/init containers, including projected volumes. */
final class ConfigMapReferences {
    private ConfigMapReferences() { }
    static Set<String> names(JsonNode deployment) {
        JsonNode spec = deployment.at("/spec/template/spec");
        Set<String> result = new HashSet<>();
        Set<String> mounted = new HashSet<>();
        for (String kind : new String[]{"containers", "initContainers"}) for (JsonNode container : spec.path(kind)) {
            for (JsonNode source : container.path("envFrom")) add(result, source.at("/configMapRef/name"));
            for (JsonNode variable : container.path("env")) add(result, variable.at("/valueFrom/configMapKeyRef/name"));
            for (JsonNode mount : container.path("volumeMounts")) add(mounted, mount.path("name"));
        }
        for (JsonNode volume : spec.path("volumes")) if (mounted.contains(volume.path("name").asText())) {
            add(result, volume.at("/configMap/name"));
            for (JsonNode source : volume.at("/projected/sources")) add(result, source.at("/configMap/name"));
        }
        return Set.copyOf(result);
    }
    private static void add(Set<String> values, JsonNode name) {
        if (name.isTextual() && !name.asText().isBlank()) values.add(name.asText());
    }
}
