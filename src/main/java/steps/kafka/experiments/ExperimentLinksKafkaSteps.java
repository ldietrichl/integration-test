package steps.kafka.experiments;

import com.fasterxml.jackson.databind.*;
import config.services.core.ExperimentLinksSettings;
import io.perfeccionista.framework.Environment;
import io.qameta.allure.Allure;
import org.apache.kafka.common.TopicPartition;
import ru.sber.qa.services.kafka.*;
import java.time.Duration;
import java.util.*;

/** Bounded observer. A limit breach fails evidence collection instead of silently losing events. */
public final class ExperimentLinksKafkaSteps implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final KafkaConsumerClient<String,String> client;
    private final List<JsonNode> events = new ArrayList<>();
    private final Set<String> coordinates = new HashSet<>();
    private final String topic;
    private long bytes;
    private final Set<Long> taskIds = new HashSet<>();
    public void watchTask(long id) { taskIds.add(id); }
    public ExperimentLinksKafkaSteps(ExperimentLinksSettings settings) {
        topic = settings.topic();
        client = Environment.getForCurrentThread().getService(KafkaService.class)
                .consumerClient("links-" + UUID.randomUUID(), Duration.ofSeconds(3));
        try {
            var names = List.of(topic);
            var partitions = names.stream().flatMap(t -> client.getConsumerClient().partitionsFor(t).stream())
                    .map(p -> new TopicPartition(p.topic(), p.partition())).toList();
            if (partitions.isEmpty()) throw new IllegalStateException("BLOCKED_ENVIRONMENT: у топика нет доступных partitions: " + topic);
            client.getConsumerClient().assign(partitions);
            client.getConsumerClient().seekToEnd(partitions);
            Map<String,Long> offsets = new TreeMap<>();
            partitions.forEach(p -> offsets.put(p.toString(), client.getConsumerClient().position(p)));
            Allure.addAttachment("Начальные offsets Kafka", "application/json", JSON.writeValueAsString(offsets), ".json");
        } catch (Exception e) { client.close(); throw new IllegalStateException("Не удалось начать наблюдение Kafka: " + topic, e); }
    }
    public List<JsonNode> poll() throws Exception {
        client.clear();
        try {
            client.poll(Duration.ofMillis(250));
            for (var wrapper : client.records().toList()) {
                var r = wrapper.toConsumerRecord();
                if (!coordinates.add(r.topic() + ":" + r.partition() + ":" + r.offset())) continue;
                if (r.value() == null) continue;
                bytes += r.value().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (bytes > 64L * 1024 * 1024 || coordinates.size() > 10000)
                    throw new IllegalStateException("Превышен лимит наблюдения Kafka; доказательства неполны");
                var event = JSON.createObjectNode().put("topic", r.topic()).put("partition", r.partition())
                        .put("offset", r.offset()).put("timestamp", r.timestamp());
                if (r.key() != null) event.put("key", r.key());
                var headers = event.putArray("headers");
                for (var h : r.headers()) headers.addObject().put("name", h.key()).put("base64",
                        h.value() == null ? null : Base64.getEncoder().encodeToString(h.value()));
                try { event.set("payload", JSON.readTree(r.value())); }
                catch (Exception invalid) { if (topic.equals(r.topic())) throw invalid; else continue; }
                JsonNode payload = event.path("payload");
                boolean relevant = topic.equals(r.topic()) && taskIds.contains(payload.path("taskId").asLong(-1));
                if (relevant) events.add(event);
            }
            return List.copyOf(events);
        } finally { client.clear(); }
    }
    public List<JsonNode> task(long id) {
        return events.stream().filter(e -> topic.equals(e.path("topic").asText()))
                .map(e -> e.path("payload")).filter(e -> e.path("taskId").asLong(-1) == id).toList();
    }
    public List<JsonNode> observations() { return List.copyOf(events); }
    @Override public void close() {
        try { Allure.addAttachment("События Kafka: " + topic, "application/json", JSON.writeValueAsString(events), ".json"); }
        catch (Exception e) { throw new IllegalStateException(e); }
        finally { client.close(); }
    }
}
