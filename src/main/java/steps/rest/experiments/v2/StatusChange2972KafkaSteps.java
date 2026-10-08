package steps.rest.experiments.v2;

import com.fasterxml.jackson.databind.JsonNode;
import io.perfeccionista.framework.Environment;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.services.kafka.KafkaConsumerClient;
import java.time.Duration;
import java.util.*;

/** Start at current end offsets before the action; retain Kafka coordinates with every actual payload. */
public final class StatusChange2972KafkaSteps implements AutoCloseable {
    private final StatusChange2972Steps owner;
    private final KafkaConsumerClient<String,String> client;
    private final Capture capture = new Capture();
    public StatusChange2972KafkaSteps(StatusChange2972Steps owner) {
        this.owner = owner;
        // KafkaService caches clients by name; each capture owns a distinct lifecycle.
        client = Environment.getForCurrentThread().getService(KafkaService.class)
                .consumerClient("explab2972-" + UUID.randomUUID(), Duration.ofSeconds(3));
        var topics=Arrays.stream(owner.settings.required("kafka.topics").split(",")).map(String::trim).toList();
        // SDK 1.10.3 assign(List) repeatedly replaces the assignment. Assign the union once on its owned client.
        var partitions=topics.stream().flatMap(topic -> client.getConsumerClient().partitionsFor(topic).stream())
                .map(p -> new org.apache.kafka.common.TopicPartition(p.topic(),p.partition())).toList();
        if (partitions.isEmpty()) throw new IllegalStateException("No Kafka partitions available for configured topics");
        client.getConsumerClient().assign(partitions);
        client.getConsumerClient().seekToEnd(client.getConsumerClient().assignment());
        // Resolve lazy seek positions BEFORE executing the REST request.
        client.getConsumerClient().assignment().forEach(partition -> client.getConsumerClient().position(partition));
    }
    /** Register all correlated requests before polling when several can be published together. */
    public void expect(Collection<String> requestIds) { capture.expect(requestIds); }
    public List<JsonNode> poll(String requestId) throws Exception {
        expect(List.of(requestId));
        client.poll(Duration.ofMillis(300));
        try {
            for (var wrapper : client.records().toList()) {
                var r = wrapper.toConsumerRecord();
                var observation = capture.accept(r.topic(), r.partition(), r.offset(), r.timestamp(), r.value());
                if (observation != null) owner.record("Kafka message", observation);
            }
        } finally {
            client.clear();
        }
        return capture.messages(requestId);
    }
    public List<JsonNode> await(String requestId) throws Exception {
        owner.awaitAssertion("Kafka delivery for " + requestId, () -> org.junit.jupiter.api.Assertions.assertFalse(poll(requestId).isEmpty()));
        return poll(requestId);
    }
    @Override public void close() { client.close(); }

    /** Keeps only this scenario's records; unrelated shared-topic payloads are neither retained nor attached. */
    static final class Capture {
        private final Set<String> expected = new HashSet<>();
        private final Map<String,JsonNode> messages = new LinkedHashMap<>();

        void expect(Collection<String> requestIds) {
            for (String requestId : requestIds) {
                if (requestId == null || requestId.isBlank())
                    throw new IllegalArgumentException("A nonblank Kafka requestId is required");
                expected.add(requestId);
            }
        }

        JsonNode accept(String topic, int partition, long offset, long timestamp, String value) {
            if (value == null) return null;
            String key = topic + ":" + partition + ":" + offset;
            if (messages.containsKey(key)) return null;
            JsonNode body;
            try { body = StatusChange2972Steps.JSON.readTree(value); }
            catch (com.fasterxml.jackson.core.JsonProcessingException unrelatedNonJson) { return null; }
            if (body == null || !body.isObject()
                    || (!expected.contains(body.at("/messageInfo/requestId").asText())
                    && !expected.contains(body.path("requestId").asText()))) return null;
            var observation = StatusChange2972Steps.JSON.createObjectNode().put("topic", topic)
                    .put("partition", partition).put("offset", offset).put("timestamp", timestamp);
            observation.set("payload", body);
            messages.put(key, observation);
            return observation;
        }

        List<JsonNode> messages(String requestId) {
            return messages.values().stream().map(n -> n.path("payload"))
                    .filter(n -> requestId.equals(n.at("/messageInfo/requestId").asText())
                            || requestId.equals(n.path("requestId").asText())).toList();
        }
    }
}
