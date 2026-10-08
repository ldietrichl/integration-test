package steps.flow.splitter.reactions;
import static dto.splitter.precalc.ReactionsPrecalcRequests.*;
import static util.splittercheck.ReactionsPrecalcAssertions.*;

import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.databind.node.ObjectNode;
import config.services.core.RegressionProfileConfiguration;
import io.qameta.allure.Allure;

import ru.sber.qa.services.kafka.KafkaService;

import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reusable scenario operations; test cases remain in their ticket package. */
public abstract class ReactionsMonitoringSteps extends ReactionsPrecalcSteps {
    @Override public String managedProfile() { return Objects.toString(System.getenv("EXPLAB_2885_PROFILE"), ""); }
    public Map<String, JsonNode> capture(KafkaService kafka, List<ObjectNode> requests, Runnable action) {
        String env = RegressionProfileConfiguration.required("splitter.precalc.monitoring.kafka.env");
        String topic = RegressionProfileConfiguration.required("splitter.precalc.monitoring.topic");
        Duration timeout = Duration.ofSeconds(30);
        var consumer = kafka.consumerClient(env, timeout);
        Set<String> ids = new HashSet<>(); requests.forEach(r -> ids.add(r.path("requestId").asText()));
        Map<String, JsonNode> found = new HashMap<>();
        try {
            consumer.subscribe(topic); consumer.poll(Duration.ofMillis(500));
            long since = System.currentTimeMillis();
            action.run();
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline && found.size() < ids.size()) {
                consumer.poll(Duration.ofMillis(300));
                consumer.records().forEach(w -> {
                    var record = w.toConsumerRecord();
                    if (record.value() == null) return;
                    try {
                        collect(JSON.readTree(String.valueOf(record.value())), ids, found, since);
                    } catch (java.io.IOException ignored) { /* other topics' log payload formats */ }
                });
            }
            assertEquals(ids, found.keySet(), "Missing PRE_CALC_REQUEST events in " + topic);
            found.forEach((id, node) -> Allure.addAttachment("EXPLAB-2885 monitoring " + id, "application/json", node.toPrettyString(), ".json"));
            return found;
        } finally { consumer.unsubscribe(); }
    }
    public static void collect(JsonNode node, Set<String> ids, Map<String, JsonNode> found, long since) {
        if (node == null) return;
        if (node.isTextual()) {
            String raw = node.asText().trim();
            if (raw.startsWith("{") || raw.startsWith("[")) {
                try { collect(JSON.readTree(raw), ids, found, since); } catch (java.io.IOException ignored) { }
            }
        } else if (node.isContainerNode()) {
            String id = node.path("requestIdIn").asText();
            if (ids.contains(id) && "PRE_CALC_REQUEST".equals(node.path("function").asText())) {
                long time = node.path("completedTimestamp").asLong(-1);
                assertTrue(time >= since - 1000, "Stale or missing completedTimestamp: " + node);
                found.put(id, node);
            } else node.forEach(child -> collect(child, ids, found, since));
        }
    }
    public static void event(Map<String, JsonNode> events, ObjectNode request, String result) {
        JsonNode e = events.get(request.path("requestId").asText()); assertNotNull(e);
        assertEquals(result, e.path("result").asText(), e.toString());
        assertEquals(request.path("soConfigVersion").asLong(), e.path("soConfigVersion").asLong(-1));
        assertTrue(List.of("splittingPoing", "splittingPoin", "splittingPoint", "splittingPointCode").stream()
                .anyMatch(n -> "REACTIONS".equals(e.path(n).asText())), e.toString());
    }
    public static void eventCounters(JsonNode event, int... values) {
        assertNotNull(event); ObjectNode wrapper = JSON.createObjectNode(); wrapper.set("counter", event);
        assertCounters(wrapper, values[0], values[1], values[2], values[3], values[4], values[5], values[6]);
    }
}
