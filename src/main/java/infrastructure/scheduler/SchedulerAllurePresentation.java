package infrastructure.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.util.*;

/** Presentation only: translate observed steps, never synthesize successful execution. */
public final class SchedulerAllurePresentation {
    private static final Map<String, String> NAMES = load();
    private SchedulerAllurePresentation() { }
    private static Map<String, String> load() {
        try (InputStream input = SchedulerAllurePresentation.class.getResourceAsStream("/scheduler-allure-ru.json")) {
            if (input == null) throw new IllegalStateException("Missing scheduler-allure-ru.json");
            JsonNode node = new ObjectMapper().readTree(input);
            Map<String, String> values = new LinkedHashMap<>();
            node.fields().forEachRemaining(field -> values.put(field.getKey(), field.getValue().asText()));
            return values;
        } catch (Exception failure) { throw new IllegalStateException("Scheduler Russian step dictionary unavailable", failure); }
    }
    public static String translate(String name) {
        if (name == null) return null;
        String exact = NAMES.get(name);
        if (exact != null) return exact;
        return NAMES.keySet().stream().filter(key -> key.endsWith(" ") && name.startsWith(key))
                .max(Comparator.comparingInt(String::length))
                .map(key -> NAMES.get(key) + name.substring(key.length())).orElse(name);
    }
    public static void steps(List<io.qameta.allure.model.StepResult> steps) {
        if (steps == null) return;
        for (var step : steps) { step.setName(translate(step.getName())); steps(step.getSteps()); }
    }
    public static void tree(JsonNode node) {
        if (!node.isObject()) return;
        for (String field : List.of("steps", "befores", "afters")) {
            for (JsonNode step : node.path(field)) {
                if (step instanceof ObjectNode object) object.put("name", translate(step.path("name").asText()));
                tree(step);
            }
        }
    }
}
