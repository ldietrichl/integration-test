package infrastructure.kubernetes;

import java.util.*;

/** Merge partial states before acquiring locks or performing writes. */
public final class WorkloadStates {
    private WorkloadStates() { }
    public static List<ConfigMapState> merge(List<ConfigMapState> states) {
        Map<String, Map<String, String>> maps = new LinkedHashMap<>();
        for (var state : states) state.data().forEach((key, value) -> {
            String previous = maps.computeIfAbsent(state.name(), unused -> new LinkedHashMap<>()).putIfAbsent(key, value);
            if (previous != null && !previous.equals(value))
                throw new IllegalStateException("Conflicting desired ConfigMap values: " + state.name() + "/" + key);
        });
        return maps.entrySet().stream().map(e -> new ConfigMapState(e.getKey(), e.getValue())).toList();
    }
}
